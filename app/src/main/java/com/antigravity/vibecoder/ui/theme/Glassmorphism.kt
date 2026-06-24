package com.antigravity.vibecoder.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val LiquidGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFF0F172A),
        Color(0xFF312E81),
        Color(0xFF4C1D95),
        Color(0xFF831843),
        Color(0xFF0F172A)
    )
)

fun Modifier.liquidBackground(): Modifier = this.background(LiquidGradient)

fun Modifier.glassPanel(
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    alpha: Float = 0.08f,
    borderAlpha: Float = 0.15f
): Modifier = this
    .background(Color.White.copy(alpha = alpha), shape)
    .border(1.dp, Color.White.copy(alpha = borderAlpha), shape)

fun Modifier.glassButton(
    shape: RoundedCornerShape = RoundedCornerShape(24.dp)
): Modifier = this.glassPanel(shape, alpha = 0.12f, borderAlpha = 0.2f)
