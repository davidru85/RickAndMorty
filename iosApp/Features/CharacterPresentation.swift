import MultiverseExplorer
import SwiftUI

/// The feature-boundary mapping the iOS screens share (`DESIGN.md` §3.4, `TASK-055`).
///
/// The design system takes primitives only, and the shared Kotlin contracts cross into Swift with a
/// few types whose Swift form needs one decision: a `DisplayText` is data or copy, a `CharacterStatus`
/// is one of three tones, a `CopyKey` is a key that a resource file resolves and a `FailureMessage`
/// is a key plus the values its wording substitutes. Deciding any of those twice would be the second
/// implementation `CONTRACTS.md` R2 forbids, so every screen resolves them here and no screen carries
/// its own copy of the rule.
///
/// Nothing here derives a display value: the value comes from the shared formatter or from the state,
/// and this file only chooses how Swift renders it (`GUIDELINES.md` §5.7: no user-visible literal in
/// a Swift source).
enum CharacterPresentation {
    /// The string a shared `CopyKey` carries across the boundary (`IC-017`).
    ///
    /// Kotlin's `CopyKey` is a value class, so the ObjC interop erases it to its underlying string and
    /// the header types it as `Any`; the cast is the boundary itself, and a value that is not a string
    /// would be an interop regression rather than something to paper over.
    static func key(_ value: Any) -> String {
        guard let key = value as? String else {
            preconditionFailure("a CopyKey must cross the boundary as its string value, got \(type(of: value))")
        }
        return key
    }

    /// The canonical id a shared value carries (`IC-001`), needed for the shared-element key.
    static func identifier(_ value: Any) -> String {
        guard let identifier = value as? String else {
            preconditionFailure("a CharacterId must cross the boundary as its string value, got \(type(of: value))")
        }
        return identifier
    }

    /// A card's identity in a lazy grid: its canonical id, never its position (`GAP-031`).
    ///
    /// A lazy container keeps a cell per identity. Keyed by position, the skeleton's cells 0…5 were
    /// the same identities as the first six cards, so the grid kept painting skeletons after page 1
    /// loaded, and a filter change reused a cell for a different character. The pager de-duplicates
    /// by id (`IC-014`), so the canonical id is unique within a list.
    static func gridIdentity(_ card: CharacterCardUi) -> String {
        "card-\(identifier(card.id))"
    }

    /// [display] resolved for a component that takes primitives (`IC-016`): a `Data` value as it is,
    /// a `Copy` key from the app's own resource files.
    static func text(_ display: any DisplayText) -> String {
        switch display {
        case let data as DisplayTextData:
            return data.value
        case let copy as DisplayTextCopy:
            return LocalizedCopy.shared.text(for: key(copy.key))
        default:
            preconditionFailure("unsupported DisplayText case \(type(of: display))")
        }
    }

    /// The design system's status mirror (`UI_SPEC.md` §4.1): the same mapping the Android screen
    /// applies, so an unrecognised status is never a different colour on the two platforms.
    static func tone(_ status: any CharacterStatus) -> StatusTone {
        switch status {
        case is CharacterStatusAlive: return .alive
        case is CharacterStatusDead: return .dead
        default: return .unknown
        }
    }

    /// The failure's message (`ERROR_FLOW.md` §4) resolved from the app's resource files, with the
    /// values its wording substitutes.
    ///
    /// The number of the rate-limit countdown travels as an argument of the key rather than
    /// interpolated in shared code (`ERROR_FLOW.md` §4.1, `GAP-027`), so the one formatter here reads
    /// the positional specifier from the template the resource file carries and substitutes
    /// accordingly — no platform invents a number, and a missing argument renders no message change.
    static func message(_ message: FailureMessage) -> String {
        let template = LocalizedCopy.shared.text(for: key(message.key))
        guard !message.arguments.isEmpty else { return template }
        let isNumeric = template.contains("%ld") || template.contains("%d")
        let values: [CVarArg] = message.arguments.map { argument -> CVarArg in
            if isNumeric {
                return Int(argument) ?? 0
            }
            return argument as NSString
        }
        return String(format: template, arguments: values)
    }

    /// The accessibility label of one card (`UI_SPEC.md` §9): the card is **one** merged element, so
    /// the name, the status label and the species are announced in one utterance. The status is
    /// always the text label, never the dot's colour alone (`REQ-UX-005`).
    static func cardLabel(name: String, statusLabel: String, species: String) -> String {
        "\(name), \(statusLabel), \(species)"
    }

    /// The stable id of one status filter segment (`UI_SPEC.md` §6.2).
    ///
    /// `StatusFilter` is a Kotlin enum whose Swift case names are lowercased, so comparing cases is
    /// what identifies a filter; the segmented control addresses its segments by a plain id, and this
    /// is the only place the two are connected. It is here rather than in the view so the mapping is
    /// testable without rendering, and so Discovery and its tests share one definition.
    static func filterIdentifier(_ status: StatusFilter) -> String {
        switch status {
        case StatusFilter.all: return "filter.all"
        case StatusFilter.alive: return "filter.alive"
        case StatusFilter.dead: return "filter.dead"
        case StatusFilter.unknown: return "filter.unknown"
        default: return "filter.unknown"
        }
    }

    /// The status filter a segment id names, or `nil` for an id this platform does not offer.
    static func statusFilter(_ identifier: String) -> StatusFilter? {
        switch identifier {
        case "filter.all": return StatusFilter.all
        case "filter.alive": return StatusFilter.alive
        case "filter.dead": return StatusFilter.dead
        case "filter.unknown": return StatusFilter.unknown
        default: return nil
        }
    }

    /// The detail subtitle: `Species · Origin`, joined from parts the state already carries.
    ///
    /// The screen joins the parts it is given rather than deriving any of them: the origin row's
    /// value is the shared derivation, and a part that is absent is left out. `nil` means "no
    /// subtitle", never an empty line (`IC-019`, `UI_SPEC.md` §6.3).
    static func subtitle(species: String, info: [InfoRowUi]) -> String? {
        var parts: [String] = [species]
        if let origin = info.first(where: { $0.kind == InfoRowKind.origin })?.value {
            parts.append(origin)
        }
        let joined = parts.filter { !$0.isEmpty }.joined(separator: " · ")
        return joined.isEmpty ? nil : joined
    }

    /// The SF Symbol `UI_SPEC.md` §6.3 fixes for an info row: `globe.americas.fill`,
    /// `mappin.and.ellipse`, `play.tv.fill`.
    static func infoSymbol(for kind: InfoRowKind) -> String {
        switch kind {
        case InfoRowKind.origin: return "globe.americas.fill"
        case InfoRowKind.lastknownlocation: return "mappin.and.ellipse"
        default: return "play.tv.fill"
        }
    }

    /// The `UI_SPEC.md` §5.1 placeholder portrait for a card whose image has not loaded: the
    /// `Surface Container High` box with the `Glass/Fill` token, never a broken-image glyph.
    ///
    /// `GlassCharacterCard` takes a concrete `Image`, so the placeholder is drawn as one; the bytes
    /// themselves stay in the image pipeline (`REQ-FUNC-021`, `TASK-058`), and this is paint only.
    static func placeholderPortrait() -> Image {
        let size = CGSize(
            width: MultiverseDimensions.glassCardWidth,
            height: MultiverseDimensions.glassCardHeight
        )
        let rect = CGRect(origin: .zero, size: size)
        return Image(size: size) { context in
            context.fill(Path(rect), with: .color(MultiverseColors.surfaceContainerHigh))
            context.fill(Path(rect), with: .color(MultiverseGlassColors.fill))
        }
    }
}

/// One tappable character card, rendered through the one image seam (`TASK-058`, `UI_SPEC.md` §5.1).
///
/// Discovery's grid and the Favorites grid both display a favourite through the same `IC-016`
/// contract (`UI_SPEC.md` §6.4), so they share this one cell rather than each re-implementing the
/// cached-image-then-load sequence — a second implementation is what `CONTRACTS.md` R2 forbids. The
/// cell lives here, beside the mapping it uses, so neither feature depends on the other (`ADR-0001`).
///
/// `GlassCharacterCard` takes a concrete `Image`, so the cell resolves the portrait itself: the
/// synchronous memory hit paints immediately, otherwise the async load fills it in, and the
/// `Surface Container High` placeholder stands in until then — never a broken-image glyph. The cache
/// key is the card's `imageUrl` verbatim, which is what the pipeline keys on, so a second render of
/// the same URL is a memory hit and issues no request.
///
/// The card is **one** merged accessibility element announcing "name, status, species, button"
/// (`UI_SPEC.md` §9); the portrait is decorative inside it and the status is the text label, never
/// the dot's colour alone (`REQ-UX-005`). The `identifierPrefix` scopes the accessibility identifier
/// to the surface that hosts the card, so a UI test can tell Discovery's grid from Favorites'.
struct CharacterCardCell: View {
    let card: CharacterCardUi
    let loader: (any PortraitImageLoading)?
    let identifierPrefix: String
    let action: () -> Void

    @State private var portrait: Image?

    init(
        card: CharacterCardUi,
        loader: (any PortraitImageLoading)? = nil,
        identifierPrefix: String = "character.card",
        action: @escaping () -> Void
    ) {
        self.card = card
        self.loader = loader
        self.identifierPrefix = identifierPrefix
        self.action = action
    }

    private var resolvedLoader: any PortraitImageLoading {
        loader ?? PortraitImagePipeline.shared
    }

    var body: some View {
        Button(action: action) {
            GlassCharacterCard(
                name: card.name,
                species: species,
                statusTone: CharacterPresentation.tone(card.status),
                statusLabel: statusLabel,
                portrait: portrait ?? CharacterPresentation.placeholderPortrait()
            )
        }
        .buttonStyle(.plain)
        .task(id: card.imageUrl) {
            if let cached = resolvedLoader.cachedImage(for: card.imageUrl) {
                portrait = cached
                return
            }
            portrait = await resolvedLoader.image(for: card.imageUrl)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(
            Text(CharacterPresentation.cardLabel(name: card.name, statusLabel: statusLabel, species: species))
        )
        .accessibilityAddTraits(.isButton)
        .accessibilityIdentifier("\(identifierPrefix).\(CharacterPresentation.identifier(card.id))")
    }

    private var statusLabel: String {
        LocalizedCopy.shared.text(for: CharacterPresentation.key(card.statusLabel))
    }

    private var species: String {
        CharacterPresentation.text(card.species)
    }
}

extension CharacterCardUi {
    /// The grid identity of `CharacterPresentation.gridIdentity(_:)`, as a key path `ForEach` can read.
    var gridIdentity: String { CharacterPresentation.gridIdentity(self) }
}
