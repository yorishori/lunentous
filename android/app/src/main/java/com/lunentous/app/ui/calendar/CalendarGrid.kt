package com.lunentous.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lunentous.app.ui.theme.LunentousExtendedTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

private val WEEKDAYS = listOf("S", "M", "T", "W", "T", "F", "S")

/**
 * Mobile-adapted from web/src/components/CalendarGrid.tsx: day cells are
 * too small for text labels on a phone, so markers become small colored
 * dots (solid = due, hollow-ring = projected, ring = logged) plus a phase
 * color bar along the bottom -- tapping a day surfaces the detail panel
 * with the full text instead.
 */
@Composable
fun CalendarGrid(
    year: Int,
    month: Int,
    markersByDate: Map<String, List<CalendarMarker>>,
    phaseBandsByDate: Map<String, List<PhaseBand>>,
    selectedDay: String?,
    onDayClick: (String) -> Unit,
) {
    val colors = LunentousExtendedTheme.colors
    val yearMonth = YearMonth.of(year, month)
    val daysInMonth = yearMonth.lengthOfMonth()
    val firstDayOfWeek = yearMonth.atDay(1).dayOfWeek.let { if (it == DayOfWeek.SUNDAY) 0 else it.value }
    val todayIso = LocalDate.now().toString()

    val cells = buildList {
        repeat(firstDayOfWeek) { add(null) }
        for (d in 1..daysInMonth) add(d)
        while (size % 7 != 0) add(null)
    }

    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            WEEKDAYS.forEach { w ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(w, style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                }
            }
        }
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(modifier = Modifier.weight(1f).padding(2.dp)) {
                        if (day == null) {
                            Box(Modifier.aspectRatio(1f))
                        } else {
                            val iso = LocalDate.of(year, month, day).toString()
                            DayCell(
                                day = day,
                                markers = markersByDate[iso].orEmpty(),
                                bands = phaseBandsByDate[iso].orEmpty(),
                                isToday = iso == todayIso,
                                isSelected = iso == selectedDay,
                                onClick = { onDayClick(iso) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    markers: List<CalendarMarker>,
    bands: List<PhaseBand>,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LunentousExtendedTheme.colors
    val background = if (isSelected) colors.accentSoft else colors.surface

    Column(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(if (isToday) 1.5.dp else 0.dp, if (isToday) colors.accent else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Text(
            day.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = if (isToday) colors.accent else colors.text,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(1.dp), modifier = Modifier.padding(top = 2.dp)) {
            markers.take(4).forEach { marker -> MarkerDot(marker, colors.accent) }
        }
        if (bands.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .size(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(parseColorOrDefault(bands.first().color, colors.accent)),
            )
        }
    }
}

/** Solid = due, ring = logged, dashed-look ring (thinner border) =
 * projected -- mirrors CalendarGrid.tsx's solid/bordered/dashed marker
 * styles, compacted from a text pill down to a dot for phone-sized cells. */
@Composable
private fun MarkerDot(marker: CalendarMarker, defaultColor: Color) {
    val color = parseColorOrDefault(marker.color, defaultColor)
    when (marker.kind) {
        MarkerKind.DUE -> Box(Modifier.size(5.dp).clip(RoundedCornerShape(50)).background(color))
        MarkerKind.LOGGED -> Box(
            Modifier
                .size(5.dp)
                .clip(RoundedCornerShape(50))
                .background(color.copy(alpha = 0.35f))
                .border(1.dp, color, RoundedCornerShape(50)),
        )
        MarkerKind.PROJECTED -> Box(
            Modifier
                .size(5.dp)
                .clip(RoundedCornerShape(50))
                .border(1.dp, color, RoundedCornerShape(50)),
        )
    }
}

private fun parseColorOrDefault(hex: String?, default: Color): Color =
    hex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() } ?: default
