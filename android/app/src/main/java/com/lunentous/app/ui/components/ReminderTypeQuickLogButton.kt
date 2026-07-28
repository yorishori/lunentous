package com.lunentous.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The dashboard's one-tap "log this reminder" affordance: the reminder
 * type's own icon and color, sized to be an obvious tap target, with a
 * small "+" badge overlaid rather than a separate checkmark button
 * elsewhere in the row -- the icon itself *is* the button.
 */
@Composable
fun ReminderTypeQuickLogButton(
    icon: ImageVector,
    tint: Color,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    // The "+" badge must NOT live inside the same Box that's clipped to
    // CircleShape -- BottomEnd-aligns to the square bounding box's true
    // corner, which falls outside the inscribed circle, so a shared clip
    // would cut the badge off. Keeping the outer Box unclipped and only
    // circle-clipping the icon's own inner Box lets the badge sit fully
    // on top of the circle's edge instead.
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.16f))
                .clickable(onClickLabel = contentDescription, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(size * 0.5f))
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(size * 0.42f)
                .clip(CircleShape)
                .background(tint)
                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(size * 0.26f))
        }
    }
}
