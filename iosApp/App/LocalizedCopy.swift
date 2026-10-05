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

    /// The value for [key], a named copy key (`DEC-144`).
    public func text(for key: CopyKey) -> String {
        text(for: key.rawValue)
    }

    /// The template for [key] with [arguments] substituted for its positional specifiers, in order
    /// (`DEC-123`): a number for `%1$ld`, a text for `%1$@`. It is the one way a template is filled, so
    /// no caller replaces a specifier by hand or guesses an argument's type from the template.
    public func text(for key: String, arguments: [CopyArgument]) -> String {
        let template = text(for: key)
        guard !arguments.isEmpty else { return template }
        let values: [CVarArg] = arguments.map { argument -> CVarArg in
            switch argument {
            case .number(let value): return value
            case .text(let value): return value as NSString
            }
        }
        return String(format: template, locale: Locale.current, arguments: values)
    }

    /// [text(for:arguments:)] for a named copy key.
    public func text(for key: CopyKey, arguments: [CopyArgument]) -> String {
        text(for: key.rawValue, arguments: arguments)
    }

    /// The plural copy [key] carries for [count] (`DEC-132`): the `Localizable.stringsdict` entry picks
    /// the quantity form for the bundle's language, and [count] is substituted as a number.
    public func plural(for key: String, count: Int) -> String {
        String.localizedStringWithFormat(NSLocalizedString(key, bundle: bundle, comment: ""), count)
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

/// One typed argument of a copy template (`DEC-123`): a number for an integer specifier, a text for
/// `%@`.
public enum CopyArgument: Sendable, Equatable {
    case number(Int64)
    case text(String)
}
