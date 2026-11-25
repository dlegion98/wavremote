import Foundation
import SwiftUI

enum Severity: Int { case problem, warning, tip }

enum Fix: Equatable {
    case restartNode(String, String)   // name, ip
    case restartRouter
    case runCheck
    case showDevices
    case openWeb(String)               // label

    var label: String {
        switch self {
        case .restartNode(let name, _): return "Restart \(name)"
        case .restartRouter: return "Restart the router"
        case .runCheck: return "Find the cause"
        case .showDevices: return "See devices"
        case .openWeb(let label): return label
        }
    }
}

struct Issue: Identifiable {
    var id: String
    var severity: Severity
    var title: String
    var detail: String
    var fix: Fix? = nil
}

/// Turns what the app knows about the network into plain-language problems and what to do about them.
enum Health {
    private static let knownKey = "knownMeshNodes"

    static func issues(overview o: Overview?, devices: DeviceList?, mesh: [MeshNode]?, wifi: WifiConfig?,
                       loginHash: String?, diag: [String: Any]?) -> [Issue] {
        var out: [Issue] = []

        // Internet
        if let o, !o.internetUp {
            if !o.wanLink {
                out.append(Issue(id: "wan-cable", severity: .problem, title: "Internet cable unplugged",
                    detail: "Nothing is connected to the router's internet port, or your modem or fibre box is off. Check the cable between the modem and the router's internet (WAN) port, and that the modem has power."))
            } else if let d = diag, d.str("wan_ip") == "0" {
                out.append(Issue(id: "wan-ip", severity: .problem, title: "Your provider isn't giving the router an address",
                    detail: "The cable is fine, but your internet provider didn't hand the router an internet address. Restart your modem or fibre box, wait 2 minutes, then restart the router." +
                        (o.wanType.contains("PPPoE") ? " As you use PPPoE, also check the username and password in the router's internet settings." : ""),
                    fix: .openWeb("Open internet settings")))
            } else if let d = diag, d.str("ping_gateway") == "0" {
                out.append(Issue(id: "wan-gateway", severity: .problem, title: "Can't reach your provider",
                    detail: "The router has an address but your provider's equipment isn't answering. Restart your modem or fibre box. If that doesn't help, it's probably an outage: check your provider's service status."))
            } else if let d = diag, d.str("ping_ip") == "0" {
                out.append(Issue(id: "wan-outage", severity: .problem, title: "Your provider's network is down",
                    detail: "The router reaches your provider, but the internet beyond it doesn't answer. This is usually an outage on their side. Check your provider's service status page, or call them."))
            } else {
                out.append(Issue(id: "wan-down", severity: .problem, title: "No internet from your provider",
                    detail: "The router is connected to your modem but isn't getting internet. Restart your modem or fibre box, wait 2 minutes, then restart the router. Tap Find the cause to check each step.",
                    fix: diag == nil ? .runCheck : .restartRouter))
            }
        }
        if let d = diag {
            if o?.internetUp == true && d.str("dns_analysis") == "0" {
                out.append(Issue(id: "dns", severity: .problem, title: "Websites won't load: name lookup (DNS) is failing",
                    detail: "The internet connection works, but looking up website names fails. Switch to a public DNS server such as 1.1.1.1 in the router's internet settings, or turn off AdGuard if you use it.",
                    fix: .openWeb("Open internet settings")))
            }
            if let idle = Int(d.str("cputime")), 100 - idle >= 90 {
                out.append(Issue(id: "cpu", severity: .warning, title: "The router is overloaded",
                    detail: "Its processor is almost fully busy, which slows everything down. Restarting it usually clears this.", fix: .restartRouter))
            }
            if let total = Double(d.str("totalram")), let free = Double(d.str("freemem")), total > 0, (total - free) / total >= 0.92 {
                out.append(Issue(id: "mem", severity: .warning, title: "The router is almost out of memory",
                    detail: "It can become slow or drop connections. Restart it to free memory.", fix: .restartRouter))
            }
        }

        // Mesh points
        if let mesh {
            let defaults = UserDefaults.standard
            var known = Set(defaults.stringArray(forKey: knownKey) ?? [])
            let present = Set(mesh.map(key))
            for k in known.subtracting(present).sorted() {
                let name = k.components(separatedBy: "|").last ?? "A mesh point"
                out.append(Issue(id: "node-off-\(k)", severity: .problem, title: "\(name) is offline",
                    detail: "This mesh point isn't connected to your network. Check it has power, then unplug it for 10 seconds and plug it back in. If it keeps dropping, move it closer to the main router."))
            }
            if !present.isEmpty && !present.isSubset(of: known) {
                known.formUnion(present)
                defaults.set(Array(known), forKey: knownKey)
            }
            for n in mesh {
                guard let r = n.rssi, r < -73 else { continue }
                out.append(Issue(id: "node-weak-\(key(n))", severity: .warning, title: "\(n.name) has a weak link to the router",
                    detail: "It's too far from the main router or behind thick walls, so devices near it get slower Wi-Fi. Move it a little closer, or restart it.",
                    fix: n.ip.map { Fix.restartNode(n.name, $0) }))
            }
        }

        // Security
        if let h = loginHash, h == WavlinkAPI.hashPassword("admin") {
            out.append(Issue(id: "admin-default", severity: .warning, title: "Your router still uses the default password",
                detail: "Anyone on your Wi-Fi could sign in to the router with \"admin\". Change it to something only you know.",
                fix: .openWeb("Change it in the router's web page")))
        }
        if let w = wifi {
            var open: [String] = []
            if w.on2 && w.open2 { open.append("\"\(w.ssid2)\"") }
            if w.on5 && w.open5 && w.ssid5 != w.ssid2 { open.append("\"\(w.ssid5)\"") }
            if !open.isEmpty {
                out.append(Issue(id: "wifi-open", severity: .warning, title: "Your Wi-Fi has no password",
                    detail: "Anyone nearby can join \(open.joined(separator: " and ")) and use your internet. Add a password in the Wi-Fi tab."))
            }
        }
        if o?.updateAvailable == true {
            out.append(Issue(id: "firmware", severity: .warning, title: "A firmware update is available",
                detail: "It brings fixes and security patches for the router.", fix: .openWeb("Update in the router's web page")))
        }

        // Tips
        let weak = (devices?.online ?? []).filter { !$0.isWired && (1...25).contains($0.rssi) }
        if !weak.isEmpty {
            let names = weak.prefix(3).map(\.name).joined(separator: ", ") + (weak.count > 3 ? " and \(weak.count - 3) more" : "")
            out.append(Issue(id: "weak-devices", severity: .tip,
                title: weak.count == 1 ? "\(weak[0].name) has a weak signal" : "\(weak.count) devices have a weak signal",
                detail: "\(names) \(weak.count == 1 ? "is" : "are") far from the nearest mesh point, so \(weak.count == 1 ? "it" : "they") may be slow or drop out. Moving a mesh point closer helps.",
                fix: .showDevices))
        }
        let days = (o?.uptime ?? 0) / 86400
        if days >= 30 {
            out.append(Issue(id: "uptime", severity: .tip, title: "The router hasn't restarted in \(days) days",
                detail: "A restart every few weeks keeps it running smoothly.", fix: .restartRouter))
        }
        return out.sorted { $0.severity.rawValue < $1.severity.rawValue }
    }

    private static func key(_ n: MeshNode) -> String { "\(n.mac ?? n.ip ?? n.name)|\(n.name)" }
}

/// "Network health" panel: what's wrong, in plain words, with a fix button.
struct HealthPanel: View {
    @EnvironmentObject var model: AppModel
    @State private var showWeb = false
    @State private var confirm: Fix?

    var body: some View {
        let issues = model.issues
        VStack(alignment: .leading, spacing: 0) {
            if issues.isEmpty {
                HStack(spacing: 12) {
                    Image(systemName: "checkmark.circle.fill").foregroundStyle(Pro.green).font(.system(size: 20))
                    VStack(alignment: .leading, spacing: 2) {
                        Text(model.diagRunning ? "Checking your network…" : "Everything looks good").font(.pro(16, .semibold))
                        Text(model.diagRunning ? "This takes about 20 seconds." : "No problems found with your internet, mesh points or Wi-Fi.")
                            .font(.pro(13)).foregroundStyle(Pro.muted)
                    }
                }
                .padding(.bottom, 12)
            }
            ForEach(issues) { issue in
                VStack(alignment: .leading, spacing: 8) {
                    HStack(alignment: .top, spacing: 12) {
                        Image(systemName: issue.severity == .problem ? "exclamationmark.octagon.fill"
                              : issue.severity == .warning ? "exclamationmark.triangle.fill" : "lightbulb.fill")
                            .foregroundStyle(issue.severity == .problem ? Pro.red : issue.severity == .warning ? Pro.amber : Pro.accent)
                            .font(.system(size: 18))
                        VStack(alignment: .leading, spacing: 4) {
                            Text(issue.title).font(.pro(16, .semibold))
                            Text(issue.detail).font(.pro(14)).foregroundStyle(Pro.muted)
                        }
                    }
                    if let fix = issue.fix {
                        Button(fix == .runCheck && model.diagRunning ? "Checking…" : fix.label) { handle(fix) }
                            .buttonStyle(SecondaryButtonStyle())
                            .disabled(model.busy || (fix == .runCheck && model.diagRunning))
                    }
                }
                .padding(.bottom, 14)
            }
            Divider().overlay(Pro.border).padding(.bottom, 10)
            Button {
                model.runHealthCheck()
            } label: {
                Label(model.diagRunning ? "Running a full check…" : "Run a full check", systemImage: "stethoscope")
                    .font(.pro(15, .semibold))
            }
            .disabled(model.diagRunning)
        }
        .panel()
        .task(id: model.overview?.internetUp) {
            // No internet: run the router's own check straight away to pin down why.
            if model.overview?.internetUp == false && model.diag == nil { model.runHealthCheck() }
        }
        .sheet(isPresented: $showWeb) { RouterWebPage(host: model.apiHost ?? "192.168.20.1").ignoresSafeArea() }
        .confirmationDialog(confirmTitle, isPresented: Binding(get: { confirm != nil }, set: { if !$0 { confirm = nil } }),
                            titleVisibility: .visible) {
            Button("Restart", role: .destructive) {
                if let c = confirm {
                    if case .restartNode(let name, let ip) = c { model.restartNode(name: name, ip: ip) } else { model.reboot() }
                }
                confirm = nil
            }
        }
    }

    private var confirmTitle: String {
        if case .restartNode(let name, _) = confirm { return "Restart \(name)?" }
        return "Restart the router?"
    }

    private func handle(_ fix: Fix) {
        switch fix {
        case .restartNode, .restartRouter: confirm = fix
        case .runCheck: model.runHealthCheck()
        case .showDevices: model.tab = .devices
        case .openWeb: showWeb = true
        }
    }
}
