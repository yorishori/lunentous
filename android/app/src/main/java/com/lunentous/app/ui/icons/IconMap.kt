package com.lunentous.app.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material.icons.filled.Yard
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Maps the lucide-react icon names stored in reminder_types/phase_types
 * (see web/src/lib/icons.ts's ICON_NAMES) to their closest Material Symbols
 * equivalent -- lucide's own SVGs aren't available in a Compose-native
 * form, so this is a curated lookup rather than a 1:1 icon set.
 */
private val ICON_MAP: Map<String, ImageVector> = mapOf(
    "Droplet" to Icons.Filled.WaterDrop,
    "Droplets" to Icons.Filled.Opacity,
    "Leaf" to Icons.Filled.LocalFlorist,
    "Sprout" to Icons.Filled.Spa,
    "Flower" to Icons.Filled.LocalFlorist,
    "Flower2" to Icons.Filled.LocalFlorist,
    "Sun" to Icons.Filled.WbSunny,
    "Sunrise" to Icons.Filled.WbTwilight,
    "Scissors" to Icons.Filled.ContentCut,
    "Shovel" to Icons.Filled.Yard,
    "Bug" to Icons.Filled.BugReport,
    "SprayCan" to Icons.Filled.Science,
    "Thermometer" to Icons.Outlined.Thermostat,
    "Wind" to Icons.Filled.Air,
    "CloudRain" to Icons.Filled.Cloud,
    "Snowflake" to Icons.Filled.AcUnit,
    "Moon" to Icons.Filled.DarkMode,
    "Recycle" to Icons.Filled.Recycling,
    "Package" to Icons.Outlined.Inventory2,
    "Bell" to Icons.Filled.NotificationsActive,
    "Heart" to Icons.Filled.Favorite,
    "Star" to Icons.Filled.Star,
    "Sparkles" to Icons.Filled.LightMode,
    "TreeDeciduous" to Icons.Filled.Forest,
    "TreePine" to Icons.Filled.Forest,
    "Bird" to Icons.Filled.Pets,
    "Worm" to Icons.Filled.Pets,
    "Beaker" to Icons.Filled.Science,
    "Pipette" to Icons.Filled.Science,
    "Fan" to Icons.Filled.Air,
    "Lightbulb" to Icons.Filled.Lightbulb,
)

val DEFAULT_ICON: ImageVector = Icons.Filled.Grain

/** Every pickable name, in the same curated order as web/src/lib/icons.ts's
 * ICON_NAMES -- the DB stores the name string, so distinct entries here
 * matter even where two names currently share a Material icon. */
val ICON_NAMES: List<String> = ICON_MAP.keys.toList()

fun iconFor(name: String?): ImageVector = name?.let { ICON_MAP[it] } ?: DEFAULT_ICON
