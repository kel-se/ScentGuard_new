package com.example.scentguard.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object AuthUIConfig {
    // --- Colors & Gradients ---
    val BackgroundGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFF0F9F1), // Very light mint/white
            Color(0xFFFFFFFF),  // Pure white transition
            Color(0xFFE8F5E9)   // SoftMint
        )
    )
    
    val CardBackground = Color.White
    val FieldContainerColor = Color(0xFFF2F2F7).copy(alpha = 0.3f)
    
    // --- Spacing & Sizing ---
    val BrandTopPadding = 32.dp
    val TaglineLetterSpacing = 2.sp
    
    val CardCornerRadius = 40.dp
    val CardElevation = 12.dp
    val CardInternalPadding = 28.dp
    
    val FieldCornerRadius = 20.dp
    val FieldHeight = 56.dp
    val SmallFieldHeight = 52.dp // Used for Sign-Up to save space
    
    val ButtonHeight = 56.dp
    val SpacerMedium = 16.dp
    val SpacerSmall = 8.dp
    
    // --- Responsive Scaling Constants ---
    const val LottieHeightRatio = 0.25f // 25% of screen height
    const val CompactLottieHeightRatio = 0.15f // For screens with many fields
}
