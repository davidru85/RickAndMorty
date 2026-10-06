import SwiftUI

/// The variants a preview covers (`GUIDELINES.md` §6.5, `DEC-025`, `TASK-140`): the default state, the
/// largest Dynamic Type size, Reduce Transparency, and the material path an iOS 18–25 system draws instead
/// of Liquid Glass (`GUIDELINES.md` §6.2).
enum PreviewVariant {
    case standard
    case largestDynamicType
    case reduceTransparency
    case materialFallback
}

extension View {
    /// This view in [variant], in the app's single dark appearance (`UI_SPEC.md` §9). It changes only the
    /// environment a preview renders in, so the view under preview is the one the app ships.
    func previewVariant(_ variant: PreviewVariant = .standard) -> some View {
        Group {
            switch variant {
            case .standard:
                self
            case .largestDynamicType:
                dynamicTypeSize(.accessibility5)
            case .reduceTransparency:
                transformEnvironment(\._accessibilityReduceTransparency) { $0 = true }
            case .materialFallback:
                environment(\.multiverseGlassPath, .material)
            }
        }
        .preferredColorScheme(.dark)
    }
}
