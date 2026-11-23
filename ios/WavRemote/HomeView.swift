import SwiftUI
import Charts

struct MainView: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.scenePhase) private var phase

    var body: some View {
        TabView(selection: $model.tab) {
            HomeView().tabItem { Label("Overview", systemImage: "square.grid.2x2") }.tag(AppTab.home)
            DevicesView().tabItem { Label("Devices", systemImage: "laptopcomputer.and.iphone") }.tag(AppTab.devices)
            WifiView().tabItem { Label("Wi-Fi", systemImage: "wifi") }.tag(AppTab.wifi)
            MoreView().tabItem { Label("More", systemImage: "slider.horizontal.3") }.tag(AppTab.more)
        }
        .safeAreaInset(edge: .top, spacing: 0) {
            if model.away {
                HStack(spacing: 8) {
                    Image(systemName: "globe").font(.system(size: 13, weight: .semibold))
                    Text("Away from home, connected through Tailscale").font(.pro(13, .semibold))
                }
                .foregroundStyle(Pro.accent)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 6)
                .background(Pro.accent.opacity(0.12))
            }
        }
        .overlay(alignment: .top) {
            if model.busy {
                ProgressView().progressViewStyle(.linear).tint(Pro.accent)
            }
        }
        .overlay(alignment: .bottom) {
            if let t = model.toast {
                Text(t).font(.pro(15, .medium)).foregroundStyle(Pro.text)
                    .padding(.horizontal, 16).padding(.vertical, 12)
                    .background(Pro.panelHigh)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Pro.border))
                    .padding(.horizontal, 16).padding(.bottom, 64)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .task(id: t) {
                        try? await Task.sleep(nanoseconds: 3_500_000_000)
                        withAnimation { model.toast = nil }
                    }
            }
        }
        .animation(.easeOut(duration: 0.2), value: model.toast)
        // Live refresh of the visible tab every 10 s while the app is open.
        .task(id: model.tab) {
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 10_000_000_000)
                guard phase == .active else { continue }
                switch model.tab {
                case .home:
                    await model.refreshOverview(quiet: true)
                    await model.refreshDevices(quiet: true)
                case .devices:
                    await model.refreshDevices(quiet: true)
                default: break
                }
            }
        }
    }
}

/// Animated score ring (Wi-Fi experience).
struct RingGauge: View {
    let score: Int
    let color: Color
    @State private var shown: Double = 0

    var body: some View {
        ZStack {
            Circle().trim(from: 0, to: 0.75).stroke(Pro.border, style: StrokeStyle(lineWidth: 9, lineCap: .round))
                .rotationEffect(.degrees(135))
            Circle().trim(from: 0, to: 0.75 * shown / 100).stroke(color, style: StrokeStyle(lineWidth: 9, lineCap: .round))
                .rotationEffect(.degrees(135))
            Text("\(score)").font(.pro(22, .semibold))
        }
        .frame(width: 78, height: 78)
        .onAppear { withAnimation(.easeOut(duration: 0.8)) { shown = Double(score) } }
        .onChange(of: score) { s in withAnimation(.easeOut(duration: 0.6)) { shown = Double(s) } }
    }
}

struct Stat: View {
    let value: String
    let label: String
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value).font(.pro(22, .semibold)).lineLimit(1).minimumScaleFactor(0.6)
            Text(label).font(.pro(13)).foregroundStyle(Pro.muted)
        }
        .panel(padding: 14)
    }
}

struct ToggleTile: View {
    let label: String
    let symbol: String
    let on: Bool?
    let action: (Bool) -> Void

    var body: some View {
        let isOn = on ?? false
        Button { if let on { action(!on) } } label: {
            VStack(alignment: .leading, spacing: 10) {
                Image(systemName: symbol).font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(isOn ? .white : Pro.accent)
                    .frame(width: 36, height: 36)
                    .background(isOn ? Color.white.opacity(0.18) : Pro.accent.opacity(0.12))
                    .clipShape(RoundedRectangle(cornerRadius: 6))
                VStack(alignment: .leading, spacing: 1) {
                    Text(label).font(.pro(14, .semibold)).lineLimit(1)
                    Text(on == nil ? "…" : isOn ? "On" : "Off").font(.pro(13)).opacity(0.8)
                }
                .foregroundStyle(isOn ? .white : Pro.text)
            }
            .panel(padding: 14, fill: isOn ? Pro.accent : Pro.panel)
            .animation(.easeOut(duration: 0.2), value: isOn)
        }
        .buttonStyle(.plain)
        .disabled(on == nil)
    }
}

struct HomeView: View {
    @EnvironmentObject var model: AppModel
    @State private var setupNeeded: String?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    header
                    if let list = model.devices, let score = experienceScore(list) {
                        HStack(spacing: 16) {
                            RingGauge(score: score, color: score >= 60 ? Pro.green : score >= 40 ? Pro.amber : Pro.red)
                            VStack(alignment: .leading, spacing: 4) {
                                Text("Wi-Fi experience").font(.pro(14, .semibold)).foregroundStyle(Pro.muted)
                                Text(experienceLabel(score)).font(.pro(22, .semibold))
                                Text("Based on the signal of \(list.online.count) online devices").font(.pro(13)).foregroundStyle(Pro.muted)
                            }
                        }
                        .panel()
                    }
                    stats
                    throughput
                    SectionLabel("Quick controls")
                    HStack(spacing: 10) {
                        ToggleTile(label: "Guest Wi-Fi", symbol: "person.2.fill",
                                   on: model.guest.map { $0.str("guest_enable") == "1" }) { on in
                            if !model.setGuest(on) { setupNeeded = "Guest Wi-Fi needs a name and password first. Set it up in the router's web page (More)." }
                        }
                        ToggleTile(label: "Kids' Wi-Fi", symbol: "figure.and.child.holdinghands",
                                   on: model.kids.map { $0.str("enable") == "1" }) { on in
                            if !model.setKids(on) { setupNeeded = "Kids' Wi-Fi needs a password and at least one blocked time first. Set it up in the router's web page (More)." }
                        }
                        ToggleTile(label: "Lights", symbol: "lightbulb.fill", on: model.lightsOn) { model.setLights($0) }
                    }
                    topClients
                }
                .padding(16)
            }
            .refreshable { await model.refreshAll() }
            .background(Pro.bg)
            .navigationTitle(model.overview?.model ?? "WavRemote")
            .navigationBarTitleDisplayMode(.inline)
            .alert("Set up first", isPresented: Binding(get: { setupNeeded != nil }, set: { if !$0 { setupNeeded = nil } })) {
                Button("OK", role: .cancel) {}
            } message: { Text(setupNeeded ?? "") }
        }
    }

    private var header: some View {
        let up = model.overview?.internetUp
        return HStack(spacing: 12) {
            Circle().fill(up == nil ? Pro.amber : up! ? Pro.green : Pro.red).frame(width: 12, height: 12)
            VStack(alignment: .leading, spacing: 2) {
                Text(up == nil ? "Checking…" : up! ? "Internet online" : "Internet offline").font(.pro(26, .semibold))
                if let o = model.overview {
                    Text([o.wanType, o.wanIp].compactMap { $0 }.joined(separator: ", "))
                        .font(.pro(14)).foregroundStyle(Pro.muted)
                }
            }
        }
        .padding(.vertical, 4)
    }

    private var stats: some View {
        let o = model.overview
        return LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
            Button { model.tab = .devices } label: {
                Stat(value: model.devices.map { "\($0.online.count)" } ?? o?.onlineDevices.map { "\($0)" } ?? "–", label: "Devices online")
            }.buttonStyle(.plain)
            Stat(value: o?.meshNodes.map { "\($0 + 1)" } ?? "–", label: "Mesh points")
            Stat(value: o?.uptime.map(formatDuration) ?? "–", label: "Router uptime")
            Stat(value: model.traffic.last.map { formatSpeed($0.down) } ?? "–", label: "Download now")
        }
    }

    private var throughput: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("Throughput").font(.pro(14, .semibold)).foregroundStyle(Pro.muted)
                Spacer()
                Label("Down", systemImage: "circle.fill").foregroundStyle(Pro.down)
                Label("Up", systemImage: "circle.fill").foregroundStyle(Pro.up)
            }
            .font(.pro(12))
            .labelStyle(DotLabel())
            if model.traffic.count < 2 {
                Text("The graph fills in while this screen is open.").font(.pro(13)).foregroundStyle(Pro.muted)
                    .frame(maxWidth: .infinity, minHeight: 120)
            } else {
                Chart(model.traffic) { s in
                    AreaMark(x: .value("Time", s.at), y: .value("Down", s.down))
                        .foregroundStyle(Pro.down.opacity(0.25))
                        .interpolationMethod(.monotone)
                    LineMark(x: .value("Time", s.at), y: .value("Down", s.down), series: .value("Dir", "Down"))
                        .foregroundStyle(Pro.down)
                        .interpolationMethod(.monotone)
                    LineMark(x: .value("Time", s.at), y: .value("Up", s.up), series: .value("Dir", "Up"))
                        .foregroundStyle(Pro.up)
                        .interpolationMethod(.monotone)
                }
                .chartXAxis(.hidden)
                .chartYAxis {
                    AxisMarks(position: .leading) { v in
                        AxisGridLine().foregroundStyle(Pro.border)
                        AxisValueLabel {
                            if let k = v.as(Int.self) { Text(formatSpeed(k)).font(.pro(11)).foregroundStyle(Pro.muted) }
                        }
                    }
                }
                .frame(height: 140)
                .animation(.easeOut(duration: 0.4), value: model.traffic.count)
            }
        }
        .panel()
    }

    @ViewBuilder private var topClients: some View {
        if let list = model.devices {
            let top = list.online.sorted { ($0.downKBs + $0.upKBs) > ($1.downKBs + $1.upKBs) }.prefix(5)
            if !top.isEmpty {
                SectionLabel("Busiest right now")
                VStack(spacing: 12) {
                    ForEach(Array(top)) { d in
                        HStack {
                            Image(systemName: deviceSymbol(d)).foregroundStyle(Pro.accent).frame(width: 24)
                            Text(d.name).font(.pro(15, .medium)).lineLimit(1)
                            Spacer()
                            Text("↓ \(formatSpeed(d.downKBs))  ↑ \(formatSpeed(d.upKBs))").font(.pro(13)).foregroundStyle(Pro.muted)
                        }
                    }
                }
                .panel()
            }
        }
    }
}

struct DotLabel: LabelStyle {
    func makeBody(configuration: Configuration) -> some View {
        HStack(spacing: 5) { configuration.icon.font(.system(size: 7)); configuration.title.foregroundStyle(Pro.muted) }
    }
}
