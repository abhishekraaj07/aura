package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.VioletNeon
import kotlin.math.sin

@Composable
fun VoiceWaveform(
    isListening: Boolean,
    rmsDb: Float,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "waveform")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val barCount = 13
    val normalizedRms = (rmsDb.coerceIn(0f, 15f) / 15f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        val width = size.width
        val height = size.height
        val barWidth = 6.dp.toPx()
        val spacing = (width - (barCount * barWidth)) / (barCount + 1)
        val centerY = height / 2f

        for (i in 0 until barCount) {
            val progress = i.toFloat() / barCount
            val sineWave = sin(progress * Math.PI * 2 + phase).toFloat()

            val baseHeight = if (isListening) {
                // Height modulated by RMS volume dB and sine animation
                val dynamicScale = 0.3f + 0.7f * normalizedRms
                val waveHeight = (height * 0.75f) * dynamicScale * (0.4f + 0.6f * kotlin.math.abs(sineWave))
                waveHeight.coerceAtLeast(8.dp.toPx())
            } else {
                6.dp.toPx()
            }

            val x = spacing + i * (barWidth + spacing)
            val top = centerY - (baseHeight / 2f)

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = if (isListening) {
                        listOf(CyanNeon, VioletNeon)
                    } else {
                        listOf(Color.Gray.copy(alpha = 0.4f), Color.Gray.copy(alpha = 0.2f))
                    }
                ),
                topLeft = Offset(x, top),
                size = Size(barWidth, baseHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}
