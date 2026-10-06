package io.github.davidru85.multiverse.feature.discovery.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.davidru85.multiverse.core.designsystem.image.ImageSeam
import io.github.davidru85.multiverse.core.domain.model.StatusFilter
import io.github.davidru85.multiverse.core.presentation.CharacterCardUi
import org.koin.androidx.compose.koinViewModel

/**
 * The Discovery destination's route (`TASK-001`, `UI_SPEC.md` §6.2).
 *
 * It resolves its own ViewModel from the graph, so the shell composes a destination without naming a
 * use case or a store — the shape `IC-018` and `DESIGN.md` §5 give every feature screen. [onOpenDetail]
 * receives the exact card the user tapped, so the caller publishes it to the `IC-025` hand-off before
 * it navigates. [onStatusSelected] receives every tap on a status chip, the selected one included, so the
 * shell can play the selection sound (`REQ-FUNC-036`); the feature knows nothing of sound.
 */
@Composable
public fun DiscoveryRoute(
    seam: ImageSeam,
    onOpenDetail: (CharacterCardUi) -> Unit,
    modifier: Modifier = Modifier,
    onStatusSelected: (StatusFilter) -> Unit = {},
    viewModel: DiscoveryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DiscoveryScreen(
        state = state,
        onIntent = viewModel::onIntent,
        modifier = modifier,
        seam = seam,
        onOpenDetail = onOpenDetail,
        onStatusSelected = onStatusSelected,
    )
}
