package com.example.scentguard.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.scentguard.utils.pressClickEffect
import com.example.scentguard.utils.responsiveContainer

@Composable
fun ScentGuardCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp, // Reduced for professional tool-like feel
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentPadding: Dp = 24.dp,
    onClick: (() -> Unit)? = null,
    maxWidth: Dp = 480.dp,
    borderWidth: Dp = 1.dp, // Slightly stronger border
    borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), // More visible definition
    content: @Composable ColumnScope.() -> Unit
) {
    val haptic = LocalHapticFeedback.current
    
    val baseModifier = modifier
        .responsiveContainer(maxWidth)
        .let { if (onClick != null) it.pressClickEffect() else it }

    if (onClick != null) {
        Card(
            modifier = baseModifier,
            shape = RoundedCornerShape(cornerRadius),
            colors = CardDefaults.cardColors(
                containerColor = containerColor,
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 0.dp
            ),
            border = androidx.compose.foundation.BorderStroke(
                width = borderWidth,
                color = borderColor
            ),
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(contentPadding)
            ) {
                content()
            }
        }
    } else {
        Card(
            modifier = baseModifier,
            shape = RoundedCornerShape(cornerRadius),
            colors = CardDefaults.cardColors(
                containerColor = containerColor,
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 0.dp
            ),
            border = androidx.compose.foundation.BorderStroke(
                width = borderWidth,
                color = borderColor
            )
        ) {
            Column(
                modifier = Modifier.padding(contentPadding)
            ) {
                content()
            }
        }
    }
}
