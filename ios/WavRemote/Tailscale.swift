import Foundation
import SafariServices
import SwiftUI

/// Tailscale running inside the app (userspace, no VPN profile), via the Go bridge in TSBridge.h.
/// Lets WavRemote reach the router away from home through a subnet router on your tailnet.
@MainActor
final class TailscaleManager: ObservableObject {
    static let shared = TailscaleManager()

    struct Status: Decodable, Equatable {
        var state: String
        var authURL: String?
        var ip: String?
        var name: String?
        var tailnet: String?
        var error: String?
    }

    @Published private(set) var enabled = UserDefaults.standard.bool(forKey: "tsEnabled")
    @Published private(set) var status = Status(state: "Stopped")
    @Published var busy = false

    private var poller: Task<Void, Never>?
    private var port: Int?

    var running: Bool { status.state == "Running" }

    private var stateDir: String {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        return base.appendingPathComponent("tailscale", isDirectory: true).path
    }

    func startIfEnabled() { if enabled { start() } }

    func setEnabled(_ on: Bool) {
        enabled = on
        UserDefaults.standard.set(on, forKey: "tsEnabled")
        if on { start() } else { stop() }
    }

    private func start() {
        let dir = stateDir
        status.state = "Starting"
        Task.detached {
            let err = TSStart(dir, "wavremote-iphone")
            var message: String?
            if let err { message = String(cString: err); TSFree(err) }
            await MainActor.run {
                if let message { self.status = Status(state: "Stopped", error: message) }
            }
        }
        poll()
    }

    private func stop() {
        poller?.cancel()
        poller = nil
        status = Status(state: "Stopped")
        Task.detached { TSStop() }
    }

    /// Asks for a new sign-in link if there isn't one yet.
    func login() {
        busy = true
        Task.detached {
            if let err = TSLogin() { TSFree(err) }
            await MainActor.run { self.busy = false }
        }
        poll()
    }

    func logout() {
        busy = true
        Task.detached {
            if let err = TSLogout() { TSFree(err) }
            await MainActor.run { self.busy = false }
        }
        poll()
    }

    func refresh() async {
        let json: String = await Task.detached {
            guard let p = TSStatus() else { return "" }
            defer { TSFree(p) }
            return String(cString: p)
        }.value
        if let s = try? JSONDecoder().decode(Status.self, from: Data(json.utf8)), s != status {
            status = s
        }
    }

    private func poll() {
        poller?.cancel()
        poller = Task { [weak self] in
            while !Task.isCancelled {
                guard let self, self.enabled else { return }
                await self.refresh()
                let wait: UInt64 = self.running ? 10 : 2
                try? await Task.sleep(nanoseconds: wait * 1_000_000_000)
            }
        }
    }

    /// Waits up to `seconds` for Tailscale to connect, if it's switched on.
    func waitUntilRunning(seconds: Double) async -> Bool {
        guard enabled else { return false }
        let end = Date().addingTimeInterval(seconds)
        while Date() < end {
            await refresh()
            if running { return true }
            if status.state == "NeedsLogin" || status.state == "NeedsMachineAuth" { return false }
            try? await Task.sleep(nanoseconds: 700_000_000)
        }
        return running
    }

    /// Local HTTP proxy that carries the app's requests over the tailnet.
    func proxyPort() -> Int? {
        if let port { return port }
        let p = Int(TSProxyPort())
        if p > 0 { port = p; return p }
        return nil
    }

    func reaches(_ host: String) async -> Bool {
        guard running else { return false }
        return await Task.detached { TSProbe(host, 5000) == 1 }.value
    }

    var stateText: String {
        switch status.state {
        case "Running":
            var s = "Connected"
            if let t = status.tailnet, !t.isEmpty { s += " to \(t)" }
            if let ip = status.ip { s += " as \(ip)" }
            return s
        case "NeedsLogin": return "Sign in to your Tailscale account to connect."
        case "NeedsMachineAuth": return "Waiting for this device to be approved in the Tailscale admin console."
        case "Starting", "NoState": return "Starting…"
        default: return enabled ? "Starting…" : "Off"
        }
    }
}

struct SafariSheet: UIViewControllerRepresentable {
    let url: URL
    func makeUIViewController(context: Context) -> SFSafariViewController {
        let vc = SFSafariViewController(url: url)
        vc.preferredBarTintColor = UIColor(Pro.bg)
        vc.preferredControlTintColor = UIColor(Pro.accent)
        return vc
    }
    func updateUIViewController(_ vc: SFSafariViewController, context: Context) {}
}

/// Turn built-in Tailscale on, sign in, see its state.
struct TailscaleCard: View {
    @ObservedObject var ts = TailscaleManager.shared
    @State private var signInURL: URL?

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Toggle(isOn: Binding(get: { ts.enabled }, set: { ts.setEnabled($0) })) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Built-in Tailscale").font(.pro(16, .semibold))
                    Text("Reach your router from anywhere. No VPN needed.").font(.pro(13)).foregroundStyle(Pro.muted)
                }
            }
            .tint(Pro.accent)

            if ts.enabled {
                HStack(spacing: 8) {
                    Circle().fill(ts.running ? Pro.green : ts.status.state == "NeedsLogin" ? Pro.amber : Pro.muted)
                        .frame(width: 9, height: 9)
                    Text(ts.stateText).font(.pro(14)).foregroundStyle(Pro.text)
                }
                if let e = ts.status.error, !e.isEmpty, !ts.running {
                    Text(e).font(.pro(13)).foregroundStyle(Pro.red)
                }
                if ts.status.state == "NeedsLogin" {
                    Button {
                        if let s = ts.status.authURL, let u = URL(string: s) { signInURL = u } else { ts.login() }
                    } label: {
                        Text(ts.status.authURL == nil ? "Getting sign-in link…" : "Sign in to Tailscale")
                    }
                    .buttonStyle(PrimaryButtonStyle())
                    .disabled(ts.busy)
                }
                if ts.running {
                    Button("Sign out of Tailscale") { ts.logout() }
                        .buttonStyle(SecondaryButtonStyle())
                        .disabled(ts.busy)
                }
                Text("Your home server must share your home network on Tailscale (subnet route 192.168.20.0/24), approved in the Tailscale admin console. WavRemote shows up there as its own device.")
                    .font(.pro(13)).foregroundStyle(Pro.muted)
            }
        }
        .panel()
        .sheet(item: Binding(get: { signInURL.map(IdentifiedURL.init) }, set: { signInURL = $0?.url })) { item in
            SafariSheet(url: item.url).ignoresSafeArea()
        }
        .onChange(of: ts.running) { on in if on { signInURL = nil } }
    }
}

struct IdentifiedURL: Identifiable {
    let url: URL
    var id: String { url.absoluteString }
}
