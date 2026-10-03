package io.github.davidru85.multiverse.feature.favorites.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.davidru85.multiverse.core.designsystem.image.ImageSeam
import io.github.davidru85.multiverse.core.presentation.DetailHandoff
import org.koin.androidx.compose.koinViewModel

/**
 * The Favorites route: the graph-resolved [FavoritesViewModel] composed into [FavoritesScreen], and the
 * card-to-detail hand-off performed before navigation (`IC-025`, `DESIGN.md` §4.2).
 *
 * It exists so the shell's `composable<Favorites>` block names one composable and no state holder: the
 * ViewModel comes from `favoritesModule` plus `coreModule` through `koinViewModel()`, and the shell
 * passes the [seam], the [handoff] it owns and the navigation callback it owns. The feature names no
 * other feature and no `NavHost` (`ADR-0001` rule 6, `S2`).
 *
 * [handoff] is published **before** [onOpenDetail] is invoked, so the Detail screen's hero has its
 * source in the first composed frame even though the navigation happens after
 * (`AC-REQ-FUNC-002-1`). Publishing is the shell's hand-off instance and not a copy: the screen reads it
 * once on entry, and the next entry clears it.
 */
@Composable
public fun FavoritesRoute(
    seam: ImageSeam,
    handoff: DetailHandoff,
    onOpenDetail: (io.github.davidru85.multiverse.core.domain.model.CharacterId) -> Unit,
    onBrowseCharacters: () -> Unit,
    illustration: Painter,
    modifier: Modifier = Modifier,
    portalMark: Painter? = null,
    viewModel: FavoritesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    FavoritesScreen(
        state = state,
        seam = seam,
        onCharacterSelected = { card ->
            handoff.publish(card)
            onOpenDetail(card.id)
        },
        onIntent = viewModel::onIntent,
        onBrowseCharacters = onBrowseCharacters,
        illustration = illustration,
        modifier = modifier,
        portalMark = portalMark,
    )
}
