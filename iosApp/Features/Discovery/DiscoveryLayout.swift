import SwiftUI

/// The fixed geometry of the Discovery surface (`UI_SPEC.md` §4.1, §8).
enum DiscoveryLayout {
    /// The skeleton count of `UI_SPEC.md` §8, "Initial loading".
    static let skeletonCount = 6

    /// The count line's fade when the count leaves or returns (`UI_SPEC.md` §6.2), in seconds.
    static let countLineFade: Double = 0.15

    /// How many cards before the end a card's appearance requests the next page (`API_SPECS.md` §8): the
    /// distance Android's grid uses, so both platforms prefetch at the same point.
    static let prefetchDistance = 2

    /// One identity per skeleton, disjoint from every card's `CharacterPresentation.gridIdentity`.
    static let skeletonIdentities = (0..<skeletonCount).map { "skeleton-\($0)" }

    /// The portal logo's size on the empty search (`UI_SPEC.md` §8): the empty-state well's.
    static let emptyMarkSize: CGFloat = MultiverseDimensions.emptyStateWell

    /// The height the empty and error surfaces keep inside the scrolling content, so they sit in the
    /// screen's middle rather than collapsing against the filters.
    static let fullSurfaceMinHeight: CGFloat = 420
}
