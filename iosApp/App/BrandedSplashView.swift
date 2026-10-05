import SwiftUI

/// The branded splash (`UI_SPEC.md` §6.1, `REQ-FUNC-007`, `TASK-007`/`TASK-054`).
///
/// The portal's rotation **is** the loading indicator, so it is exposed as an indeterminate progress
/// indicator labelled with the canonical `splash_loading` copy — the same signal the Android splash
/// exposes, which `TEST-A11Y-001` asserts there and the recorded iOS checklist carries here
/// (`TESTING.md` §9). With Reduce Motion on, the rotation is replaced by an opacity pulse and the
/// progress signal stays (`AC-REQ-UX-007-1`).
struct BrandedSplashView: View {
    let reduceMotion: Bool

    @State private var angle: Double = 0
    @State private var pulse = false

    private var rotationAnimation: Animation? {
        reduceMotion ? nil : .linear(duration: SplashTiming.minimumSeconds).repeatForever(autoreverses: false)
    }

    private var pulseAnimation: Animation? {
        reduceMotion ? .easeInOut(duration: SplashTiming.minimumSeconds).repeatForever(autoreverses: true) : nil
    }

    var body: some View {
        ZStack {
            MultiverseBrandColors.spaceBlack.ignoresSafeArea()

            Circle()
                .strokeBorder(MultiverseBrandColors.portalGreen, lineWidth: 6)
                .frame(width: 120, height: 120)
                .rotationEffect(.degrees(angle))
                .opacity(reduceMotion ? (pulse ? 0.6 : 1.0) : 1.0)
                // One animation per path, chosen by the accessibility setting rather than by a
                // mixed-value expression: the rotation is the loading indicator, and Reduce Motion
                // replaces it with an opacity pulse while the progress signal stays.
                .animation(rotationAnimation, value: angle)
                .animation(pulseAnimation, value: pulse)

            Text(LocalizedCopy.shared.text(for: "splash_wordmark"))
                .font(.largeTitle.weight(.black))
                .foregroundStyle(MultiverseColors.onSurface)
                .offset(y: 110)
        }
        .accessibilityElement()
        .accessibilityLabel(LocalizedCopy.shared.text(for: "splash_loading"))
        .accessibilityAddTraits(.updatesFrequently)
        .onAppear {
            if reduceMotion {
                pulse = true
            } else {
                angle = 360 * 3
            }
        }
    }
}
