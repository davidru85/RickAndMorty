package io.github.davidru85.multiverse.feature.characterdetail.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.davidru85.multiverse.core.designsystem.image.ImageSeam
import io.github.davidru85.multiverse.core.domain.model.CharacterId
import io.github.davidru85.multiverse.core.presentation.CharacterCardUi
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The Character detail destination's route (`TASK-002`, `UI_SPEC.md` §6.3).
 *
 * [header] is the card the hand-off carried (`IC-025`): it is rendered before the network responds, so
 * the hero animates from the card's bounds and the known fields are on screen immediately
 * (`AC-REQ-FUNC-002-1`). A deep link or a process restart passes `null`, and the screen renders from
 * its own load state.
 */
@Composable
public fun CharacterDetailRoute(
    id: CharacterId,
    seam: ImageSeam,
    header: CharacterCardUi?,
    onBack: () -> Unit,
    /** Shares the character on screen (`DEC-125`); the shell owns the platform share sheet. */
    onShare: (CharacterCardUi) -> Unit,
    modifier: Modifier = Modifier,
    portalMark: androidx.compose.ui.graphics.painter.Painter? = null,
    viewModel: CharacterDetailViewModel =
        koinViewModel(parameters = { parametersOf(id, header) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CharacterDetailScreen(
        state = state,
        seam = seam,
        onIntent = viewModel::onIntent,
        onBack = onBack,
        onShare = onShare,
        modifier = modifier,
        portalMark = portalMark,
    )
}
