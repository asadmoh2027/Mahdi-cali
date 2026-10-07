package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.TealDark
import com.example.ui.theme.TealPrimary

@Composable
fun ShiftSelectorBar(
    selectedShift: String, // "Dhammaan", "Gelin Hore", "Gelin Danbe"
    onShiftSelected: (String) -> Unit,
    morningCount: Int? = null,
    afternoonCount: Int? = null,
    totalCount: Int? = null,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Shift-ka Dugsiga:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TealDark
                )
                Text(
                    text = when (selectedShift) {
                        "Gelin Hore" -> "☀️ Gelin Hore"
                        "Gelin Danbe" -> "🌙 Gelin Danbe"
                        else -> "🌐 Dhammaan"
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TealPrimary
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val shifts = listOf(
                    Triple("Gelin Hore", "☀️ Gelin Hore", morningCount),
                    Triple("Gelin Danbe", "🌙 Gelin Danbe", afternoonCount),
                    Triple("Dhammaan", "🌐 Dhammaan", totalCount)
                )

                shifts.forEach { (shiftKey, label, count) ->
                    val isSelected = selectedShift.equals(shiftKey, ignoreCase = true)
                    val bgColor by animateColorAsState(
                        if (isSelected) TealPrimary else MaterialTheme.colorScheme.surface,
                        label = "shiftBg"
                    )
                    val textColor by animateColorAsState(
                        if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                        label = "shiftText"
                    )

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = bgColor,
                        modifier = Modifier
                            .weight(1f)
                            .height(30.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(
                                width = if (isSelected) 1.dp else 0.5.dp,
                                color = if (isSelected) TealDark else Color.Gray.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { onShiftSelected(shiftKey) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = textColor
                            )
                            if (count != null && count >= 0) {
                                Spacer(modifier = Modifier.width(2.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) Color.White.copy(alpha = 0.25f) else TealPrimary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "$count",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else TealDark,
                                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
