// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

// The transport glyphs, drawn rather than loaded.
//
// The same four shapes the video chrome draws, and deliberately a second copy
// rather than a shared one. The two libraries are independent — an application
// takes music without video and the other way round — and the only place that
// could hold one copy is core, which has no Compose in it and should not.
// Four vector paths is a smaller price than a UI toolkit in the engine.
// The three glyphs the generated Fluent table does not carry.
//
// Play, Pause, Next and Previous lived here too, hand-drawn, which is a second
// set of the same pictures that drifts from the first the moment either is
// touched. They come from FluentIcons now — the same path data the browser
// renders, generated from the web's own table.
//
// Shuffle, Repeat and Collapse are genuinely absent from it: that table is the
// VIDEO chrome's icon set and a video player has no shuffle. Their reference is
// nomercy-app-web's music player, which is what draws the web music chrome.
// Until that is read the way dom.ts was, these are the last hand-drawn glyphs
// in the trio, and web-music-fidelity-spec.md says so rather than letting them
// look finished.
public object MusicIcons {

    // The way back to the row. A chevron pointing down, because that is the
    // direction the full player goes when it closes.
    public val Collapse: ImageVector by lazy {
        glyph("Collapse") {
            moveTo(6f, 9f)
            lineTo(12f, 15f)
            lineTo(18f, 9f)
            lineTo(16.6f, 7.6f)
            lineTo(12f, 12.2f)
            lineTo(7.4f, 7.6f)
            close()
        }
    }

    // Two crossing arrows, which is the shape every client uses and therefore
    // the one a listener recognises without reading a label.
    public val Shuffle: ImageVector by lazy {
        glyph("Shuffle") {
            moveTo(4f, 7f)
            horizontalLineTo(8f)
            lineTo(18f, 17f)
            horizontalLineTo(21f)
            verticalLineTo(19f)
            horizontalLineTo(17f)
            lineTo(7f, 9f)
            horizontalLineTo(4f)
            close()
            moveTo(17f, 5f)
            horizontalLineTo(21f)
            verticalLineTo(7f)
            horizontalLineTo(18f)
            lineTo(15f, 10f)
            lineTo(13.6f, 8.6f)
            close()
            moveTo(4f, 17f)
            horizontalLineTo(7f)
            lineTo(10f, 14f)
            lineTo(11.4f, 15.4f)
            lineTo(8f, 19f)
            horizontalLineTo(4f)
            close()
        }
    }

    // A loop. The one-track variant carries a mark inside it rather than being a
    // different shape, because they are the same idea at two scopes.
    public val Repeat: ImageVector by lazy {
        glyph("Repeat") {
            moveTo(7f, 6f)
            horizontalLineTo(17f)
            verticalLineTo(3f)
            lineTo(22f, 7.5f)
            lineTo(17f, 12f)
            verticalLineTo(9f)
            horizontalLineTo(7f)
            close()
            moveTo(17f, 18f)
            horizontalLineTo(7f)
            verticalLineTo(21f)
            lineTo(2f, 16.5f)
            lineTo(7f, 12f)
            verticalLineTo(15f)
            horizontalLineTo(17f)
            close()
        }
    }

}

// One button, with the label a screen reader announces and a test finds it by.
//
// The description is required rather than optional. An unlabelled icon button is
// invisible to both, and a music player is exactly the surface somebody drives
// without looking at it.
@Composable
public fun MusicIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction: MutableInteractionSource = remember { MutableInteractionSource() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(BUTTON_SIZE)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        Image(
            painter = rememberVectorPainter(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Color.White),
            modifier = Modifier.size(GLYPH_SIZE),
        )
    }
}

private fun glyph(name: String, block: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = GLYPH_SIZE,
        defaultHeight = GLYPH_SIZE,
        viewportWidth = VIEWPORT,
        viewportHeight = VIEWPORT,
    ).apply { path(fill = SolidColor(Color.White), pathBuilder = block) }.build()

private val BUTTON_SIZE = 40.dp
private val GLYPH_SIZE = 24.dp
private const val VIEWPORT = 24f
