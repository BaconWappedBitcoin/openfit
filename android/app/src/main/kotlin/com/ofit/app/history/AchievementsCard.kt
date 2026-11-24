package com.ofit.app.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ofit.protocol.Achievements
import com.ofit.protocol.HistoryEntry
import com.ofit.protocol.UnitSystem

/** The monthly distance challenge and the achievement badges (P13), on History. */
@Composable
fun AchievementsCard(entries: List<HistoryEntry>, bestWeekStreak: Int, units: UnitSystem) {
    val challenge = remember(entries, units) {
        Achievements.monthly(
            entries,
            goalMeters = if (units == UnitSystem.IMPERIAL) Achievements.MONTHLY_GOAL_METERS_IMPERIAL else Achievements.MONTHLY_GOAL_METERS,
        )
    }
    val badges = remember(entries, bestWeekStreak) { Achievements.badges(entries, bestWeekStreak) }
    val accent = MaterialTheme.colorScheme.primary
    val earned = badges.count { it.earned }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("This month's challenge", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${units.distanceValueText(challenge.doneMeters)} of ${units.distanceValueText(challenge.goalMeters)} ${units.distanceUnitLong}" +
                        if (challenge.complete) " · done!" else " · ${challenge.daysLeft} days left",
                    color = Color(0xCCFFFFFF),
                )
            }
            Text("$earned of ${badges.size} badges", color = Color(0x99FFFFFF))
        }
        LinearProgressIndicator(
            progress = { challenge.progress },
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
            color = if (challenge.complete) Color(0xFF34D399) else accent,
            trackColor = Color(0x22FFFFFF),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Earned first, then the nearest to being earned.
            items(badges.sortedWith(compareByDescending<Achievements.Badge> { it.earned }.thenByDescending { it.progress })) { b ->
                Column(
                    Modifier
                        .width(128.dp)
                        .height(150.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (b.earned) accent.copy(alpha = 0.22f) else Color(0x10FFFFFF))
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(if (b.earned) Color(0xFFFFC857) else Color(0x22FFFFFF)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = if (b.earned) Color(0xFF3A2A00) else Color(0x66FFFFFF))
                    }
                    Text(b.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center, color = if (b.earned) Color.White else Color(0x99FFFFFF))
                    Text(b.detail, fontSize = 11.sp, textAlign = TextAlign.Center, color = Color(0x99FFFFFF), lineHeight = 13.sp)
                    Spacer(Modifier.weight(1f))
                    if (!b.earned) {
                        LinearProgressIndicator(
                            progress = { b.progress },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                            color = accent,
                            trackColor = Color(0x22FFFFFF),
                        )
                    }
                }
            }
        }
    }
}
