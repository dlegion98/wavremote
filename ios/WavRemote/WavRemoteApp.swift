import SwiftUI

@main
struct WavRemoteApp: App {
    @StateObject private var model: AppModel

    init() {
        // Start Tailscale first so it's connecting while the app looks for the router.
        TailscaleManager.shared.startIfEnabled()
        _model = StateObject(wrappedValue: AppModel())
        let nav = UINavigationBarAppearance()
        nav.configureWithOpaqueBackground()
        nav.backgroundColor = UIColor(Pro.bg)
        nav.shadowColor = .clear
        nav.titleTextAttributes = [.foregroundColor: UIColor(Pro.text)]
        nav.largeTitleTextAttributes = [.foregroundColor: UIColor(Pro.text)]
        UINavigationBar.appearance().standardAppearance = nav
        UINavigationBar.appearance().scrollEdgeAppearance = nav

        let tab = UITabBarAppearance()
        tab.configureWithOpaqueBackground()
        tab.backgroundColor = UIColor(Color(hex: 0x12171D))
        tab.shadowColor = UIColor(Pro.border)
        UITabBar.appearance().standardAppearance = tab
        UITabBar.appearance().scrollEdgeAppearance = tab
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(model)
                .preferredColorScheme(.dark)
                .tint(Pro.accent)
        }
    }
}

struct RootView: View {
    @EnvironmentObject var model: AppModel

    var body: some View {
        ZStack {
            Pro.bg.ignoresSafeArea()
            switch model.screen {
            case .starting: StartingView().transition(.opacity)
            case .connect: ConnectView().transition(.opacity)
            case .login(let host): LoginView(host: host).transition(.opacity)
            case .main: MainView().transition(.opacity.combined(with: .move(edge: .bottom)))
            }
        }
        .animation(.easeOut(duration: 0.25), value: model.screen)
        .foregroundStyle(Pro.text)
    }
}
