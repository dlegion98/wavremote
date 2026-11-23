package tsbridge

import (
	"encoding/json"
	"net"
	"net/netip"

	"tailscale.com/net/netmon"
)

// InterfaceSource lists the phone's network interfaces. Android 11+ blocks Go from reading
// them itself, so the app supplies them (as Tailscale's own Android app does).
type InterfaceSource interface {
	InterfacesJSON() string
}

type ifaceJSON struct {
	Name      string   `json:"name"`
	Index     int      `json:"index"`
	MTU       int      `json:"mtu"`
	Up        bool     `json:"up"`
	Loopback  bool     `json:"loopback"`
	P2P       bool     `json:"p2p"`
	Multicast bool     `json:"multicast"`
	Broadcast bool     `json:"broadcast"`
	Addrs     []string `json:"addrs"` // CIDR, e.g. 192.168.20.31/24
}

// SetInterfaceSource makes Tailscale ask src for the interface list. Call before Start.
func SetInterfaceSource(src InterfaceSource) {
	netmon.RegisterInterfaceGetter(func() ([]netmon.Interface, error) {
		var list []ifaceJSON
		if err := json.Unmarshal([]byte(src.InterfacesJSON()), &list); err != nil {
			return nil, err
		}
		out := make([]netmon.Interface, 0, len(list))
		for _, it := range list {
			var flags net.Flags
			if it.Up {
				flags |= net.FlagUp | net.FlagRunning
			}
			if it.Loopback {
				flags |= net.FlagLoopback
			}
			if it.P2P {
				flags |= net.FlagPointToPoint
			}
			if it.Multicast {
				flags |= net.FlagMulticast
			}
			if it.Broadcast {
				flags |= net.FlagBroadcast
			}
			addrs := []net.Addr{}
			for _, a := range it.Addrs {
				p, err := netip.ParsePrefix(a)
				if err != nil {
					continue
				}
				addrs = append(addrs, &net.IPNet{
					IP:   p.Addr().AsSlice(),
					Mask: net.CIDRMask(p.Bits(), p.Addr().BitLen()),
				})
			}
			out = append(out, netmon.Interface{
				Interface: &net.Interface{Index: it.Index, MTU: it.MTU, Name: it.Name, Flags: flags},
				AltAddrs:  addrs,
			})
		}
		return out, nil
	})
}
