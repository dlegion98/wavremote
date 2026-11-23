import SwiftUI

func deviceSymbol(_ d: Device) -> String {
    let n = d.name.lowercased()
    if d.isWired { return "cable.connector" }
    if n.contains("iphone") || n.contains("phone") || n.contains("galaxy") || n.contains("pixel") || n.contains("android") { return "iphone" }
    if n.contains("ipad") || n.contains("tab") { return "ipad" }
    if n.contains("mac") || n.contains("laptop") || n.contains("book") { return "laptopcomputer" }
    if n.contains("tv") || n.contains("roku") || n.contains("chromecast") || n.contains("fire") { return "tv" }
    if n.contains("watch") { return "applewatch" }
    if n.contains("echo") || n.contains("homepod") || n.contains("speaker") || n.contains("sonos") { return "hifispeaker" }
    if n.contains("ps5") || n.contains("xbox") || n.contains("switch") || n.contains("playstation") { return "gamecontroller" }
    if n.contains("printer") { return "printer" }
    if n.contains("cam") || n.contains("ring") || n.contains("doorbell") { return "video" }
    return "desktopcomputer"
}

struct SignalBars: View {
    let percent: Int
    var body: some View {
        let lit = percent >= 75 ? 4 : percent >= 50 ? 3 : percent >= 25 ? 2 : percent > 0 ? 1 : 0
        HStack(alignment: .bottom, spacing: 2) {
            ForEach(0..<4, id: \.self) { i in
                RoundedRectangle(cornerRadius: 1)
                    .fill(i < lit ? Pro.accent : Pro.border)
                    .frame(width: 4, height: CGFloat(5 + i * 3))
            }
        }
    }
}

enum DeviceFilter: String, CaseIterable { case online = "Online", offline = "Offline", blocked = "Blocked" }

struct DevicesView: View {
    @EnvironmentObject var model: AppModel
    @State private var query = ""
    @State private var filter: DeviceFilter = .online
    @State private var selected: Device?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Picker("Show", selection: $filter) {
                        ForEach(DeviceFilter.allCases, id: \.self) { Text($0.rawValue).tag($0) }
                    }
                    .pickerStyle(.segmented)

                    if let list = model.devices {
                        let shown = items(list)
                        if shown.isEmpty {
                            Text(query.isEmpty ? "Nothing here right now." : "No device matches \"\(query)\".")
                                .font(.pro(15)).foregroundStyle(Pro.muted).padding(.top, 24)
                                .frame(maxWidth: .infinity)
                        } else {
                            VStack(spacing: 0) {
                                ForEach(shown) { d in
                                    Button { selected = d } label: { DeviceRow(d: d) }.buttonStyle(.plain)
                                    if d.id != shown.last?.id { Divider().overlay(Pro.border).padding(.leading, 56) }
                                }
                            }
                            .panel(padding: 4)
                        }
                    } else {
                        ProgressView().frame(maxWidth: .infinity).padding(.top, 40)
                    }
                }
                .padding(16)
            }
            .refreshable { await model.refreshDevices() }
            .awayBanner()
            .searchable(text: $query, prompt: "Name, IP or MAC")
            .background(Pro.bg)
            .navigationTitle("Devices")
            .sheet(item: $selected) { d in DeviceSheet(device: d).environmentObject(model).presentationDetents([.medium, .large]) }
        }
    }

    private func items(_ list: DeviceList) -> [Device] {
        let base: [Device]
        switch filter {
        case .online: base = list.online
        case .offline: base = list.offline
        case .blocked: base = (list.online + list.offline).filter { $0.blocked }
        }
        let q = query.trimmingCharacters(in: .whitespaces).lowercased()
        if q.isEmpty { return base }
        return base.filter { $0.name.lowercased().contains(q) || $0.ip.contains(q) || $0.mac.lowercased().contains(q) }
    }
}

struct DeviceRow: View {
    let d: Device
    var body: some View {
        HStack(spacing: 12) {
            IconBadge(symbol: deviceSymbol(d), tint: d.online ? Pro.accent : Pro.muted, size: 40)
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(d.name).font(.pro(16, .semibold)).lineLimit(1)
                    if d.blocked { TagView(text: "Blocked", color: Pro.red) }
                    if d.hasLimit { TagView(text: "Limited", color: Pro.amber) }
                }
                Text(d.online ? "\(d.connection), \(d.ip)" : "Offline, \(d.ip)")
                    .font(.pro(13)).foregroundStyle(Pro.muted).lineLimit(1)
            }
            Spacer()
            if d.online {
                VStack(alignment: .trailing, spacing: 4) {
                    if !d.isWired { SignalBars(percent: min(100, Int(Double(d.rssi) * 1.15))) }
                    Text("↓ \(formatSpeed(d.downKBs))").font(.pro(12)).foregroundStyle(Pro.muted)
                }
            }
        }
        .padding(.horizontal, 10).padding(.vertical, 10)
        .contentShape(Rectangle())
    }
}

struct DeviceSheet: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss
    let device: Device
    @State private var name = ""
    @State private var down = ""
    @State private var up = ""

    /// Latest copy from the router, so the sheet follows changes.
    private var d: Device {
        guard let list = model.devices else { return device }
        return (list.online + list.offline).first { $0.id == device.id } ?? device
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 14) {
                    IconBadge(symbol: deviceSymbol(d), size: 52)
                    VStack(alignment: .leading) {
                        Text(d.name).font(.pro(22, .semibold))
                        Text(d.online ? d.connection : "Offline").font(.pro(14)).foregroundStyle(Pro.muted)
                    }
                }
                LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
                    Stat(value: formatSpeed(d.downKBs), label: "Download")
                    Stat(value: formatSpeed(d.upKBs), label: "Upload")
                    Stat(value: d.isWired ? "Wired" : "\(min(100, Int(Double(d.rssi) * 1.15)))%", label: "Signal")
                    Stat(value: formatDuration(d.onlineSec), label: "Connected for")
                }
                VStack(alignment: .leading, spacing: 8) {
                    row("IP address", d.ip)
                    row("MAC address", d.mac.uppercased())
                }
                .panel()

                Toggle(isOn: Binding(get: { d.blocked }, set: { model.setBlocked(d, $0) })) {
                    VStack(alignment: .leading) {
                        Text("Block internet").font(.pro(16, .semibold))
                        Text("It stays on Wi-Fi but can't get online.").font(.pro(13)).foregroundStyle(Pro.muted)
                    }
                }
                .tint(Pro.red)
                .panel()

                VStack(alignment: .leading, spacing: 10) {
                    ProField(label: "Name", text: $name)
                    Button("Rename") { model.rename(d, to: name) }
                        .buttonStyle(SecondaryButtonStyle())
                        .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty || name == d.name || model.busy)
                }
                .panel()

                VStack(alignment: .leading, spacing: 10) {
                    Text("Speed limit").font(.pro(16, .semibold))
                    Text("In KB/s. 0 means no limit.").font(.pro(13)).foregroundStyle(Pro.muted)
                    HStack(spacing: 10) {
                        ProField(label: "Download", text: $down, keyboard: .numberPad)
                        ProField(label: "Upload", text: $up, keyboard: .numberPad)
                    }
                    Button("Save limit") { model.setLimit(d, down: Int(down) ?? 0, up: Int(up) ?? 0) }
                        .buttonStyle(SecondaryButtonStyle())
                        .disabled(model.busy)
                }
                .panel()

                if !d.online {
                    Button("Remove from list") { model.forget(d); dismiss() }
                        .buttonStyle(SecondaryButtonStyle())
                }
            }
            .padding(20)
        }
        .background(Pro.bg)
        .onAppear {
            name = d.name
            down = "\(d.limitDown)"
            up = "\(d.limitUp)"
        }
    }

    private func row(_ k: String, _ v: String) -> some View {
        HStack {
            Text(k).font(.pro(15)).foregroundStyle(Pro.muted)
            Spacer()
            Text(v).font(.pro(15, .medium)).textSelection(.enabled)
        }
    }
}
