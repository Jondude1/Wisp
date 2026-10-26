package com.jondude1.wisp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.jondude1.wisp.agent.OrbState
import kotlin.math.sin

/**
 * The one bold thing on the home screen: an aurora halo that breathes when idle, swells with your
 * voice when listening, spins up with a counter-rotating arc while it thinks and acts, and
 * pulses softly while it speaks. Rotation and energy are integrated per frame and eased, so
 * switching state never snaps.
 */
@Composable
fun Orb(state: OrbState, level: Float, modifier: Modifier = Modifier) {
    val stateNow by rememberUpdatedState(state)
    val levelNow by rememberUpdatedState(level)

    var angle by remember { mutableFloatStateOf(0f) }
    var speed by remember { mutableFloatStateOf(14f) }
    var phase by remember { mutableFloatStateOf(0f) }
    var energy by remember { mutableFloatStateOf(0f) }
    var busy by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                    val s = stateNow
                    val targetSpeed = when (s) {
                        OrbState.Idle -> 14f
                        OrbState.Listening -> 42f
                        OrbState.Thinking -> 150f
                        OrbState.Acting -> 85f
                        OrbState.Speaking -> 30f
                    }
                    speed += (targetSpeed - speed) * (dt * 2.5f).coerceAtMost(1f)
                    angle = (angle + speed * dt) % 360f
                    phase += dt * (if (s == OrbState.Idle) 1.2f else 2.4f)

                    val targetEnergy = when (s) {
                        OrbState.Listening -> levelNow
                        OrbState.Speaking -> 0.35f + 0.25f * sin(phase * 3f)
                        else -> 0f
                    }
                    energy += (targetEnergy - energy) * (dt * 10f).coerceAtMost(1f)

                    val targetBusy = if (s == OrbState.Thinking || s == OrbState.Acting) 1f else 0f
                    busy += (targetBusy - busy) * (dt * 4f).coerceAtMost(1f)
                }
                last = now
            }
        }
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        // Soft bloom behind the ring
        Canvas(Modifier.fillMaxSize().blur(26.dp)) {
            drawHalo(angle, phase, energy, busy, alpha = 0.85f, thickBoost = 1.9f)
        }
        // Crisp ring on top
        Canvas(Modifier.fillMaxSize()) {
            drawHalo(angle, phase, energy, busy, alpha = 1f, thickBoost = 1f)
            drawThinking(angle, busy)
        }
    }
}

private fun DrawScope.haloRadius(phase: Float, energy: Float): Float {
    val base = size.minDimension / 2f
    return base * 0.56f * (1f + 0.04f * sin(phase) + energy * 0.18f)
}

private fun DrawScope.drawHalo(angle: Float, phase: Float, energy: Float, busy: Float, alpha: Float, thickBoost: Float) {
    val c = center
    val base = size.minDimension / 2f
    val r = haloRadius(phase, energy)
    val thick = base * (0.085f + 0.02f * busy + 0.03f * energy) * thickBoost
    rotate(angle, c) {
        drawCircle(
            brush = Brush.sweepGradient(W.Aurora, c),
            radius = r,
            center = c,
            alpha = alpha,
            style = Stroke(thick),
        )
    }
    // A faint inner fill gives the ring some depth without becoming a disc
    drawCircle(
        brush = Brush.radialGradient(listOf(W.Iris.copy(alpha = 0.10f), Color.Transparent), c, r),
        radius = r,
        center = c,
    )
}

private fun DrawScope.drawThinking(angle: Float, busy: Float) {
    if (busy < 0.02f) return
    val c = center
    val r = size.minDimension / 2f * 0.56f * 1.3f
    val stroke = Stroke(2.dp.toPx(), cap = StrokeCap.Round)
    val tl = Offset(c.x - r, c.y - r)
    val sz = Size(r * 2, r * 2)
    drawArc(W.Bone, -angle * 1.7f, 64f, false, tl, sz, alpha = 0.7f * busy, style = stroke)
    drawArc(W.Bone, -angle * 1.7f + 180f, 28f, false, tl, sz, alpha = 0.35f * busy, style = stroke)
}
