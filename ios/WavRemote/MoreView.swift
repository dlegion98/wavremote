import SwiftUI
import SafariServices

struct RouterWebPage: UIViewControllerRepresentable {
    let host: String
    func makeUIViewController(context: Context) -> SFSafariViewController {
        let vc = SFSafariViewController(url: URL(string: "http://\(host)/") ?? URL(string: "http://192.168.20.1/")!)
        vc.preferredBarTintColor = UIColor(Pro.bg)
        vc.preferredControlTintColor = UIColor(Pro.accent)
        return vc
    }
    func updateUIViewController(_ vc: SFSafariViewController, context: Context) {}
}

struct MoreView: View {
    @EnvironmentObject var model: AppModel
    @State private var confirmRestart = false
    @State private var confirmSignOut = false
    @State private var showWeb = false
    @State private var restartTarget: MeshNode?
    @State private var confirmAll = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    SectionLabel("Mesh points")
                    VStack(spacing: 12) {
                        node("Main router", model.host ?? "", "Online", Pro.green) { confirmRestart = true }
                        ForEach(model.mesh ?? []) { n in
                            node(n.name, n.ip ?? "", n.linkLabel, n.linkLabel == "Weak link" ? Pro.amber : Pro.green) {
                                restartTarget = n
                            }
                        }
                    }
                    .panel()
                    Button { confirmAll = true } label: { Label("Restart everything", systemImage: "arrow.triangle.2.circlepath") }
                        .buttonStyle(SecondaryButtonStyle())
                        .disabled(model.busy)

                    SectionLabel("Router")
                    VStack(alignment: .leading, spacing: 8) {
                        info("Model", model.overview?.model ?? "–")
                        info("Firmware", model.overview?.firmware ?? "–")
                        info("Internet", model.overview?.wanType ?? "–")
                        info("Public IP", model.overview?.wanIp ?? "–")
                        if model.overview?.updateAvailable == true {
                            TagView(text: "Firmware update available", color: Pro.amber)
                        }
                    }
                    .panel()

                    Button { showWeb = true } label: { Label("All settings (router web page)", systemImage: "safari") }
                        .buttonStyle(SecondaryButtonStyle())
                    Button { confirmRestart = true } label: { Label("Restart the router", systemImage: "arrow.clockwise") }
                        .buttonStyle(SecondaryButtonStyle())
                        .disabled(model.busy)

                    SectionLabel("Away from home")
                    TailscaleCard()

                    SectionLabel("App")
                    VStack(alignment: .leading, spacing: 8) {
                        info("Version", (Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String) ?? "–")
                        Text("Updates arrive through SideStore.").font(.pro(13)).foregroundStyle(Pro.muted)
                    }
                    .panel()

                    Button(role: .destructive) { confirmSignOut = true } label: {
                        Label("Sign out", systemImage: "rectangle.portrait.and.arrow.right")
                    }
                    .buttonStyle(SecondaryButtonStyle())
                }
                .padding(16)
            }
            .refreshable { await model.refreshAll() }
            .awayBanner()
            .background(Pro.bg)
            .navigationTitle("More")
            .sheet(isPresented: $showWeb) { RouterWebPage(host: model.host ?? "192.168.20.1").ignoresSafeArea() }
            .confirmationDialog("Restart the router?", isPresented: $confirmRestart, titleVisibility: .visible) {
                Button("Restart", role: .destructive) { model.reboot() }
            } message: { Text("Everything loses internet for about two minutes.") }
            .confirmationDialog("Restart \(restartTarget?.name ?? "")?",
                                isPresented: Binding(get: { restartTarget != nil }, set: { if !$0 { restartTarget = nil } }),
                                titleVisibility: .visible) {
                Button("Restart", role: .destructive) {
                    if let n = restartTarget, let ip = n.ip { model.restartNode(name: n.name, ip: ip) }
                    restartTarget = nil
                }
            } message: { Text("Devices near it lose Wi-Fi for about 2 minutes. Everything else keeps working.") }
            .confirmationDialog("Restart everything?", isPresented: $confirmAll, titleVisibility: .visible) {
                Button("Restart all", role: .destructive) { model.restartEverything() }
            } message: { Text("Every mesh point and the main router restart. All devices lose internet for about 3 minutes.") }
            .confirmationDialog("Sign out?", isPresented: $confirmSignOut, titleVisibility: .visible) {
                Button("Sign out") { model.signOut(forget: false) }
                Button("Sign out and forget the password", role: .destructive) { model.signOut(forget: true) }
            }
        }
    }

    private func node(_ name: String, _ ip: String, _ status: String, _ color: Color,
                      restart: @escaping () -> Void) -> some View {
        HStack(spacing: 12) {
            IconBadge(symbol: "wifi.router", tint: color)
            VStack(alignment: .leading, spacing: 2) {
                Text(name).font(.pro(16, .semibold))
                Text(ip).font(.pro(13)).foregroundStyle(Pro.muted)
            }
            Spacer()
            TagView(text: status, color: color)
            Button(action: restart) {
                Image(systemName: "arrow.clockwise").font(.system(size: 15, weight: .semibold))
                    .frame(width: 34, height: 34)
                    .background(Pro.panelHigh)
                    .clipShape(RoundedRectangle(cornerRadius: 6))
            }
            .accessibilityLabel("Restart \(name)")
            .disabled(model.busy)
        }
    }

    private func info(_ k: String, _ v: String) -> some View {
        HStack {
            Text(k).font(.pro(15)).foregroundStyle(Pro.muted)
            Spacer()
            Text(v).font(.pro(15, .medium)).lineLimit(1)
        }
    }
}
