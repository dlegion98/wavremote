# WavRemote

Control your Wavlink AX3000 mesh from your phone, instead of the WavRouter app. Android and iPhone.

## Install

### iPhone and iPad (SideStore or AltStore)

Add this source in **SideStore** or **AltStore** (Sources, then **+**), then install WavRemote from it.
New versions appear there as updates.

```
https://raw.githubusercontent.com/dlegion98/wavremote/main/sidestore.json
```

Or download the IPA and install it yourself:
**[Download WavRemote.ipa](https://raw.githubusercontent.com/dlegion98/wavremote/ios-builds/WavRemote.ipa)**
(older versions are in [ios-builds](https://github.com/dlegion98/wavremote/tree/ios-builds)).

The first time it opens, allow **Local Network** access so it can see your router.

### Android

Download the APK from the **[latest release](https://github.com/dlegion98/wavremote/releases/latest)**.
After that, the app updates itself (More, App update).

### Away from home

The router only answers inside your home network. Run Tailscale on an always-on computer at home that
shares your network (`tailscale up --advertise-routes=192.168.20.0/24`, then approve the route in the
Tailscale admin console), and turn Tailscale on on your phone. WavRemote then works anywhere.

## Android app

A native Kotlin/Jetpack Compose app for Wavlink AX3000 mesh routers, replacing the WavRouter app.
It talks directly to the router's own JSON API (`/protocol.csp`). The API was mapped from the
router's web UI scripts.

## Features
- **Find**: reads the real gateway for the Wi-Fi network and checks the usual Wavlink addresses.
  All traffic goes over Wi-Fi even with mobile data on.
- **Sign in**: uses the router's admin password and can stay signed in. Only the md5 hash the router
  expects is stored, never the password. It signs back in automatically when the session expires.
- **Home**: internet status, devices online, mesh node count, Wi-Fi names, WAN details, model,
  mode, firmware and uptime, plus a firmware-update banner.
- **Devices**: online and recent devices with connection type, signal, IP and live speed.
  Tap one to rename it, block its internet, set download/upload speed limits, or remove an offline device.
- **Wi-Fi**: change network name and password, with one shared name or separate 2.4/5 GHz names.
  Every other setting (channels, bandwidth, DFS, schedules, hidden, TWT/OFDMA) is kept exactly as it was.
- **More**: mesh satellites, restart router, the router's own web settings for anything else, and sign out.

## Tools (v1.2)
- **Quick controls on Home**: guest Wi-Fi, kids' Wi-Fi and status lights on/off in one tap.
- **Right now**: total traffic and the busiest devices.
- **Speed test**: download, upload, ping and jitter via Cloudflare, with history.
- **Wi-Fi signal meter**: live dBm, quality and a 90-second graph for finding weak spots.
- **Share Wi-Fi**: QR code (main, per-band or guest) plus "send as message".
- **Wake-on-LAN**: wake a computer from the device list or by MAC.
- **New-device alerts**: background check every ~15 minutes on home Wi-Fi, with a notification for unknown devices.
- **Devices**: search, filter (Wi-Fi/wired/blocked/limited), sort (name/speed/signal/recent), CSV export,
  and private-MAC detection.

## Devices (v1.3)
- **Favourites**: star a device to keep it at the top of Devices and show it on Home.
- **Device history**: the app remembers when each device was last online and when it first noticed it.
  This is kept on the phone only.
- Tap an IP or MAC address to copy it. The device list remembers its sort order.

See [CHANGELOG.md](CHANGELOG.md) for what changed in each update.

## API notes (protocol.csp)
| Action | Call |
|---|---|
| Login | `fname=system&opt=login&function=set&usrid=md5("admin"+password)` → `token` |
| Status | `net/wan_info`, `sys/baseinfo`, `net/guide`, `system/main` (get) |
| Devices | `system/main` (terminals), `net/dhcp_leases`, `system/host_if&if=FFFFFFFT&flage=0` |
| Rename | `net/host_nick` set `mac`, `nick` |
| Block | `net/host_black` set `mac`, `index=1/0` |
| Speed limit | `net/host_ls` set `mac`, `speed`, `up_speed` (KB/s, 0 = unlimited) |
| Wi-Fi | `net/wifi_ap` / `net/wifi_ap_5g` get/set |
| Mesh | `net/wifi_mesh_agent_info` get |
| Reboot | `system/reset_reboot` set `action=0` |

## Build
- **Android**: open in Android Studio, then Build, Build APK(s). Requires Android 8.0 or later.
  GitHub Actions builds and publishes a signed APK on every push.
- **iOS**: SwiftUI app in [`ios/`](ios). GitHub Actions builds an unsigned IPA on macOS for every change
  there, and updates `sidestore.json`. Requires iOS 16 or later.
