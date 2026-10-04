import Foundation

/// The one place iOS resolves a canonical copy key (`IC-017`, `REQ-FUNC-013`, `REQ-UX-008`,
/// `TASK-054`).
///
/// The keys are the shared `CopyKeys` list and the values are the committed
/// `Localizable.strings` files the parity test holds identical to the Android copy set. Nothing in
/// the Swift sources carries a user-visible literal, so a missing translation surfaces as the key
/// itself rather than as an English fallback that would hide the gap.
public final class LocalizedCopy: @unchecked Sendable {
    public static let shared = LocalizedCopy()

    private let bundle: Bundle

    public init(bundle: Bundle = .main) {
        self.bundle = bundle
    }

    /// The value the app's own resources carry for [key].
    ///
    /// `NSLocalizedString` returns the key when no entry exists, which is the signal a parity gap
    /// would produce; returning the key unchanged is therefore deliberate rather than a fallback.
    public func text(for key: String) -> String {
        NSLocalizedString(key, bundle: bundle, comment: "")
    }

    /// `true` when a locale actually carries [key], for a test that must distinguish a real entry
    /// from the key echoed back.
    public func hasEntry(for key: String, in language: String) -> Bool {
        guard
            let path = bundle.path(forResource: language, ofType: "lproj"),
            let localized = Bundle(path: path)
        else {
            return false
        }
        return localized.localizedString(forKey: key, value: nil, table: nil) != key
    }
}
