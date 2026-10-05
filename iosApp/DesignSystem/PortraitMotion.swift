import SwiftUI

/// The card-to-detail zoom transition and its Reduce Motion fallback (`UI_SPEC.md` §7,
/// `REQ-FUNC-009`, `REQ-UX-007`, `AC-REQ-FUNC-009-2`, `AC-REQ-UX-007-1`, `TASK-057`).
///
/// The iOS peer of the Android `PortraitMotion`: the same two durations, the same shared-element
/// key rule and the same fallback decision, so the platforms cannot drift. It is a separate type
/// rather than constants inside a view so the fallback is **decidable without rendering**, which is
/// what makes it testable — the Android suite asserts the same decision on virtual time.
///
/// The zoom itself is the platform's own: `.matchedTransitionSource(id:in:)` on the card and
/// `.navigationTransition(.zoom(sourceID:in:))` on the detail destination, both driven by one
/// `@Namespace` owned by the shell. Reduce Motion replaces them with a cross-fade, and the splash's
/// rotation becomes an opacity pulse (`UI_SPEC.md` §7); the destination still changes, because the
/// motion is the decoration and not the behaviour.
public enum PortraitMotion {
    /// The zoom transition's duration (`UI_SPEC.md` §7).
    public static let transitionSeconds: Double = 0.450

    /// The cross-fade Reduce Motion substitutes (`UI_SPEC.md` §7).
    public static let reducedMotionCrossfadeSeconds: Double = 0.300

    /// The parallax factor the portrait translates at while it scrolls (`UI_SPEC.md` §7).
    public static let parallaxFactor: Double = 0.85

    /// The shared-element key of one character's portrait.
    ///
    /// It is derived from the canonical id (`IC-001`), never from a position in a list, so the same
    /// character carries the same key in the grid and in the hero — the Android rule verbatim.
    public static func sharedKey(characterID: String) -> String { "portrait-\(characterID)" }

    /// The animation a destination change runs with.
    ///
    /// With Reduce Motion on there is no zoom and no travel: the cross-fade is what remains, and it
    /// is shorter because it does not have to cover distance. Returning `nil` would leave the change
    /// unanimated rather than cross-faded, which is a different (and worse) behaviour.
    public static func transitionAnimation(reduceMotion: Bool) -> Animation {
        if reduceMotion {
            return .easeInOut(duration: reducedMotionCrossfadeSeconds)
        }
        return .easeOut(duration: transitionSeconds)
    }

    /// `true` when the platform should use the zoom transition rather than the cross-fade.
    public static func usesZoomTransition(reduceMotion: Bool) -> Bool { !reduceMotion }

    /// The id both ends of the zoom use for one character (`DEC-135`): the grid cell is
    /// `.matchedTransitionSource` under it and the Detail destination `.navigationTransition(.zoom)`.
    /// `nil` with Reduce Motion, so neither end applies the zoom and the change is the cross-fade.
    public static func zoomSourceID(characterID: String, reduceMotion: Bool) -> String? {
        usesZoomTransition(reduceMotion: reduceMotion) ? sharedKey(characterID: characterID) : nil
    }

    /// `true` when the portrait parallax is suppressed (`UI_SPEC.md` §7).
    public static func usesParallax(reduceMotion: Bool) -> Bool { !reduceMotion }

    /// `true` when the splash's portal pulse replaces its rotation (`REQ-UX-007`).
    public static func splashPulsesInsteadOfSpinning(reduceMotion: Bool) -> Bool { reduceMotion }
}

/// What the platform reports about the Reduce Motion setting (`UI_SPEC.md` §7).
///
/// It is a seam so a test can drive both branches without touching the system setting, mirroring the
/// Android `rememberReduceMotion()` reading `ANIMATOR_DURATION_SCALE`.
public protocol MotionPreference {
    var reduceMotionEnabled: Bool { get }
}

/// The real preference, read from the platform setting.
public struct SystemMotionPreference: MotionPreference {
    public init() {}

    public var reduceMotionEnabled: Bool { UIAccessibility.isReduceMotionEnabled }
}

/// The shell's zoom namespace (`DEC-135`), handed to the grids through the environment so a card cell
/// can be the transition's source without its screen taking a parameter; `nil` outside the shell.
private struct PortraitTransitionNamespaceKey: EnvironmentKey {
    static let defaultValue: Namespace.ID? = nil
}

extension EnvironmentValues {
    public var portraitTransitionNamespace: Namespace.ID? {
        get { self[PortraitTransitionNamespaceKey.self] }
        set { self[PortraitTransitionNamespaceKey.self] = newValue }
    }
}
