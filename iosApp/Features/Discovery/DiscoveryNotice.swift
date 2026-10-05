import MultiverseExplorer

/// The non-blocking notice over Discovery content (`UI_SPEC.md` §8, `ERROR_FLOW.md` §4, §9, `DEC-124`).
///
/// It decides what the bottom glass banner says, so the decision is testable without rendering: the
/// class-specific message when a load failed beside displayable content (`IC-018.contentFailure`),
/// the stale banner when the content came from an expired cache entry, and nothing while a refresh is
/// in flight or the content is fresh. A failure with nothing displayable is the full-surface error,
/// never a notice. The one action is always Retry, which the shared reducer turns into a re-attempt of
/// the failed load or a network revalidation of stale content.
struct DiscoveryNotice: Equatable {
    let message: String
    let actionLabel: String

    static func make(for state: CharacterListUiState, copy: LocalizedCopy = .shared) -> DiscoveryNotice? {
        guard state.loadState is LoadStateContent, !state.isRefreshing else { return nil }
        let formatters = DefaultPresentationFormatters.shared
        let retry = copy.text(for: CharacterPresentation.key(formatters.retryAction()))
        if let failure = state.contentFailure {
            let message = CharacterPresentation.message(formatters.failureMessage(failure: failure), copy: copy)
            return DiscoveryNotice(message: message, actionLabel: retry)
        }
        if state.isStale {
            let message = copy.text(for: CharacterPresentation.key(CopyKeys.shared.STATE_STALE_BANNER))
            return DiscoveryNotice(message: message, actionLabel: retry)
        }
        return nil
    }
}
