package com.ofit.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * OpenFit's menus: dark glass, rounded, the profile's accent for what is selected, red for what is
 * destructive. Used for every drop-down so they all look the same.
 */
object OfitMenuColors {
    val container = Color(0xF2141419)
    val border = Color(0x2EFFFFFF)
    val text = Color(0xFFF2F2F5)
    val muted = Color(0x99FFFFFF)
    val danger = Color(0xFFF87171)
}

@Composable
fun OfitDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    /** See-through, with no shadow: for adjusting something the menu would otherwise cover. */
    seeThrough: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.widthIn(min = 240.dp).padding(vertical = 4.dp),
        offset = DpOffset(0.dp, 6.dp),
        containerColor = if (seeThrough) OfitMenuColors.container.copy(alpha = 0.12f) else OfitMenuColors.container,
        border = BorderStroke(1.dp, if (seeThrough) androidx.compose.ui.graphics.Color.Transparent else OfitMenuColors.border),
        shape = RoundedCornerShape(18.dp),
        shadowElevation = if (seeThrough) 0.dp else 16.dp,
        tonalElevation = 0.dp,
        content = content,
    )
}

/** A small caps heading inside a menu. */
@Composable
fun OfitMenuHeader(text: String) {
    Text(
        text.uppercase(),
        color = OfitMenuColors.muted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 4.dp),
    )
}

/** A thin divider inside a menu. */
@Composable
fun OfitMenuDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .height(1.dp)
            .background(OfitMenuColors.border),
    )
}

/**
 * One row: optional icon in a tinted square, label, optional detail on the right, a check when
 * [selected]. [destructive] rows are red.
 */
@Composable
fun OfitMenuItem(
    text: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    selected: Boolean = false,
    destructive: Boolean = false,
    detail: String? = null,
    enabled: Boolean = true,
) {
    val accent = MaterialTheme.colorScheme.primary
    val tint = when {
        destructive -> OfitMenuColors.danger
        selected -> accent
        else -> OfitMenuColors.text
    }
    Row(
        modifier = Modifier
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) accent.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background((if (destructive) OfitMenuColors.danger else accent).copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = if (destructive) OfitMenuColors.danger else accent, modifier = Modifier.size(18.dp))
            }
        }
        Text(
            text,
            color = if (enabled) tint else OfitMenuColors.muted,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        if (detail != null) Text(detail, color = OfitMenuColors.muted, fontSize = 13.sp)
        if (selected) {
            Spacer(Modifier.width(2.dp))
            Icon(Icons.Filled.Check, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        }
    }
}

/** A row of choices shown as segments, for picking one of a few (e.g. Gentle / Normal / Strong). */
@Composable
fun OfitMenuSegments(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x14FFFFFF))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { option ->
            val on = option == selected
            Text(
                option,
                color = if (on) Color(0xFF111111) else OfitMenuColors.text,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (on) accent else Color.Transparent)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/** Column helper so a menu's own content can be laid out without importing layout bits. */
@Composable
fun OfitMenuSection(content: @Composable ColumnScope.() -> Unit) = Column(content = content)
