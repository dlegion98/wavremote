import SwiftUI

extension Color {
    init(hex: UInt32, alpha: Double = 1) {
        self.init(.sRGB,
                  red: Double((hex >> 16) & 0xFF) / 255,
                  green: Double((hex >> 8) & 0xFF) / 255,
                  blue: Double(hex & 0xFF) / 255,
                  opacity: alpha)
    }
}

/// "Pro dark": slate panels, 1 pt borders, blue accent, green/amber/red status. Always dark.
enum Pro {
    static let bg = Color(hex: 0x0E1217)
    static let panel = Color(hex: 0x181E26)
    static let panelHigh = Color(hex: 0x202731)
    static let border = Color(hex: 0x36404D)
    static let text = Color(hex: 0xF4F6FA)
    static let muted = Color(hex: 0xB6BFCC)
    static let accent = Color(hex: 0x3D8BFF)
    static let green = Color(hex: 0x2FBF71)
    static let amber = Color(hex: 0xF2A93B)
    static let red = Color(hex: 0xF2555A)
    static let down = Color(hex: 0x3D8BFF)
    static let up = Color(hex: 0x9B7BFF)
}

extension Font {
    /// Schibsted Grotesk, bundled with the app.
    static func pro(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
        let name: String
        switch weight {
        case .heavy, .black, .bold: name = "SchibstedGrotesk-ExtraBold"
        case .semibold: name = "SchibstedGrotesk-SemiBold"
        case .medium: name = "SchibstedGrotesk-Medium"
        default: name = "SchibstedGrotesk-Regular"
        }
        return .custom(name, size: size)
    }
}

struct PanelStyle: ViewModifier {
    var padding: CGFloat
    var fill: Color
    func body(content: Content) -> some View {
        content
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(fill)
            .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 8, style: .continuous).stroke(Pro.border, lineWidth: 1))
    }
}

/// Fades in and rises a few points the first time it's shown.
struct Appear: ViewModifier {
    @State private var shown = false
    func body(content: Content) -> some View {
        content
            .opacity(shown ? 1 : 0)
            .offset(y: shown ? 0 : 10)
            .onAppear { withAnimation(.easeOut(duration: 0.3)) { shown = true } }
    }
}

extension View {
    func panel(padding: CGFloat = 16, fill: Color = Pro.panel) -> some View {
        modifier(PanelStyle(padding: padding, fill: fill)).modifier(Appear())
    }
}

struct SectionLabel: View {
    let text: String
    init(_ text: String) { self.text = text }
    var body: some View {
        Text(text).font(.pro(14, .semibold)).foregroundStyle(Pro.muted)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.top, 8).padding(.leading, 4)
    }
}

struct TagView: View {
    let text: String
    let color: Color
    var body: some View {
        Text(text).font(.pro(12, .medium)).foregroundStyle(color)
            .padding(.horizontal, 6).padding(.vertical, 2)
            .background(color.opacity(0.14))
            .clipShape(RoundedRectangle(cornerRadius: 4))
    }
}

struct IconBadge: View {
    let symbol: String
    var tint: Color = Pro.accent
    var size: CGFloat = 40
    var body: some View {
        Image(systemName: symbol)
            .font(.system(size: size * 0.45, weight: .semibold))
            .foregroundStyle(tint)
            .frame(width: size, height: size)
            .background(tint.opacity(0.14))
            .clipShape(RoundedRectangle(cornerRadius: 6))
    }
}

struct PrimaryButtonStyle: ButtonStyle {
    var fill: Color = Pro.accent
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.pro(16, .semibold))
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity, minHeight: 50)
            .background(fill.opacity(configuration.isPressed ? 0.8 : 1))
            .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

struct SecondaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.pro(15, .semibold))
            .foregroundStyle(Pro.text)
            .frame(maxWidth: .infinity, minHeight: 46)
            .background(configuration.isPressed ? Pro.panelHigh : Pro.panel)
            .clipShape(RoundedRectangle(cornerRadius: 8))
            .overlay(RoundedRectangle(cornerRadius: 8).stroke(Pro.border, lineWidth: 1))
    }
}

/// A labelled text field in the app's style.
struct ProField: View {
    let label: String
    @Binding var text: String
    var secure = false
    var error: String? = nil
    var keyboard: UIKeyboardType = .default
    @State private var reveal = false

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).font(.pro(14, .semibold)).foregroundStyle(Pro.muted)
            HStack {
                Group {
                    if secure && !reveal { SecureField("", text: $text) } else { TextField("", text: $text) }
                }
                .font(.pro(16))
                .foregroundStyle(Pro.text)
                .keyboardType(keyboard)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                if secure {
                    Button(reveal ? "Hide" : "Show") { reveal.toggle() }
                        .font(.pro(14, .semibold)).foregroundStyle(Pro.accent)
                }
            }
            .padding(.horizontal, 12).frame(minHeight: 48)
            .background(Color(hex: 0x0A0D11))
            .clipShape(RoundedRectangle(cornerRadius: 6))
            .overlay(RoundedRectangle(cornerRadius: 6).stroke(error == nil ? Pro.border : Pro.red, lineWidth: 1))
            if let error { Text(error).font(.pro(13)).foregroundStyle(Pro.red) }
        }
    }
}
