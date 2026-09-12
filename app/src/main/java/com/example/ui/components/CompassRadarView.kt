package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun CompassRadarView(
    bearingDegrees: Float,
    deviceAzimuth: Float,
    isTrackingActive: Boolean,
    modifier: Modifier = Modifier
) {
    // Relative angle: how much to rotate the needle relative to user's heading
    val animatedTargetAngle by animateFloatAsState(
        targetValue = (bearingDegrees - deviceAzimuth + 360f) % 360f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "compassRotation"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "radarPulse")
    val pulseRadiusFraction by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseRadius"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    val radarSweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarSweep"
    )

    Box(
        modifier = modifier
            .size(260.dp)
            .testTag("compass_radar_view"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val maxRadius = min(size.width, size.height) / 2f * 0.92f

            // Radar background gradient
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF0F2B48), Color(0xFF071426)),
                    center = center,
                    radius = maxRadius
                ),
                radius = maxRadius,
                center = center
            )

            // Outer ring
            drawCircle(
                color = Color(0xFF00E5FF).copy(alpha = 0.35f),
                radius = maxRadius,
                center = center,
                style = Stroke(width = 2.5.dp.toPx())
            )

            // Inner concentric radar rings
            val ringFractions = listOf(0.3f, 0.55f, 0.8f)
            ringFractions.forEach { fraction ->
                drawCircle(
                    color = Color(0xFF00E5FF).copy(alpha = 0.18f),
                    radius = maxRadius * fraction,
                    center = center,
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                            floatArrayOf(10f, 10f), 0f
                        )
                    )
                )
            }

            // Crosshair lines
            drawLine(
                color = Color(0xFF00E5FF).copy(alpha = 0.20f),
                start = Offset(center.x - maxRadius, center.y),
                end = Offset(center.x + maxRadius, center.y),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = Color(0xFF00E5FF).copy(alpha = 0.20f),
                start = Offset(center.x, center.y - maxRadius),
                end = Offset(center.x, center.y + maxRadius),
                strokeWidth = 1.dp.toPx()
            )

            // Animated live radar pulse ring if tracking
            if (isTrackingActive) {
                drawCircle(
                    color = Color(0xFF00E5FF).copy(alpha = pulseAlpha),
                    radius = maxRadius * pulseRadiusFraction,
                    center = center,
                    style = Stroke(width = 1.8.dp.toPx())
                )

                // Radar scanning sweep sector
                rotate(degrees = radarSweepAngle, pivot = center) {
                    val sweepEnd = Offset(
                        center.x + maxRadius * cos(0.0).toFloat(),
                        center.y + maxRadius * sin(0.0).toFloat()
                    )
                    drawLine(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0x0000E5FF), Color(0x6600E5FF)),
                            start = center,
                            end = sweepEnd
                        ),
                        start = center,
                        end = sweepEnd,
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }

            // Compass ticks around the circumference
            for (i in 0 until 360 step 30) {
                val angleRad = Math.toRadians((i - 90).toDouble())
                val isMajor = i % 90 == 0
                val tickLength = if (isMajor) 12.dp.toPx() else 6.dp.toPx()
                val tickStartRadius = maxRadius - tickLength
                val start = Offset(
                    (center.x + tickStartRadius * cos(angleRad)).toFloat(),
                    (center.y + tickStartRadius * sin(angleRad)).toFloat()
                )
                val end = Offset(
                    (center.x + maxRadius * cos(angleRad)).toFloat(),
                    (center.y + maxRadius * sin(angleRad)).toFloat()
                )
                drawLine(
                    color = if (isMajor) Color(0xFFFFD54F) else Color(0xFF80DEEA).copy(alpha = 0.5f),
                    start = start,
                    end = end,
                    strokeWidth = if (isMajor) 2.5.dp.toPx() else 1.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Central user position marker (Live dot)
            drawCircle(
                color = Color(0xFF00E5FF).copy(alpha = 0.3f),
                radius = 16.dp.toPx(),
                center = center
            )
            drawCircle(
                color = Color(0xFF00E5FF),
                radius = 7.dp.toPx(),
                center = center
            )
            drawCircle(
                color = Color.White,
                radius = 2.5.dp.toPx(),
                center = center
            )

            // Target Direction Arrow (pointing straight towards the destination)
            rotate(degrees = animatedTargetAngle, pivot = center) {
                val arrowTipRadius = maxRadius * 0.88f
                val arrowBaseRadius = maxRadius * 0.45f
                val arrowWidth = 14.dp.toPx()

                // Arrow pointing up (towards 0 deg in rotated coordinate)
                val tip = Offset(center.x, center.y - arrowTipRadius)
                val left = Offset(center.x - arrowWidth, center.y - arrowBaseRadius)
                val right = Offset(center.x + arrowWidth, center.y - arrowBaseRadius)
                val innerNotch = Offset(center.x, center.y - (arrowBaseRadius * 1.15f))

                // Arrow path
                val arrowPath = Path().apply {
                    moveTo(tip.x, tip.y)
                    lineTo(right.x, right.y)
                    lineTo(innerNotch.x, innerNotch.y)
                    lineTo(left.x, left.y)
                    close()
                }

                // Shaded arrow with luminous gradient
                drawPath(
                    path = arrowPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFFF3D00), Color(0xFFFF9100)),
                        startY = tip.y,
                        endY = left.y
                    )
                )

                // Arrow outline for crispness
                drawPath(
                    path = arrowPath,
                    color = Color.White.copy(alpha = 0.9f),
                    style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                )

                // Target Beacon Dot at the perimeter
                drawCircle(
                    color = Color(0xFFFF5252),
                    radius = 5.dp.toPx(),
                    center = tip
                )
            }
        }
    }
}
