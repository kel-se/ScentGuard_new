package com.example.scentguard.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object AuthUIConfig {
    // --- Colors ---
    val BackgroundColor = Color(0xFFE8F5E9) // SoftMint solid background
    val HeadlineTextColor = Color(0xFF1C1C1E)
    
    val CardBackground = Color.White
    val FieldContainerColor = Color(0xFFF2F2F7).copy(alpha = 0.3f)
    
    // --- Spacing & Sizing ---
    val BrandTopPadding = 32.dp
    val TaglineLetterSpacing = 1.sp // Reduced for better readability
    
    val CardCornerRadius = 24.dp // Reduced for professional feel
    val CardElevation = 4.dp // Subtle shadow
    val CardInternalPadding = 28.dp
    
    val FieldCornerRadius = 12.dp // Reduced rounding
    val FieldHeight = 56.dp
    val SmallFieldHeight = 52.dp // Used for Sign-Up to save space
    
    val ButtonHeight = 56.dp
    val SpacerMedium = 16.dp
    val SpacerSmall = 8.dp
    
    // --- Responsive Scaling Constants ---
    const val LottieHeightRatio = 0.25f // 25% of screen height
    const val CompactLottieHeightRatio = 0.15f // For screens with many fields
}
