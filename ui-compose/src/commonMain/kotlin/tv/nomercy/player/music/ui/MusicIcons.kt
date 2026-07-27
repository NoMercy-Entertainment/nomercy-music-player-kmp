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
public object MusicIcons {

    public val Play: ImageVector by lazy {
        glyph("Play") {
            moveTo(8f, 5f)
            lineTo(19f, 12f)
            lineTo(8f, 19f)
            close()
        }
    }

    public val Pause: ImageVector by lazy {
        glyph("Pause") {
            moveTo(6f, 5f)
            horizontalLineTo(10f)
            verticalLineTo(19f)
            horizontalLineTo(6f)
            close()
            moveTo(14f, 5f)
            horizontalLineTo(18f)
            verticalLineTo(19f)
            horizontalLineTo(14f)
            close()
        }
    }

    public val Next: ImageVector by lazy {
        glyph("Next") {
            moveTo(6f, 5f)
            lineTo(15f, 12f)
            lineTo(6f, 19f)
            close()
            moveTo(16f, 5f)
            horizontalLineTo(19f)
            verticalLineTo(19f)
            horizontalLineTo(16f)
            close()
        }
    }

    public val Previous: ImageVector by lazy {
        glyph("Previous") {
            moveTo(18f, 5f)
            lineTo(9f, 12f)
            lineTo(18f, 19f)
            close()
            moveTo(5f, 5f)
            horizontalLineTo(8f)
            verticalLineTo(19f)
            horizontalLineTo(5f)
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
