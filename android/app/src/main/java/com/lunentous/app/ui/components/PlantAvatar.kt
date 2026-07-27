package com.lunentous.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lunentous.app.ui.theme.LunentousExtendedTheme

/** Circular avatar with a fallback sprout icon -- mirrors PlantCard.tsx's
 * avatar/placeholder pattern. `photoUrl` is null for plants with no photo
 * yet or local-only plants created before camera capture lands (phase 6). */
@Composable
fun PlantAvatar(photoUrl: String?, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    val colors = LunentousExtendedTheme.colors
    if (photoUrl != null) {
        AsyncImage(
            model = photoUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape),
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(colors.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Spa, contentDescription = null, tint = colors.accent, modifier = Modifier.size(size * 0.45f))
        }
    }
}
