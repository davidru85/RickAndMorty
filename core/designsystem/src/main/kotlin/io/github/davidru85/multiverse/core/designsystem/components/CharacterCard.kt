package io.github.davidru85.multiverse.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.davidru85.multiverse.core.designsystem.image.ImageSeam
import io.github.davidru85.multiverse.core.designsystem.image.ImageSeamResult
import io.github.davidru85.multiverse.core.designsystem.theme.MultiverseTheme
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseColors
import io.github.davidru85.multiverse.core.designsystem.tokens.MultiverseDimensions

/** The two portrait heights a card offers (`UI_SPEC.md` §4.1): Regular 172 dp, Tall 224 dp. */
public enum class CardHeight { Regular, Tall }

/** The card's inset and corner (`UI_SPEC.md` §4.1): 6 dp and 28; the card fills its grid cell. */
private val CardInset = 6.dp
private val CardCorner = 28.dp
private val RegularPortrait = 172.dp
private val TallPortrait = 224.dp

/**
 * The character card (`UI_SPEC.md` §4.1): photo, name, status badge and species, and nothing else.
 *
 * The component takes primitives, never a domain or presentation type (`DESIGN.md` §3.4): the
 * caller resolves `DisplayText` and the copy key at the feature or shell boundary and passes the
 * finished strings. [statusTone] mirrors the domain status so no domain type crosses into the
 * design system.
 *
 * Accessibility (`UI_SPEC.md` §9, `AC-REQ-UX-005-1`): the whole card is **one node** whose description
 * is "<name>, <status>, <species>" and whose role is Button, so TalkBack names the role once and in the
 * device's language; the portrait inside it is decorative and the badge's own node is cleared, its
 * label being part of the one sentence. The status is never conveyed by colour alone (`REQ-UX-005`).
 * A tappable card is an M3 `Card(onClick)`, so its ripple follows the 28 corner.
 */
@Composable
public fun CharacterCard(
    name: String,
    species: String,
    statusTone: StatusTone,
    statusLabel: String,
    imageUrl: String,
    seam: ImageSeam,
    modifier: Modifier = Modifier,
    height: CardHeight = CardHeight.Regular,
    containerColor: Color = MultiverseColors.surfaceContainerHigh,
    portalMark: Painter? = null,
    onClick: (() -> Unit)? = null,
    /** The portrait's shared-element key for the card→Detail transition (`DEC-135`). */
    sharedKey: String? = null,
) {
    val portraitHeight = if (height == CardHeight.Regular) RegularPortrait else TallPortrait
    val description = "$name, $statusLabel, $species"
    val cardModifier =
        modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    // One sentence, one role, one action: the children's nodes are cleared, so the
                    // name and the species are not read a second time after the sentence.
                    Modifier.clearAndSetSemantics {
                        contentDescription = description
                        role = Role.Button
                        onClick {
                            onClick()
                            true
                        }
                    }
                } else {
                    Modifier.semantics(mergeDescendants = true) { contentDescription = description }
                },
            )
    val shape = RoundedCornerShape(CardCorner)
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    val elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    if (onClick != null) {
        Card(onClick = onClick, shape = shape, colors = colors, elevation = elevation, modifier = cardModifier) {
            CardContent(name, species, statusTone, statusLabel, imageUrl, seam, portalMark, sharedKey, portraitHeight)
        }
    } else {
        Card(shape = shape, colors = colors, elevation = elevation, modifier = cardModifier) {
            CardContent(name, species, statusTone, statusLabel, imageUrl, seam, portalMark, sharedKey, portraitHeight)
        }
    }
}

/** The portrait with its badge, then the name and the species (`UI_SPEC.md` §4.1). */
@Composable
private fun CardContent(
    name: String,
    species: String,
    statusTone: StatusTone,
    statusLabel: String,
    imageUrl: String,
    seam: ImageSeam,
    portalMark: Painter?,
    sharedKey: String?,
    portraitHeight: Dp,
) {
    Box(modifier = Modifier.padding(CardInset)) {
        CharacterPortrait(
            imageUrl = imageUrl,
            seam = seam,
            portalMark = portalMark,
            decodePx = PortraitMaxDecodePx,
            contentDescription = null,
            sharedKey = sharedKey,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(portraitHeight)
                    .portraitCorner(),
        )
        StatusBadge(
            tone = statusTone,
            label = statusLabel,
            modifier = Modifier.padding(10.dp).align(Alignment.TopStart),
        )
    }
    Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = MultiverseColors.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = species,
            style = MaterialTheme.typography.bodySmall,
            color = MultiverseColors.onSurface.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The skeleton card (`UI_SPEC.md` §8, "Initial loading"): the card's own dimensions with shimmering
 * blocks where the portrait, the name and the species sit.
 *
 * It carries no semantics: it is a placeholder, so a screen reader announces the loading state once
 * rather than describing six empty cards.
 */
@Composable
public fun CharacterCardSkeleton(
    modifier: Modifier = Modifier,
    height: CardHeight = CardHeight.Regular,
) {
    val portraitHeight = if (height == CardHeight.Regular) RegularPortrait else TallPortrait
    Card(
        shape = RoundedCornerShape(CardCorner),
        colors = CardDefaults.cardColors(containerColor = MultiverseColors.surfaceContainerHigh),
        modifier = modifier.fillMaxWidth().clearAndSetSemantics { },
    ) {
        Column(modifier = Modifier.padding(CardInset)) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(portraitHeight)
                        .portraitCorner()
                        .shimmer(),
            )
        }
        Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.6f)
                        .aspectRatio(12f)
                        .clip(RoundedCornerShape(MultiverseDimensions.cornerExtraSmall))
                        .shimmer(),
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.35f)
                        .aspectRatio(16f)
                        .clip(RoundedCornerShape(MultiverseDimensions.cornerExtraSmall))
                        .shimmer(),
            )
        }
    }
}

@Preview
@Composable
private fun CharacterCardPreview() {
    MultiverseTheme {
        Surface(color = MultiverseColors.surface) {
            CardPreviewRow()
        }
    }
}

@Composable
private fun CardPreviewRow() {
    Row(
        modifier = Modifier.padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CharacterCard(
            name = "Rick Sanchez",
            species = "Human",
            statusTone = StatusTone.Alive,
            statusLabel = "Alive",
            imageUrl = "https://rickandmortyapi.com/api/character/avatar/1.jpeg",
            seam = PreviewSeam,
            height = CardHeight.Regular,
        )
        CharacterCardSkeleton(height = CardHeight.Tall)
    }
}

/** A preview seam that never performs I/O: it reports the placeholder state. */
private object PreviewSeam : ImageSeam {
    @Composable
    override fun rememberPainter(
        url: String,
        widthPx: Int,
        heightPx: Int,
    ): ImageSeamResult = ImageSeamResult.Loading
}
