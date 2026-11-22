import Foundation

struct Overview {
    var model: String?
    var internetUp: Bool
    var wanIp: String?
    var wanType: String
    var ssid2: String?
    var ssid5: String?
    var uptime: Int?
    var firmware: String?
    var updateAvailable: Bool
    var meshNodes: Int?
    var onlineDevices: Int?
}

struct Device: Identifiable, Hashable {
    var mac: String
    var name: String
    var ip: String
    var online: Bool
    var band: Int
    var rssi: Int
    var downKBs: Int
    var upKBs: Int
    var onlineSec: Int
    var blocked: Bool
    var limitDown: Int
    var limitUp: Int

    var id: String { mac.lowercased() }
    var isWired: Bool { band == 2 }
    var hasLimit: Bool { limitDown != 0 || limitUp != 0 }
    var connection: String {
        switch band {
        case 0: return "2.4 GHz Wi-Fi"
        case 1: return "5 GHz Wi-Fi"
        case 2: return "Wired"
        default: return "Wi-Fi"
        }
    }
}

struct DeviceList {
    var online: [Device]
    var offline: [Device]
}

struct MeshNode: Identifiable {
    var name: String
    var ip: String?
    var rssi: Int?
    var id: String { (ip ?? "") + name }

    var linkLabel: String {
        guard let r = rssi else { return "Connected" }
        if r >= -55 { return "Strong link" }
        if r >= -73 { return "Good link" }
        return "Weak link"
    }
}

struct WifiConfig {
    var country: String
    var raw2: [String: Any]
    var raw5: [String: Any]

    var ssid2: String { raw2.str("SSID2g") }
    var ssid5: String { raw5.str("SSID5g") }
    var password2: String { raw2.str("security") == "0" ? "" : raw2.str("passwd") }
    var password5: String { raw5.str("security") == "0" ? "" : raw5.str("passwd") }
    var open2: Bool { raw2.str("security") == "0" || raw2.str("security").isEmpty }
    var open5: Bool { raw5.str("security") == "0" || raw5.str("security").isEmpty }
    var on2: Bool { raw2.str("enable") != "0" }
    var on5: Bool { raw5.str("enable") != "0" }
    var channel2: String { raw2.str("channel") }
    var channel5: String { raw5.str("channel") }
}

struct TrafficSample: Identifiable {
    var at: Date
    var down: Int
    var up: Int
    var id: Date { at }
}

func formatDuration(_ sec: Int) -> String {
    if sec <= 0 { return "–" }
    let d = sec / 86400, h = (sec % 86400) / 3600, m = (sec % 3600) / 60
    var parts: [String] = []
    if d > 0 { parts.append("\(d)d") }
    if h > 0 { parts.append("\(h)h") }
    if m > 0 || parts.isEmpty { parts.append("\(m)m") }
    return parts.joined(separator: " ")
}

func formatSpeed(_ kbs: Int) -> String {
    kbs > 1024 ? String(format: "%.1f MB/s", Double(kbs) / 1024) : "\(kbs) KB/s"
}

/// 0–100 Wi-Fi experience across online devices (wired counts as perfect).
func experienceScore(_ list: DeviceList) -> Int? {
    let on = list.online
    if on.isEmpty { return nil }
    let total = on.reduce(0) { $0 + ($1.isWired ? 100 : min(100, max(0, Int(Double($1.rssi) * 1.15)))) }
    return total / on.count
}

func experienceLabel(_ score: Int) -> String {
    switch score {
    case 80...: return "Excellent"
    case 60..<80: return "Good"
    case 40..<60: return "Fair"
    default: return "Poor"
    }
}

func wifiPassError(_ p: String) -> String? {
    if p.count < 8 { return "At least 8 characters" }
    if p.count > 63 { return "At most 63 characters" }
    if !p.unicodeScalars.allSatisfy({ $0.value >= 32 && $0.value <= 126 }) { return "Use plain letters, numbers and symbols" }
    return nil
}

func ssidError(_ s: String) -> String? {
    if s.trimmingCharacters(in: .whitespaces).isEmpty { return "Enter a name" }
    if s.utf8.count > 31 { return "Too long (max 31 bytes)" }
    if s.unicodeScalars.contains(where: { $0.value < 32 || $0.value == 127 }) { return "Contains characters the router doesn't allow" }
    return nil
}
