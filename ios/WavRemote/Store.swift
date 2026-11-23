import Foundation
import Security

/// Remembers the router address (UserDefaults) and its sign-in (Keychain).
enum Store {
    private static let defaults = UserDefaults.standard
    private static let service = "com.dayo.wavremote.login"

    static var lastHost: String? {
        get { defaults.string(forKey: "lastHost") }
        set { defaults.set(newValue, forKey: "lastHost") }
    }

    static var customHost: String {
        get { defaults.string(forKey: "customHost") ?? "" }
        set { defaults.set(newValue, forKey: "customHost") }
    }

    static func hash(for host: String) -> String? {
        let q: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: host,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var out: AnyObject?
        guard SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess, let d = out as? Data else { return nil }
        return String(data: d, encoding: .utf8)
    }

    static func setHash(_ hash: String?, for host: String) {
        let base: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: host,
        ]
        SecItemDelete(base as CFDictionary)
        guard let hash else { return }
        var add = base
        add[kSecValueData as String] = Data(hash.utf8)
        add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        SecItemAdd(add as CFDictionary, nil)
    }
}

enum ProbeState: Int { case wavlink, otherRouter, unreachable }

struct ProbeResult: Identifiable {
    var host: String
    var state: ProbeState
    var detail: String
    var id: String { host }
}

/// Looks for the router at the usual addresses.
enum RouterScanner {
    static let defaultHosts = ["192.168.10.1", "192.168.20.1", "wavlogin.link", "192.168.0.1", "192.168.1.1"]

    static func scan(extra: String?) async -> [ProbeResult] {
        var hosts: [String] = []
        if let e = extra?.trimmingCharacters(in: .whitespaces), !e.isEmpty { hosts.append(e) }
        for h in defaultHosts where !hosts.contains(h) { hosts.append(h) }
        let results = await withTaskGroup(of: ProbeResult.self) { group -> [ProbeResult] in
            for h in hosts { group.addTask { await probe(h) } }
            var all: [ProbeResult] = []
            for await r in group { all.append(r) }
            return all
        }
        return results.sorted { $0.state.rawValue < $1.state.rawValue }
    }

    /// Quick check that something answers at host on this network (not through Tailscale).
    static func answersDirectly(_ host: String, timeout: Double = 2.5) async -> Bool {
        guard let url = URL(string: "http://\(host)/") else { return false }
        var req = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: timeout)
        req.setValue(WavlinkAPI.browserUA, forHTTPHeaderField: "User-Agent")
        return (try? await URLSession.shared.data(for: req)) != nil
    }

    static func probe(_ host: String) async -> ProbeResult {
        guard let url = URL(string: "http://\(host)/") else {
            return ProbeResult(host: host, state: .unreachable, detail: "Not a valid address")
        }
        var req = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 4)
        req.setValue(WavlinkAPI.browserUA, forHTTPHeaderField: "User-Agent")
        do {
            let (data, resp) = try await URLSession.shared.data(for: req)
            let html = String(decoding: data.prefix(200_000), as: UTF8.self)
            let server = (resp as? HTTPURLResponse)?.value(forHTTPHeaderField: "Server") ?? ""
            let isWavlink = html.range(of: "wavlink", options: .caseInsensitive) != nil ||
                server.range(of: "wavlink", options: .caseInsensitive) != nil
            return ProbeResult(host: host, state: isWavlink ? .wavlink : .otherRouter,
                               detail: isWavlink ? "Wavlink router" : "Answers, but isn't a Wavlink")
        } catch {
            return ProbeResult(host: host, state: .unreachable, detail: "No answer")
        }
    }
}
