package com.nomad.travel.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nomad.travel.R
import com.nomad.travel.ui.theme.NomadGlow
import com.nomad.travel.ui.theme.NomadRoyal

/**
 * Animated Nomad logo for loading indicators.
 *
 * Layered effects:
 *  • A rotating conic glow ring orbits the logo.
 *  • A breathing halo pulses behind it.
 *  • The logo itself gently scales and tilts in sync with the breath.
 */
@Composable
fun NomadLogoSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    showHalo: Boolean = true
) {
    val transition = rememberInfiniteTransition(label = "nomadLogoSpinner")

    val ringRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ringRotation"
    )

    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )

    val tilt by transition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tilt"
    )

    val logoScale = 0.9f + breath * 0.12f
    val haloAlpha = 0.18f + breath * 0.32f

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        if (showHalo) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = this.size.minDimension / 2f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            NomadGlow.copy(alpha = haloAlpha),
                            NomadRoyal.copy(alpha = haloAlpha * 0.4f),
                            Color.Transparent
                        ),
                        center = Offset(this.size.width / 2f, this.size.height / 2f),
                        radius = radius
                    ),
                    radius = radius
                )
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = ringRotation }
            ) {
                val radius = this.size.minDimension / 2f * 0.92f
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            NomadRoyal.copy(alpha = 0.0f),
                            NomadGlow.copy(alpha = 0.65f),
                            NomadRoyal.copy(alpha = 0.85f),
                            Color.Transparent
                        ),
                        center = Offset(this.size.width / 2f, this.size.height / 2f)
                    ),
                    radius = radius,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = (size.toPx() * 0.045f).coerceAtLeast(2f)
                    )
                )
            }
        }

        Image(
            painter = painterResource(R.drawable.ic_launcher_logo),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = logoScale
                    scaleY = logoScale
                    rotationZ = tilt
                }
        )
    }
}
