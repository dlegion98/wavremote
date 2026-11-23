// Package tsbridge runs Tailscale inside WavRemote (userspace, no VPN) and exposes a local
// HTTP proxy so the app can reach the home router through a subnet router on the tailnet.
//
// The API uses only strings, ints, bools and errors so gomobile can bind it for Android;
// cmd/iosbridge wraps the same functions as C exports for iOS.
package tsbridge

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"runtime/debug"
	"strings"
	"sync"
	"time"

	"tailscale.com/tsnet"
)

var (
	mu        sync.Mutex
	srv       *tsnet.Server
	lastErr   string
	proxyPort int

	logMu   sync.Mutex
	logRing []string
)

func logf(format string, args ...any) {
	line := fmt.Sprintf(format, args...)
	logMu.Lock()
	logRing = append(logRing, time.Now().Format("15:04:05 ")+line)
	if len(logRing) > 80 {
		logRing = logRing[len(logRing)-80:]
	}
	logMu.Unlock()
}

func current() *tsnet.Server {
	mu.Lock()
	defer mu.Unlock()
	return srv
}

// Start joins the tailnet using the state kept in dir. Safe to call more than once.
func Start(dir, hostname string) error {
	mu.Lock()
	defer mu.Unlock()
	if srv != nil {
		return nil
	}
	// Don't upload logs to Tailscale from inside this app.
	os.Setenv("TS_NO_LOGS_NO_SUPPORT", "true")
	if err := os.MkdirAll(dir, 0o700); err != nil {
		lastErr = err.Error()
		return err
	}
	// Android apps have no HOME, cache or temp folder in their environment, and Tailscale
	// panics when it can't find somewhere to keep log state. Point all of them inside dir.
	for name, sub := range map[string]string{
		"TS_LOGS_DIR":    "logs",
		"XDG_CACHE_HOME": "cache",
		"TMPDIR":         "tmp",
		"HOME":           "",
	} {
		p := filepath.Join(dir, sub)
		if err := os.MkdirAll(p, 0o700); err == nil && (name == "TS_LOGS_DIR" || os.Getenv(name) == "") {
			os.Setenv(name, p)
		}
	}
	// If Go itself crashes, write the details where the app can find them next launch.
	if f, err := os.OpenFile(filepath.Join(dir, "crash.txt"), os.O_CREATE|os.O_WRONLY|os.O_TRUNC, 0o600); err == nil {
		debug.SetCrashOutput(f, debug.CrashOptions{})
		f.Close()
	}
	s := &tsnet.Server{
		Dir:      dir,
		Hostname: hostname,
		Logf:     logf,
		UserLogf: logf,
	}
	if err := s.Start(); err != nil {
		lastErr = err.Error()
		s.Close()
		return err
	}
	lastErr = ""
	srv = s
	return nil
}

// Stop leaves the tailnet until Start is called again. The sign-in is kept.
func Stop() {
	mu.Lock()
	s := srv
	srv = nil
	mu.Unlock()
	if s != nil {
		s.Close()
	}
}

type status struct {
	State   string `json:"state"`
	AuthURL string `json:"authURL,omitempty"`
	IP      string `json:"ip,omitempty"`
	Name    string `json:"name,omitempty"`
	Tailnet string `json:"tailnet,omitempty"`
	Error   string `json:"error,omitempty"`
}

// StatusJSON reports {state, authURL, ip, name, tailnet, error}.
// state is Stopped, Starting, NeedsLogin, NeedsMachineAuth or Running.
func StatusJSON() string {
	out := status{State: "Stopped"}
	mu.Lock()
	s := srv
	out.Error = lastErr
	mu.Unlock()
	if s != nil {
		out.State = "Starting"
		if lc, err := s.LocalClient(); err == nil {
			ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
			st, err := lc.StatusWithoutPeers(ctx)
			cancel()
			if err == nil {
				out.State = st.BackendState
				out.AuthURL = st.AuthURL
				if len(st.TailscaleIPs) > 0 {
					out.IP = st.TailscaleIPs[0].String()
				}
				if st.Self != nil {
					out.Name = st.Self.HostName
				}
				if st.CurrentTailnet != nil {
					out.Tailnet = st.CurrentTailnet.Name
				}
			}
		}
	}
	b, _ := json.Marshal(out)
	return string(b)
}

// Login asks for a fresh sign-in link (shown as authURL in StatusJSON).
func Login() error {
	s := current()
	if s == nil {
		return errors.New("Tailscale isn't running")
	}
	lc, err := s.LocalClient()
	if err != nil {
		return err
	}
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	return lc.StartLoginInteractive(ctx)
}

// Logout signs this app out of the tailnet and removes it as a device.
func Logout() error {
	s := current()
	if s == nil {
		return nil
	}
	lc, err := s.LocalClient()
	if err != nil {
		return err
	}
	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	return lc.Logout(ctx)
}

// Probe reports whether host:80 answers through the tailnet within ms milliseconds.
func Probe(host string, ms int) bool {
	s := current()
	if s == nil {
		return false
	}
	ctx, cancel := context.WithTimeout(context.Background(), time.Duration(ms)*time.Millisecond)
	defer cancel()
	c, err := s.Dial(ctx, "tcp", net.JoinHostPort(host, "80"))
	if err != nil {
		return false
	}
	c.Close()
	return true
}

// Logs returns recent Tailscale log lines, newest last.
func Logs() string {
	logMu.Lock()
	defer logMu.Unlock()
	return strings.Join(logRing, "\n")
}

// ProxyPort starts (once) an HTTP proxy on 127.0.0.1 that sends requests through the
// tailnet, and returns its port. Point the app's HTTP client at it to reach the router.
func ProxyPort() (int, error) {
	mu.Lock()
	defer mu.Unlock()
	if proxyPort != 0 {
		return proxyPort, nil
	}
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		return 0, err
	}
	dial := func(ctx context.Context, network, addr string) (net.Conn, error) {
		s := current()
		if s == nil {
			return nil, errors.New("Tailscale isn't running")
		}
		return s.Dial(ctx, network, addr)
	}
	tr := &http.Transport{
		Proxy:                 nil,
		DialContext:           dial,
		DisableKeepAlives:     true,
		DisableCompression:    true,
		ResponseHeaderTimeout: 25 * time.Second,
	}
	handler := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method == http.MethodConnect {
			tunnel(w, r, dial)
			return
		}
		if r.URL.Host == "" {
			http.Error(w, "proxy needs an absolute URL", http.StatusBadRequest)
			return
		}
		out := r.Clone(r.Context())
		out.RequestURI = ""
		out.Header.Del("Proxy-Connection")
		out.Header.Del("Proxy-Authorization")
		resp, err := tr.RoundTrip(out)
		if err != nil {
			http.Error(w, err.Error(), http.StatusBadGateway)
			return
		}
		defer resp.Body.Close()
		for k, vs := range resp.Header {
			for _, v := range vs {
				w.Header().Add(k, v)
			}
		}
		w.WriteHeader(resp.StatusCode)
		io.Copy(w, resp.Body)
	})
	go http.Serve(ln, handler)
	proxyPort = ln.Addr().(*net.TCPAddr).Port
	return proxyPort, nil
}

func tunnel(w http.ResponseWriter, r *http.Request, dial func(context.Context, string, string) (net.Conn, error)) {
	ctx, cancel := context.WithTimeout(r.Context(), 20*time.Second)
	up, err := dial(ctx, "tcp", r.Host)
	cancel()
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadGateway)
		return
	}
	hj, ok := w.(http.Hijacker)
	if !ok {
		up.Close()
		http.Error(w, "tunnel not supported", http.StatusInternalServerError)
		return
	}
	down, buf, err := hj.Hijack()
	if err != nil {
		up.Close()
		return
	}
	down.Write([]byte("HTTP/1.1 200 Connection Established\r\n\r\n"))
	if buf != nil && buf.Reader.Buffered() > 0 {
		io.CopyN(up, buf, int64(buf.Reader.Buffered()))
	}
	go func() { io.Copy(up, down); up.Close() }()
	io.Copy(down, up)
	down.Close()
}
