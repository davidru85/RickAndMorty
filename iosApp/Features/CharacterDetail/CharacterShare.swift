import Foundation
import MultiverseExplorer

/// The line the Detail's Share sends (`DEC-125`, `UI_SPEC.md` §6.3): the character's name and its API
/// resource URL, in the shared copy key `share_character_text`.
///
/// The URL is built by `:core:data` from the configured host, through `MultiverseBootstrap`, so no
/// Swift source names a host; the arguments are text, substituted for the template's `%1$@`/`%2$@`.
enum CharacterShare {
    static func text(for card: CharacterCardUi, copy: LocalizedCopy = .shared) -> String {
        let template = copy.text(for: CharacterPresentation.key(CopyKeys.shared.SHARE_CHARACTER_TEXT))
        let url = MultiverseBootstrap.shared.characterUrl(id: CharacterPresentation.identifier(card.id))
        let arguments: [CVarArg] = [card.name as NSString, url as NSString]
        return String(format: template, locale: Locale.current, arguments: arguments)
    }
}
