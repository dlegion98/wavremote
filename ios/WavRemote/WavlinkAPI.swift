import Foundation
import CryptoKit

struct RouterError: LocalizedError {
    let code: Int
    let message: String
    var errorDescription: String? { message }
}

/// Talks to a Wavlink router's web API (protocol.csp), the same way its own web page does.
final class WavlinkAPI: @unchecked Sendable {
    static let sessionExpired = 10007
    static let browserUA = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"

    let host: String
    private(set) var token: String?
    private(set) var loginHash: String?

    private let session: URLSession
    /// True when requests go through the built-in Tailscale proxy.
    let viaTailscale: Bool

    private let proxyPort: Int?

    /// Another device on the same network (a mesh point), reached the same way as this one.
    func sibling(_ otherHost: String) -> WavlinkAPI { WavlinkAPI(host: otherHost, proxyPort: proxyPort) }

    init(host: String, proxyPort: Int? = nil) {
        self.host = host
        self.proxyPort = proxyPort
        let c = URLSessionConfiguration.ephemeral
        c.timeoutIntervalForRequest = proxyPort == nil ? 15 : 25
        c.timeoutIntervalForResource = proxyPort == nil ? 30 : 50
        c.requestCachePolicy = .reloadIgnoringLocalCacheData
        c.httpShouldSetCookies = false
        if let port = proxyPort {
            c.connectionProxyDictionary = [
                "HTTPEnable": 1,
                "HTTPProxy": "127.0.0.1",
                "HTTPPort": port,
            ]
        }
        viaTailscale = proxyPort != nil
        session = URLSession(configuration: c)
    }

    private var endpoint: String { "http://\(host)/protocol.csp" }

    // MARK: transport

    private func request(_ url: URL) -> URLRequest {
        var r = URLRequest(url: url)
        r.setValue(Self.browserUA, forHTTPHeaderField: "User-Agent")
        r.setValue("application/json, text/javascript, */*; q=0.01", forHTTPHeaderField: "Accept")
        r.setValue("en-GB,en;q=0.9", forHTTPHeaderField: "Accept-Language")
        r.setValue("XMLHttpRequest", forHTTPHeaderField: "X-Requested-With")
        r.setValue("http://\(host)", forHTTPHeaderField: "Origin")
        r.setValue("http://\(host)/login.html", forHTTPHeaderField: "Referer")
        return r
    }

    private func send(_ data: String) async throws -> [String: Any] {
        let isGet = data.contains("&function=get")
        var req: URLRequest
        if isGet {
            let s = token.map { "\(endpoint)?token=\($0)&\(data)" } ?? "\(endpoint)?\(data)"
            guard let u = URL(string: s) else { throw RouterError(code: -1, message: "Bad request") }
            req = request(u)
        } else {
            // The router answers HTTP 500 when protocol.csp has no query string at all.
            let s = token.map { "\(endpoint)?token=\($0)" } ?? "\(endpoint)?_=\(Int(Date().timeIntervalSince1970 * 1000))"
            guard let u = URL(string: s) else { throw RouterError(code: -1, message: "Bad request") }
            req = request(u)
            req.httpMethod = "POST"
            req.setValue("application/x-www-form-urlencoded; charset=UTF-8", forHTTPHeaderField: "Content-Type")
            req.httpBody = data.data(using: .utf8)
        }
        let (body, resp) = try await session.data(for: req)
        let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
        let text = String(decoding: body, as: UTF8.self).trimmingCharacters(in: .whitespacesAndNewlines)
        if text.hasPrefix("{"), let obj = try? JSONSerialization.jsonObject(with: Data(text.utf8)) as? [String: Any] {
            return obj
        }
        if code >= 400 {
            let what = data.components(separatedBy: "opt=").dropFirst().first?.components(separatedBy: "&").first ?? ""
            throw RouterError(code: code, message: "Router returned HTTP \(code) for \"\(what)\"")
        }
        return ["error": 0, "raw": text]
    }

    /// Sends a call, signing in again once if the session expired.
    @discardableResult
    func call(_ data: String) async throws -> [String: Any] {
        var r = try await send(data)
        var err = r.int("error")
        if err == Self.sessionExpired, let hash = loginHash {
            try await login(hash: hash)
            r = try await send(data)
            err = r.int("error")
        }
        if err != 0 {
            throw RouterError(code: err, message: err == Self.sessionExpired
                ? "Your session expired. Please sign in again." : "The router returned error \(err).")
        }
        return r
    }

    // MARK: session

    func guide() async throws -> [String: Any] { try await send("fname=net&opt=guide&function=get") }

    func login(hash: String) async throws {
        token = nil
        let r = try await send("fname=system&opt=login&function=set&usrid=\(hash)")
        let err = r["error"] == nil ? -1 : r.int("error")
        if err != 0 {
            let freq = r.int("frequency")
            let msg = freq >= 5
                ? "Too many wrong attempts. The router has locked sign-in for \(r["time"] == nil ? 60 : r.int("time")) seconds."
                : "Wrong password (\(max(0, 5 - freq)) tries left before a lock-out)."
            throw RouterError(code: err, message: msg)
        }
        let t = r.str("token")
        token = t.isEmpty ? nil : t
        loginHash = hash
    }

    // MARK: reads

    func overview() async throws -> Overview {
        let g = try? await guide()
        let wan = try await call("fname=net&opt=wan_info&function=get")
        let w2 = try? await call("fname=net&opt=wifi_ap&function=get")
        let w5 = try? await call("fname=net&opt=wifi_ap_5g&function=get")
        let base = try? await call("fname=sys&opt=baseinfo&function=get")
        let main = try? await call("fname=system&opt=main&function=get")
        let mesh = try? await call("fname=net&opt=wifi_mesh_agent_info&function=get")
        let terminals = (main?["terminals"] as? [[String: Any]]) ?? []
        let wanModes = [2: "PPPoE", 3: "Static IP", 4: "PPPoE dual access", 5: "PPTP dual access", 6: "L2TP dual access"]
        return Overview(
            model: g?.nonEmpty("Model") ?? base?.nonEmpty("Model"),
            internetUp: wan.truthy("connected"),
            wanLink: wan["link"] == nil ? true : wan.truthy("link"),
            wanIp: wan.nonEmpty("ip"),
            wanType: wanModes[wan["mode"] == nil ? 1 : wan.int("mode")] ?? "DHCP",
            ssid2: w2?.nonEmpty("SSID2g"),
            ssid5: w5?.nonEmpty("SSID5g"),
            uptime: Self.firstInt([main, base], ["ontime", "uptime", "run_time"]),
            firmware: Self.firstString([base, main], ["sdkversion", "fw_version", "firmware", "FirmwareVersion", "soft_version", "version"]),
            updateAvailable: base?.int("ota_info") == 1,
            meshNodes: (mesh?["agent_list"] as? [Any])?.count,
            onlineDevices: main == nil ? nil : terminals.filter(Self.isOnline).count
        )
    }

    func devices() async throws -> DeviceList {
        let leases = (try? await call("fname=net&opt=dhcp_leases&function=get"))?["dhcp_list"] as? [[String: Any]] ?? []
        let hostIf = try? await call("fname=system&opt=host_if&function=get&if=FFFFFFFT&flage=0")
        let main = try await call("fname=system&opt=main&function=get")
        var leaseNames: [String: String] = [:]
        for l in leases { leaseNames[l.str("mac").lowercased()] = l.str("name") }
        var limits: [String: (Int, Int)] = [:]
        for t in (hostIf?["terminals"] as? [[String: Any]]) ?? [] {
            limits[t.str("mac").lowercased()] = (t.int("ls"), t.int("ls_up"))
        }
        let all: [Device] = ((main["terminals"] as? [[String: Any]]) ?? []).map { t in
            let mac = t.str("mac")
            var name = t.str("name")
            if name.isEmpty { name = leaseNames[mac.lowercased()] ?? "" }
            if name.trimmingCharacters(in: .whitespaces).isEmpty || name == "*" { name = "Unknown device" }
            let lim = limits[mac.lowercased()] ?? (0, 0)
            return Device(
                mac: mac, name: name, ip: t.str("ip"), online: Self.isOnline(t),
                band: t["band"] == nil ? -1 : t.int("band"), rssi: t.int("rssi"),
                downKBs: t.int("speed"), upKBs: t.int("up_speed"), onlineSec: t.int("ontime"),
                blocked: t.truthy("net_index"), limitDown: lim.0, limitUp: lim.1
            )
        }
        let byName: (Device, Device) -> Bool = { $0.name.lowercased() < $1.name.lowercased() }
        return DeviceList(online: all.filter { $0.online }.sorted(by: byName),
                          offline: all.filter { !$0.online }.sorted(by: byName))
    }

    func wifi() async throws -> WifiConfig {
        let region = try? await call("fname=net&opt=wifi_region&function=get")
        let w2 = try await call("fname=net&opt=wifi_ap&function=get")
        let w5 = try await call("fname=net&opt=wifi_ap_5g&function=get")
        return WifiConfig(country: region?.str("country") ?? "", raw2: w2, raw5: w5)
    }

    func meshNodes() async throws -> [MeshNode] {
        let r = try await call("fname=net&opt=wifi_mesh_agent_info&function=get")
        let list = (r["agent_list"] as? [[String: Any]]) ?? []
        return list.enumerated().map { i, o in
            MeshNode(name: o.nonEmpty("name") ?? o.nonEmpty("dev_name") ?? "Node \(i + 1)",
                     ip: o.nonEmpty("ip_addr") ?? o.nonEmpty("ip"),
                     rssi: o["rssi"] == nil ? nil : o.int("rssi"),
                     mac: o.nonEmpty("mac") ?? o.nonEmpty("pairKey") ?? o.nonEmpty("pair_key"))
        }
    }

    // MARK: actions

    func rename(mac: String, name: String) async throws {
        try await call("fname=net&opt=host_nick&function=set&mac=\(mac)&nick=\(Self.enc(name))")
    }

    func setBlocked(mac: String, blocked: Bool) async throws {
        try await call("fname=net&opt=host_black&function=set&mac=\(mac)&index=\(blocked ? 1 : 0)")
    }

    func setLimit(mac: String, downKB: Int, upKB: Int) async throws {
        try await call("fname=net&opt=host_ls&function=set&mac=\(mac)&speed=\(downKB)&up_speed=\(upKB)")
    }

    func forgetOffline(mac: String) async throws {
        try await call("fname=net&opt=host_del_history&function=set&mac=\(mac)")
    }

    func reboot() async {
        _ = try? await call("fname=system&opt=reset_reboot&function=set&action=0")
    }

    /// Starts the router's own network check; read the results with get("sys", "netdiagnostics") ~18 s later.
    func startDiagnostics() async throws {
        try await call("fname=sys&opt=netdiagnostics&function=set")
    }

    /// 2.4 GHz first without restart, then 5 GHz three seconds later with restart (as the web page does).
    func saveBands(country: String, b2: BandEdit, b5: BandEdit) async throws {
        try await call(b2.setData(country: country, needRestart: false))
        try await Task.sleep(nanoseconds: 3_000_000_000)
        _ = try? await call(b5.setData(country: country, needRestart: true))
    }

    func get(_ fname: String, _ opt: String, _ extra: String = "") async throws -> [String: Any] {
        try await call("fname=\(fname)&opt=\(opt)&function=get\(extra)")
    }

    @discardableResult
    func set(_ fname: String, _ opt: String, _ params: String = "") async throws -> [String: Any] {
        try await call("fname=\(fname)&opt=\(opt)&function=set\(params)")
    }

    // MARK: helpers

    static func hashPassword(_ password: String) -> String {
        Insecure.MD5.hash(data: Data(("admin" + password).utf8)).map { String(format: "%02x", $0) }.joined()
    }

    /// JavaScript's encodeURIComponent(), then %20 back to a space, as the web page does.
    static func enc(_ s: String) -> String {
        let safe = Set("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.!~*'()".utf8)
        var out = ""
        for b in s.utf8 {
            if safe.contains(b) { out.append(Character(UnicodeScalar(b))) }
            else { out += String(format: "%%%02X", b) }
        }
        return out.replacingOccurrences(of: "%20", with: " ")
    }

    static func isOnline(_ t: [String: Any]) -> Bool {
        let f = Array(t.str("flag"))
        return f.count > 1 && f[1] == "T"
    }

    private static func firstInt(_ objs: [[String: Any]?], _ keys: [String]) -> Int? {
        for o in objs { for k in keys { if let o, o[k] != nil, let v = Int(o.str(k)) { return v } } }
        return nil
    }

    private static func firstString(_ objs: [[String: Any]?], _ keys: [String]) -> String? {
        for o in objs { for k in keys { if let v = o?.nonEmpty(k) { return v } } }
        return nil
    }
}

// MARK: JSON helpers

extension Dictionary where Key == String, Value == Any {
    /// The value as the web page would send it back: booleans as 1/0, missing as "".
    func str(_ key: String) -> String {
        guard let v = self[key] else { return "" }
        if v is NSNull { return "" }
        if let n = v as? NSNumber {
            if CFGetTypeID(n) == CFBooleanGetTypeID() { return n.boolValue ? "1" : "0" }
            return n.stringValue
        }
        if let s = v as? String { return s }
        return "\(v)"
    }

    func nonEmpty(_ key: String) -> String? {
        let s = str(key).trimmingCharacters(in: .whitespaces)
        return s.isEmpty ? nil : str(key)
    }

    func int(_ key: String) -> Int {
        if let n = self[key] as? NSNumber { return n.intValue }
        return Int(str(key)) ?? 0
    }

    func truthy(_ key: String) -> Bool {
        let s = str(key).lowercased()
        return s == "1" || s == "true"
    }
}
