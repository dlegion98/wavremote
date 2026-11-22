import SwiftUI
import CoreImage.CIFilterBuiltins

/// Standard Wi-Fi join QR code (WIFI:T:WPA;S:name;P:password;;).
func wifiQR(ssid: String, password: String, open: Bool) -> UIImage? {
    func esc(_ s: String) -> String {
        var o = ""
        for c in s { if "\;,:\"".contains(c) { o.append("\\") }; o.append(c) }
        return o
    }
    let text = open ? "WIFI:T:nopass;S:\(esc(ssid));;" : "WIFI:T:WPA;S:\(esc(ssid));P:\(esc(password));;"
    let f = CIFilter.qrCodeGenerator()
    f.message = Data(text.utf8)
    f.correctionLevel = "M"
    guard let out = f.outputImage?.transformed(by: CGAffineTransform(scaleX: 10, y: 10)),
          let cg = CIContext().createCGImage(out, from: out.extent) else { return nil }
    return UIImage(cgImage: cg)
}

struct WifiView: View {
    @EnvironmentObject var model: AppModel
    @State private var editing = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if let w = model.wifi {
                        NetworkCard(band: "5 GHz", ssid: w.ssid5, password: w.password5, open: w.open5, on: w.on5, channel: w.channel5)
                        NetworkCard(band: "2.4 GHz", ssid: w.ssid2, password: w.password2, open: w.open2, on: w.on2, channel: w.channel2)
                        Button("Change names and passwords") { editing = true }
                            .buttonStyle(PrimaryButtonStyle())
                            .disabled(model.busy)
                    } else {
                        ProgressView().frame(maxWidth: .infinity).padding(.top, 40)
                    }
                }
                .padding(16)
            }
            .refreshable { await model.refreshWifi() }
            .background(Pro.bg)
            .navigationTitle("Wi-Fi")
            .sheet(isPresented: $editing) {
                if let w = model.wifi { WifiEditor(config: w).environmentObject(model) }
            }
        }
    }
}

struct NetworkCard: View {
    let band: String
    let ssid: String
    let password: String
    let open: Bool
    let on: Bool
    let channel: String
    @State private var reveal = false
    @State private var showQR = false

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                IconBadge(symbol: "wifi", tint: on ? Pro.accent : Pro.muted)
                VStack(alignment: .leading, spacing: 2) {
                    Text(ssid.isEmpty ? "Unnamed" : ssid).font(.pro(18, .semibold)).lineLimit(1)
                    Text("\(band), " + (on ? (channel.isEmpty || channel == "0" ? "auto channel" : "channel \(channel)") : "turned off"))
                        .font(.pro(13)).foregroundStyle(Pro.muted)
                }
                Spacer()
                TagView(text: on ? "On" : "Off", color: on ? Pro.green : Pro.muted)
            }
            HStack {
                Text(open ? "No password" : reveal ? password : String(repeating: "•", count: min(12, max(8, password.count))))
                    .font(.pro(16, .medium)).textSelection(.enabled)
                Spacer()
                if !open {
                    Button(reveal ? "Hide" : "Show") { reveal.toggle() }.font(.pro(14, .semibold))
                    Button { UIPasteboard.general.string = password } label: { Image(systemName: "doc.on.doc") }
                        .padding(.leading, 8)
                }
                Button { showQR = true } label: { Image(systemName: "qrcode") }.padding(.leading, 8)
            }
            .padding(12)
            .background(Color(hex: 0x0A0D11))
            .clipShape(RoundedRectangle(cornerRadius: 6))
        }
        .panel()
        .sheet(isPresented: $showQR) {
            VStack(spacing: 16) {
                Text("Join \(ssid)").font(.pro(22, .semibold))
                Text("Point a phone's camera at this code.").font(.pro(15)).foregroundStyle(Pro.muted)
                if let img = wifiQR(ssid: ssid, password: password, open: open) {
                    Image(uiImage: img).interpolation(.none).resizable().scaledToFit()
                        .frame(maxWidth: 260).padding(16).background(Color.white)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                }
            }
            .padding(24)
            .presentationDetents([.medium])
        }
    }
}

struct WifiEditor: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss
    let config: WifiConfig
    @State private var b2: BandEdit
    @State private var b5: BandEdit
    @State private var same: Bool
    @State private var confirm = false

    init(config: WifiConfig) {
        self.config = config
        let e2 = BandEdit(raw: config.raw2, is5: false)
        let e5 = BandEdit(raw: config.raw5, is5: true)
        _b2 = State(initialValue: e2)
        _b5 = State(initialValue: e5)
        _same = State(initialValue: e2.ssid == e5.ssid && e2.password == e5.password)
    }

    private func passError(_ b: BandEdit) -> String? { b.auth == 0 ? nil : wifiPassError(b.password) }

    private var valid: Bool {
        ssidError(b5.ssid) == nil && passError(b5) == nil &&
            (same || (ssidError(b2.ssid) == nil && passError(b2) == nil))
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Toggle("Same name and password on both bands", isOn: $same).font(.pro(15)).tint(Pro.accent).panel()
                    VStack(alignment: .leading, spacing: 12) {
                        Text(same ? "Both bands" : "5 GHz").font(.pro(16, .semibold))
                        ProField(label: "Network name", text: $b5.ssid, error: ssidError(b5.ssid))
                        if b5.auth != 0 { ProField(label: "Password", text: $b5.password, secure: true, error: passError(b5)) }
                        Toggle("Hide network name", isOn: $b5.hidden).font(.pro(15)).tint(Pro.accent)
                        Toggle("Band on", isOn: $b5.enabled).font(.pro(15)).tint(Pro.accent)
                    }
                    .panel()
                    if !same {
                        VStack(alignment: .leading, spacing: 12) {
                            Text("2.4 GHz").font(.pro(16, .semibold))
                            ProField(label: "Network name", text: $b2.ssid, error: ssidError(b2.ssid))
                            if b2.auth != 0 { ProField(label: "Password", text: $b2.password, secure: true, error: passError(b2)) }
                            Toggle("Hide network name", isOn: $b2.hidden).font(.pro(15)).tint(Pro.accent)
                            Toggle("Band on", isOn: $b2.enabled).font(.pro(15)).tint(Pro.accent)
                        }
                        .panel()
                    }
                    Text("Every device on your Wi-Fi disconnects for a moment while the router applies this. Devices will need the new password if you change it.")
                        .font(.pro(13)).foregroundStyle(Pro.muted)
                    Button("Save") { confirm = true }
                        .buttonStyle(PrimaryButtonStyle())
                        .disabled(!valid || model.busy)
                }
                .padding(16)
            }
            .background(Pro.bg)
            .navigationTitle("Wi-Fi names")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } } }
            .confirmationDialog("Save and restart Wi-Fi?", isPresented: $confirm, titleVisibility: .visible) {
                Button("Save") {
                    var two = b2
                    if same {
                        two.ssid = b5.ssid; two.password = b5.password; two.hidden = b5.hidden; two.enabled = b5.enabled
                    }
                    model.saveWifi(two, b5)
                    dismiss()
                }
            }
        }
    }
}
