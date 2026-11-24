package com.ofit.app.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ofit.protocol.WorkoutRating

/** The colour of an effort level: green, amber, red. */
fun levelColor(level: WorkoutRating.Level): Color = when (level) {
    WorkoutRating.Level.EASY -> Color(0xFF34D399)
    WorkoutRating.Level.MEDIUM -> Color(0xFFFBBF24)
    WorkoutRating.Level.HARD -> Color(0xFFF87171)
}

/** Two small tags for a workout card: its effort ("Medium", coloured) and its type ("Run"). */
@Composable
fun RatingBadges(rating: WorkoutRating.Rating, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        val c = levelColor(rating.level)
        Text(
            rating.level.label,
            color = c,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xCC000000)).padding(horizontal = 8.dp, vertical = 3.dp),
        )
        Text(
            rating.kind.label,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xCC000000)).padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** The effort and type filters shared by the workout lists: "Any" or one value. */
val LEVEL_FILTERS = listOf("Any effort") + WorkoutRating.Level.entries.map { it.label }
val KIND_FILTERS = listOf("Any type") + WorkoutRating.Kind.entries.map { it.label }

fun WorkoutRating.Rating?.matches(level: String, kind: String): Boolean {
    if (level == LEVEL_FILTERS[0] && kind == KIND_FILTERS[0]) return true
    if (this == null) return false
    return (level == LEVEL_FILTERS[0] || this.level.label == level) && (kind == KIND_FILTERS[0] || this.kind.label == kind)
}
