import Foundation
import SwiftUI

enum Screen: Equatable {
    case starting
    case connect
    case login(String)
    case main
}

enum AppTab: Hashable { case home, devices, wifi, more }

@MainActor
final class AppModel: ObservableObject {
    @Published var screen: Screen = .connect
    @Published var tab: AppTab = .home

    // connect / sign-in
    @Published var scanning = false
    @Published var scanResults: [ProbeResult] = []
    @Published var customHost = Store.customHost
    @Published var loginBusy = false
    @Published var loginError: String?

    // data
    @Published private(set) var host: String?
    @Published var overview: Overview?
    @Published var devices: DeviceList?
    @Published var wifi: WifiConfig?
    @Published var mesh: [MeshNode]?
    @Published var traffic: [TrafficSample] = []
    @Published var guest: [String: Any]?
    @Published var kids: [String: Any]?
    @Published var lightsOn: Bool?
    @Published var busy = false
    @Published var toast: String?

    private var api: WavlinkAPI?
    /// True while connected through built-in Tailscale instead of the home Wi-Fi.
    @Published private(set) var away = false
    private var lastReconnect = Date.distantPast

    var lastHost: String? { Store.lastHost }

    init() {
        if let h = Store.lastHost {
            if let hash = Store.hash(for: h) {
                screen = .starting
                Task { await signIn(host: h, hash: hash, remember: true) }
            } else {
                screen = .login(h)
            }
        } else {
            scan()
        }
    }

    // MARK: connect

    func scan() {
        guard !scanning else { return }
        Store.customHost = customHost
        scanning = true
        Task {
            var results = await RouterScanner.scan(extra: customHost.isEmpty ? Store.lastHost : customHost)
            // Away from home: the usual addresses may still answer through built-in Tailscale.
            let ts = TailscaleManager.shared
            if !results.contains(where: { $0.state == .wavlink }), await ts.waitUntilRunning(seconds: 6) {
                for i in results.indices where results[i].state != .wavlink {
                    if await ts.reaches(results[i].host) {
                        results[i].state = .wavlink
                        results[i].detail = "Reachable through Tailscale"
                    }
                }
                results.sort { $0.state.rawValue < $1.state.rawValue }
            }
            scanResults = results
            scanning = false
        }
    }

    func findAnother() {
        screen = .connect
        if scanResults.isEmpty { scan() }
    }

    func open(host: String) {
        loginError = nil
        if let hash = Store.hash(for: host) {
            screen = .login(host)
            Task { await signIn(host: host, hash: hash, remember: true) }
        } else {
            screen = .login(host)
        }
    }

    func hasSavedLogin(_ host: String) -> Bool { Store.hash(for: host) != nil }

    func login(host: String, password: String, remember: Bool) {
        Task { await signIn(host: host, hash: WavlinkAPI.hashPassword(password), remember: remember) }
    }

    private func signIn(host: String, hash: String, remember: Bool) async {
        guard !loginBusy else { return }
        loginBusy = true
        loginError = nil
        defer { loginBusy = false }
        guard let a = await connect(host) else {
            loginError = "Can't reach your router. At home, join its Wi-Fi. Away, switch on built-in Tailscale (Away from home)."
            screen = .login(host)
            return
        }
        do {
            try await a.login(hash: hash)
            api = a
            self.host = host
            Store.lastHost = host
            Store.setHash(remember ? hash : nil, for: host)
            clearData()
            tab = .home
            withAnimation(.easeOut(duration: 0.25)) { screen = .main }
            await refreshAll()
        } catch let e as RouterError where e.code >= 10000 || e.code == -1 {
            Store.setHash(nil, for: host)
            loginError = e.message
            screen = .login(host)
        } catch {
            loginError = Self.friendly(error)
            screen = .login(host)
        }
    }

    /// Home Wi-Fi first; otherwise the router through built-in Tailscale.
    private func connect(_ host: String) async -> WavlinkAPI? {
        if await RouterScanner.answersDirectly(host) {
            away = false
            return WavlinkAPI(host: host)
        }
        let ts = TailscaleManager.shared
        if await ts.waitUntilRunning(seconds: 10), let port = ts.proxyPort(), await ts.reaches(host) {
            away = true
            return WavlinkAPI(host: host, proxyPort: port)
        }
        return nil
    }

    /// The phone moved between home Wi-Fi and elsewhere: find the router again and sign back in.
    private func reconnectQuietly() {
        guard let h = host, let hash = Store.hash(for: h), Date().timeIntervalSince(lastReconnect) > 30 else { return }
        lastReconnect = Date()
        Task {
            guard let a = await connect(h) else { return }
            do {
                try await a.login(hash: hash)
                api = a
                await refreshAll()
            } catch {}
        }
    }

    func signOut(forget: Bool) {
        let h = host
        api = nil
        if forget, let h { Store.setHash(nil, for: h) }
        clearData()
        loginError = nil
        screen = h.map { .login($0) } ?? .connect
    }

    private func clearData() {
        overview = nil; devices = nil; wifi = nil; mesh = nil; traffic = []
        guest = nil; kids = nil; lightsOn = nil
    }

    static func friendly(_ e: Error) -> String {
        if let r = e as? RouterError { return r.message }
        let ns = e as NSError
        if ns.domain == NSURLErrorDomain {
            switch ns.code {
            case NSURLErrorTimedOut: return "The router didn't answer in time."
            case NSURLErrorCannotConnectToHost, NSURLErrorNetworkConnectionLost, NSURLErrorNotConnectedToInternet,
                 NSURLErrorCannotFindHost:
                return "Can't reach the router. At home, join its Wi-Fi. Away, switch on built-in Tailscale."
            default: break
            }
        }
        return e.localizedDescription
    }

    // MARK: data

    func refreshAll() async {
        async let a: Void = refreshOverview()
        async let b: Void = refreshDevices()
        async let c: Void = refreshWifi()
        async let d: Void = refreshMesh()
        async let e: Void = loadQuick()
        _ = await (a, b, c, d, e)
    }

    func refreshOverview(quiet: Bool = false) async {
        guard let api else { return }
        do { overview = try await api.overview() } catch { handle(error, quiet: quiet) }
    }

    func refreshDevices(quiet: Bool = false) async {
        guard let api else { return }
        do {
            let list = try await api.devices()
            devices = list
            let s = TrafficSample(at: Date(), down: list.online.reduce(0) { $0 + $1.downKBs },
                                  up: list.online.reduce(0) { $0 + $1.upKBs })
            traffic = Array((traffic + [s]).suffix(60))
        } catch { handle(error, quiet: quiet) }
    }

    func refreshWifi(quiet: Bool = false) async {
        guard let api else { return }
        do { wifi = try await api.wifi() } catch { handle(error, quiet: quiet) }
    }

    func refreshMesh(quiet: Bool = false) async {
        guard let api else { return }
        do { mesh = try await api.meshNodes() } catch { handle(error, quiet: quiet) }
    }

    func loadQuick() async {
        guard let api else { return }
        guest = try? await api.get("net", "wifi_ap_guest")
        kids = try? await api.get("net", "wifi_lt", "&wifi_type=_child")
        if let led = try? await api.set("system", "led", "&act=dump") { lightsOn = led.str("led") == "1" }
    }

    private func handle(_ e: Error, quiet: Bool) {
        if let r = e as? RouterError, r.code == WavlinkAPI.sessionExpired {
            api = nil
            loginError = r.message
            if let h = host { screen = .login(h) }
            return
        }
        if e is URLError { reconnectQuietly() }
        if !quiet { toast = Self.friendly(e) }
    }

    // MARK: actions

    /// Runs a change, shows [done] when it works, then refreshes.
    func run(_ done: String, reloadAfter: Double = 1, tolerateDisconnect: Bool = false,
             _ work: @escaping (WavlinkAPI) async throws -> Void) {
        guard let api, !busy else { return }
        busy = true
        Task {
            do {
                try await work(api)
                toast = done
            } catch let e as URLError where tolerateDisconnect {
                _ = e
                toast = done
            } catch {
                toast = Self.friendly(error)
            }
            try? await Task.sleep(nanoseconds: UInt64(reloadAfter * 1_000_000_000))
            busy = false
            await refreshAll()
        }
    }

    func rename(_ d: Device, to name: String) {
        run("Renamed to \(name)") { try await $0.rename(mac: d.mac, name: name.trimmingCharacters(in: .whitespaces)) }
    }

    func setBlocked(_ d: Device, _ blocked: Bool) {
        run(blocked ? "\(d.name) blocked from the internet" : "\(d.name) allowed again") {
            try await $0.setBlocked(mac: d.mac, blocked: blocked)
        }
    }

    func setLimit(_ d: Device, down: Int, up: Int) {
        run("Speed limit saved") { try await $0.setLimit(mac: d.mac, downKB: down, upKB: up) }
    }

    func forget(_ d: Device) {
        run("Removed \(d.name)") { try await $0.forgetOffline(mac: d.mac) }
    }

    func reboot() {
        run("Restarting. The router will be back in about two minutes.", reloadAfter: 90) { await $0.reboot() }
    }

    func saveWifi(_ b2: BandEdit, _ b5: BandEdit) {
        guard let country = wifi?.country else { return }
        run("Wi-Fi saved. Devices will reconnect in a moment.", reloadAfter: 15, tolerateDisconnect: true) {
            try await $0.saveBands(country: country, b2: b2, b5: b5)
        }
    }

    /// Returns false when guest Wi-Fi needs setting up in the router's web page first.
    func setGuest(_ on: Bool) -> Bool {
        guard let d = guest, let body = Quick.guestData(d, on: on) else { return false }
        run(on ? "Guest Wi-Fi turned on." : "Guest Wi-Fi turned off.", reloadAfter: 12, tolerateDisconnect: true) {
            try await $0.set("net", "wifi_ap_guest", body)
        }
        return true
    }

    func setKids(_ on: Bool) -> Bool {
        guard let d = kids, let body = Quick.kidsData(d, on: on) else { return false }
        run(on ? "Kids' Wi-Fi turned on." : "Kids' Wi-Fi turned off.", reloadAfter: 12, tolerateDisconnect: true) {
            try await $0.set("net", "wifi_lt", body)
        }
        return true
    }

    func setLights(_ on: Bool) {
        run(on ? "Lights on." : "Lights off.") { try await $0.set("system", "led", "&act=\(on ? "on" : "off")") }
    }
}
