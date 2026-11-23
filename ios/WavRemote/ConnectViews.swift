import SwiftUI

/// Concentric rings with a slowly radiating pulse around the router.
struct SignalRings: View {
    var color: Color = Pro.green
    @State private var pulse = false

    var body: some View {
        ZStack {
            ForEach([0.3, 0.62, 0.94], id: \.self) { f in
                Circle().stroke(Pro.border, lineWidth: 1.2).scaleEffect(f)
            }
            Circle().stroke(color.opacity(pulse ? 0 : 0.5), lineWidth: 2)
                .scaleEffect(pulse ? 1 : 0.22)
            Circle().fill(color).frame(width: 64, height: 64)
            Image(systemName: "wifi.router.fill").font(.system(size: 26, weight: .semibold)).foregroundStyle(.white)
        }
        .frame(width: 240, height: 240)
        .onAppear {
            withAnimation(.linear(duration: 3.2).repeatForever(autoreverses: false)) { pulse = true }
        }
    }
}

struct StartingView: View {
    @EnvironmentObject var model: AppModel
    var body: some View {
        VStack(spacing: 10) {
            Spacer()
            SignalRings()
            Text("Connecting to your router").font(.pro(24, .semibold)).padding(.top, 8)
            Text(model.lastHost ?? "").font(.pro(16)).foregroundStyle(Pro.muted)
            Spacer()
            Button("Find a different router") { model.findAnother() }
                .font(.pro(15, .semibold)).padding(.bottom, 24)
        }
        .padding(.horizontal, 24)
    }
}

struct ConnectView: View {
    @EnvironmentObject var model: AppModel

    var body: some View {
        let found = model.scanResults.filter { $0.state == .wavlink }
        let others = model.scanResults.filter { $0.state != .wavlink }
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                SignalRings(color: found.isEmpty && !model.scanning ? Pro.red : Pro.green)
                    .frame(maxWidth: .infinity)
                Text(model.scanning ? "Looking for your router" : found.isEmpty ? "No Wavlink found" : "Found your Wavlink")
                    .font(.pro(28, .semibold))
                Text(found.isEmpty && !model.scanning
                     ? "Join your mesh's Wi-Fi, or turn on your VPN if you're away, then search again."
                     : "Tap your router to sign in.")
                    .font(.pro(16)).foregroundStyle(Pro.muted)

                if model.scanning {
                    HStack(spacing: 12) { ProgressView(); Text("Checking the usual addresses…").foregroundStyle(Pro.muted) }
                        .font(.pro(15))
                }

                if !found.isEmpty {
                    SectionLabel("Your router")
                    VStack(spacing: 0) {
                        ForEach(found) { r in
                            Button { model.open(host: r.host) } label: {
                                HStack(spacing: 14) {
                                    IconBadge(symbol: "wifi.router")
                                    VStack(alignment: .leading) {
                                        Text(r.host).font(.pro(16, .semibold))
                                        Text(r.detail).font(.pro(13)).foregroundStyle(Pro.muted)
                                    }
                                    Spacer()
                                    Image(systemName: "chevron.right").foregroundStyle(Pro.muted)
                                }
                                .padding(.vertical, 6)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .panel(padding: 12)
                }

                if !others.isEmpty && !model.scanning {
                    SectionLabel("Other addresses checked")
                    VStack(alignment: .leading, spacing: 10) {
                        ForEach(others) { r in
                            HStack {
                                Text(r.host).font(.pro(15, .medium))
                                Spacer()
                                Text(r.detail).font(.pro(13)).foregroundStyle(Pro.muted)
                            }
                        }
                    }
                    .panel()
                }

                SectionLabel("Away from home?")
                TailscaleCard()

                SectionLabel("Know the address?")
                HStack(alignment: .bottom, spacing: 8) {
                    ProField(label: "Router address", text: $model.customHost, keyboard: .URL)
                    Button(model.scanning ? "Searching" : "Search") { model.scan() }
                        .buttonStyle(PrimaryButtonStyle())
                        .frame(width: 110)
                        .disabled(model.scanning)
                }
            }
            .padding(16)
        }
    }
}

struct LoginView: View {
    @EnvironmentObject var model: AppModel
    let host: String
    @State private var password = ""
    @State private var stay = true
    @State private var showWeb = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Button("Find a different router") { model.findAnother() }
                    .font(.pro(15, .semibold))
                IconBadge(symbol: "key.fill", size: 56)
                Text("Sign in to your router").font(.pro(28, .semibold))
                Text("Use the admin password for the Wavlink at \(host). If it's never been changed, it's \"admin\".")
                    .font(.pro(16)).foregroundStyle(Pro.muted)

                if model.hasSavedLogin(host) {
                    Button(model.loginBusy ? "Connecting…" : "Reconnect with the saved sign-in") { model.open(host: host) }
                        .buttonStyle(SecondaryButtonStyle())
                        .disabled(model.loginBusy)
                }

                ProField(label: "Admin password", text: $password, secure: true, error: model.loginError)
                Toggle("Stay signed in on this phone", isOn: $stay).font(.pro(15)).tint(Pro.accent)

                Button {
                    model.login(host: host, password: password, remember: stay)
                } label: {
                    if model.loginBusy { ProgressView().tint(.white) } else { Text("Sign in") }
                }
                .buttonStyle(PrimaryButtonStyle())
                .disabled(password.isEmpty || model.loginBusy)

                Button("Open the router's web page instead") { showWeb = true }
                    .font(.pro(15, .semibold))

                SectionLabel("Away from home?")
                TailscaleCard()
            }
            .padding(20)
        }
        .sheet(isPresented: $showWeb) { RouterWebPage(host: host) }
    }
}
