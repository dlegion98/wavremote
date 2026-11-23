// Command iosbridge builds tsbridge as a C library for the iOS app (go build -buildmode=c-archive).
// Strings returned to Swift are malloc'd; release them with TSFree.
package main

/*
#include <stdlib.h>
*/
import "C"

import (
	"unsafe"

	"github.com/dlegion98/wavremote/tailscale-bridge/tsbridge"
)

func cstr(s string) *C.char { return C.CString(s) }

func errStr(err error) *C.char {
	if err == nil {
		return nil
	}
	return C.CString(err.Error())
}

//export TSStart
func TSStart(dir, hostname *C.char) *C.char {
	return errStr(tsbridge.Start(C.GoString(dir), C.GoString(hostname)))
}

//export TSStop
func TSStop() { tsbridge.Stop() }

//export TSStatus
func TSStatus() *C.char { return cstr(tsbridge.StatusJSON()) }

//export TSLogin
func TSLogin() *C.char { return errStr(tsbridge.Login()) }

//export TSLogout
func TSLogout() *C.char { return errStr(tsbridge.Logout()) }

//export TSProbe
func TSProbe(host *C.char, ms C.int) C.int {
	if tsbridge.Probe(C.GoString(host), int(ms)) {
		return 1
	}
	return 0
}

//export TSProxyPort
func TSProxyPort() C.int {
	p, err := tsbridge.ProxyPort()
	if err != nil {
		return -1
	}
	return C.int(p)
}

//export TSLogs
func TSLogs() *C.char { return cstr(tsbridge.Logs()) }

//export TSFree
func TSFree(p *C.char) { C.free(unsafe.Pointer(p)) }

func main() {}
