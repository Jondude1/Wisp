package com.jondude1.wisp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Design tokens.
 *
 * Void is true black: the Pixel's OLED turns the background off, so the orb and text look like they
 * float, and the battery thanks you. Everything around the orb stays quiet and neutral-blue; the
 * orb's aurora colors are the only saturated thing on screen, and Ember is reserved for moments
 * that need a human decision.
 */
object W {
    val Void = Color(0xFF000000)
    val Deep = Color(0xFF0D1220)
    val Haze = Color(0xFF1A2238)
    val Bone = Color(0xFFEDEFF5)
    val Fog = Color(0xFF8A93A8)
    val Ember = Color(0xFFF2B65C)

    val Tide = Color(0xFF4FE3D0)
    val Iris = Color(0xFF7C86FF)
    val Orchid = Color(0xFFC77DFF)
    val Rose = Color(0xFFFF8FB8)
    val Aurora = listOf(Tide, Iris, Orchid, Rose, Tide)
}

object T {
    /** What the agent says. Large and light, set like a quiet line of speech rather than a chat bubble. */
    val speech = TextStyle(color = W.Bone, fontSize = 25.sp, lineHeight = 33.sp, fontWeight = FontWeight.Light, letterSpacing = (-0.3).sp)
    val greeting = TextStyle(color = W.Bone, fontSize = 40.sp, lineHeight = 46.sp, fontWeight = FontWeight.Light, letterSpacing = (-0.9).sp)
    val title = TextStyle(color = W.Bone, fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium)
    val body = TextStyle(color = W.Fog, fontSize = 15.sp, lineHeight = 22.sp)
    val bodyBone = TextStyle(color = W.Bone, fontSize = 15.sp, lineHeight = 22.sp)
    val small = TextStyle(color = W.Fog, fontSize = 13.sp, lineHeight = 18.sp)
    val button = TextStyle(color = W.Bone, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    val appName = TextStyle(color = W.Bone, fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Light, letterSpacing = (-0.4).sp)
    val metric = TextStyle(color = W.Bone, fontSize = 46.sp, lineHeight = 50.sp, fontWeight = FontWeight.Light, letterSpacing = (-1).sp)
}

@Composable
fun WispTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = W.Void,
            surface = W.Deep,
            onBackground = W.Bone,
            onSurface = W.Bone,
            primary = W.Bone,
            onPrimary = W.Void,
        ),
        content = content,
    )
}
