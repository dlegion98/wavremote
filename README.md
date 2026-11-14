# WavRemote (Android)

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
Open in Android Studio → Build → Build APK(s). Requires Android 8.0 or later.
