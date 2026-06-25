package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.ui.theme.HorizonSunset
import com.example.ui.theme.TwilightGlow
import kotlin.math.sin

@Composable
fun HorizonCanvasVisualizer(
    isPlaying: Boolean,
    extremePerformanceMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (extremePerformanceMode) {
        Canvas(modifier = modifier) {
            val width = size.width
            val height = size.height
            val centerX = width / 2f
            val centerY = height / 2f
            
            // Desenha apenas o círculo do sol estático e a linha de horizonte
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(TwilightGlow.copy(alpha = 0.12f), Color.Transparent),
                    center = Offset(centerX, centerY),
                    radius = width * 0.4f
                ),
                radius = width * 0.4f,
                center = Offset(centerX, centerY)
            )

            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(HorizonSunset, HorizonSunset.copy(alpha = 0.3f)),
                    start = Offset(centerX, centerY - width * 0.23f),
                    end = Offset(centerX, centerY + width * 0.23f)
                ),
                radius = width * 0.23f,
                center = Offset(centerX, centerY)
            )

            drawLine(
                color = Color.White.copy(alpha = 0.3f),
                start = Offset(width * 0.1f, centerY + 10f),
                end = Offset(width * 0.9f, centerY + 10f),
                strokeWidth = 1.5.dp.toPx()
            )
        }
        return
    }

    // Transições animadas para criar as ondas fluídas do visualizador
    val infiniteTransition = rememberInfiniteTransition(label = "visualizer_waves")
    
    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "phase_1"
    )

    val phase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "phase_2"
    )

    // Pulso do Sol Horizon
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = twinPulseSpec(isPlaying),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerX = width / 2f
        val centerY = height / 2f
        
        // 1. Desenha o brilho atmosférico de fundo (Twilight Glow)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    TwilightGlow.copy(alpha = 0.15f),
                    Color.Transparent
                ),
                center = Offset(centerX, centerY),
                radius = width * 0.45f
            ),
            radius = width * 0.45f,
            center = Offset(centerX, centerY)
        )

        // 2. Desenha o Sol do Horizonte (Horizon Sun) pulsing
        val sunRadius = width * 0.25f * (if (isPlaying) pulseScale else 1.0f)
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(HorizonSunset, HorizonSunset.copy(alpha = 0.2f)),
                start = Offset(centerX, centerY - sunRadius),
                end = Offset(centerX, centerY + sunRadius)
            ),
            radius = sunRadius,
            center = Offset(centerX, centerY)
        )

        // 3. Desenha um anel de órbita minimalista
        drawCircle(
            color = Color.White.copy(alpha = 0.08f),
            radius = width * 0.38f,
            center = Offset(centerX, centerY),
            style = Stroke(width = 1.dp.toPx())
        )

        // 4. Desenha a linha de Horizonte reflexiva
        val horizonY = centerY + 10f
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.4f),
                    Color.White,
                    Color.White.copy(alpha = 0.4f),
                    Color.Transparent
                )
            ),
            start = Offset(width * 0.05f, horizonY),
            end = Offset(width * 0.95f, horizonY),
            strokeWidth = 2.dp.toPx()
        )

        // 5. Desenha as ondas de frequência no rodapé (Dancing waves) usando Path nativo do Compose
        if (isPlaying) {
            val wavePath1 = Path().apply {
                moveTo(0f, horizonY)
                for (x in 0..width.toInt() step 5) {
                    val y1 = horizonY + sin(x * 0.02 + phase1) * 12f * sin(x * 3.14159 / width)
                    lineTo(x.toFloat(), y1.toFloat())
                }
            }

            val wavePath2 = Path().apply {
                moveTo(0f, horizonY)
                for (x in 0..width.toInt() step 5) {
                    val y2 = horizonY + sin(x * 0.035 - phase2) * 8f * sin(x * 3.14159 / width)
                    lineTo(x.toFloat(), y2.toFloat())
                }
            }

            // Desenhar ondas
            drawPath(
                path = wavePath1,
                color = HorizonSunset.copy(alpha = 0.7f),
                style = Stroke(width = 2.dp.toPx())
            )
            drawPath(
                path = wavePath2,
                color = TwilightGlow.copy(alpha = 0.5f),
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
    }
}

private fun twinPulseSpec(isPlaying: Boolean): DurationBasedAnimationSpec<Float> {
    return if (isPlaying) {
        tween(450, easing = FastOutSlowInEasing)
    } else {
        tween(3000, easing = LinearOutSlowInEasing)
    }
}

@Composable
fun SimpleBarVisualizer(
    isPlaying: Boolean,
    extremePerformanceMode: Boolean = false,
    modifier: Modifier = Modifier,
    barCount: Int = 12,
    barColor: Color = HorizonSunset
) {
    if (extremePerformanceMode) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val staticHeights = listOf(0.3f, 0.6f, 0.45f, 0.2f)
            repeat(barCount) { index ->
                val heightPercent = staticHeights[index % staticHeights.size]
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    val barWidth = size.width
                    val maxBarHeight = size.height
                    val barHeight = maxBarHeight * heightPercent
                    
                    drawRect(
                        color = barColor,
                        topLeft = Offset(0f, maxBarHeight - barHeight),
                        size = Size(barWidth, barHeight)
                    )
                }
            }
        }
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "bars")
    
    // Lista de animações para cada barra para simular frequências de áudio
    val animValues = List(barCount) { index ->
        if (isPlaying) {
            infiniteTransition.animateFloat(
                initialValue = 0.1f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 300 + (index * 70) % 300,
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                ),
                label = "bar_$index"
            )
        } else {
            remember { mutableStateOf(0.15f) }
        }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        animValues.forEach { animState ->
            val heightPercent = animState.value
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                val barWidth = size.width
                val maxBarHeight = size.height
                val barHeight = maxBarHeight * heightPercent
                
                drawRect(
                    color = barColor,
                    topLeft = Offset(0f, maxBarHeight - barHeight),
                    size = Size(barWidth, barHeight)
                )
            }
        }
    }
}
