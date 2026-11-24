package dev.callai.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import dev.callai.calls.Mode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import kotlin.math.sin

/** What Kivi is doing; each mood is a pose plus an accessory. */
enum class Mood { OFF, DUTY, SLEEP, DRIVE, BUSY, TALKING }

/** Kivi away from the tailgate: on his own patch of teal paint, so his ink outline never melts into the night ground. */
@Composable
fun KiviBadge(mood: Mood, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).background(modeColor(Mode.STRANGERS), RoundedCornerShape(size * 0.28f)).padding(size * 0.08f)) {
        Kivi(mood, Modifier.fillMaxSize())
    }
}

// Drawn on a 200×200 grid. Original artwork (see .impeccable/mocks/kivi.html).
private object Art {
    private fun p(d: String): Path = PathParser().parsePathString(d).toPath()
    val earL = p("M62 66 C40 50 10 58 9 92 C8 126 36 142 60 126 C68 110 72 84 62 66 Z")
    val innerL = p("M56 78 C40 69 24 76 23 95 C22 114 38 123 52 115 C58 104 61 89 56 78 Z")
    val earR = p("M138 66 C160 50 190 58 191 92 C192 126 164 142 140 126 C132 110 128 84 138 66 Z")
    val innerR = p("M144 78 C160 69 176 76 177 95 C178 114 162 123 148 115 C142 104 139 89 144 78 Z")
    val body = p("M68 128 C60 150 62 174 78 184 L122 184 C138 174 140 150 132 128 Z")
    val head = p("M100 42 C130 42 151 63 151 92 C151 121 129 140 100 140 C71 140 49 121 49 92 C49 63 70 42 100 42 Z")
    val trunk = p("M100 110 C100 128 96 142 104 150 C110 156 121 152 119 144")
    val trunkLine = p("M91 110 C91 130 86 146 98 157 C108 164 128 158 127 143 M109 112 C109 128 107 138 112 142")
    val smile = p("M80 118 Q84 123 88 119")
    val tuft = p("M95 44 Q97 34 101 41 Q104 33 107 43")
    val closed = p("M75 95 Q82 100 89 95 M111 95 Q118 100 125 95")
    val flat = p("M76 95 H88 M112 95 H124")
    val band = p("M56 84 C56 34 144 34 144 84")
    val boom = p("M146 98 C146 118 130 126 116 126")
    val bridge = p("M95 90 H105")
    val specBridge = p("M94 92 Q100 88 106 92")
}

private val Ink = Color(0xFF0B1C26)
private val Skin = Color(0xFFB3C7D3)
private val EarSkin = Color(0xFF9FB6C4)
private val Pink = Color(0xFFF2A29A)
private val Line = Stroke(3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun DrawScope.shape(p: Path, fill: Color) { drawPath(p, fill); drawPath(p, Ink, style = Line) }
private fun DrawScope.oval(cx: Float, cy: Float, rx: Float, ry: Float, color: Color) =
    drawOval(color, Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2))

@Composable
fun Kivi(mood: Mood, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    // Honour the system "Remove animations" setting: Kivi holds still.
    val still = remember { android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    val t = rememberInfiniteTransition(label = "kivi")
    val sleeping = mood == Mood.SLEEP
    val flap by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(if (sleeping) 2600 else 1300), RepeatMode.Reverse), label = "flap")
    val breathe by t.animateFloat(0f, 1f, infiniteRepeatable(tween(if (sleeping) 2600 else 1800), RepeatMode.Reverse), label = "breathe")
    val blink by t.animateFloat(0f, 0f, infiniteRepeatable(keyframes {
        durationMillis = 4200; 0f at 3900; 1f at 4000; 0f at 4120
    }), label = "blink")
    val talk by t.animateFloat(0f, 1f, infiniteRepeatable(tween(170), RepeatMode.Reverse), label = "talk")
    val drift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "z")
    val measurer = rememberTextMeasurer()
    val a = if (still) 0f else 1f

    Canvas(modifier.aspectRatio(1f).semantics { contentDescription = "Kivi the elephant, ${mood.name.lowercase()}" }) {
        val k = size.minDimension / 200f
        withTransform({ scale(k, k, Offset.Zero); scale(1f, 1f + a * breathe * 0.015f, Offset(100f, 190f)) }) {
            val earSwing = a * flap * (if (sleeping) 1.5f else 3f) + if (sleeping) 6f else 0f
            rotate(-earSwing, Offset(62f, 90f)) { shape(Art.earL, EarSkin); drawPath(Art.innerL, Pink) }
            rotate(earSwing, Offset(138f, 90f)) { shape(Art.earR, EarSkin); drawPath(Art.innerR, Pink) }
            shape(Art.body, Skin)
            oval(80f, 184f, 15f, 9f, Skin); drawOval(Ink, Offset(65f, 175f), Size(30f, 18f), style = Line)
            oval(120f, 184f, 15f, 9f, Skin); drawOval(Ink, Offset(105f, 175f), Size(30f, 18f), style = Line)
            shape(Art.head, Skin)
            drawPath(Art.tuft, Ink, style = Line)
            val trunkSwing = if (mood == Mood.TALKING) a * sin(talk * Math.PI.toFloat()) * 5f else a * flap * 1.5f
            rotate(trunkSwing, Offset(100f, 112f)) {
                drawPath(Art.trunk, Skin, style = Stroke(17f, cap = StrokeCap.Round))
                drawPath(Art.trunkLine, Ink, style = Line)
            }
            oval(70f, 110f, 8f, 5f, Pink.copy(alpha = .8f)); oval(130f, 110f, 8f, 5f, Pink.copy(alpha = .8f))
            if (mood == Mood.TALKING) oval(84f, 119f, 4f, 1.5f + 2.5f * talk * a + (1 - a) * 2f, Ink)
            else if (mood != Mood.SLEEP && mood != Mood.OFF) drawPath(Art.smile, Ink, style = Line)

            when (mood) {
                Mood.SLEEP -> drawPath(Art.closed, Ink, style = Line)
                Mood.OFF -> drawPath(Art.flat, Ink, style = Line)
                else -> {
                    val open = 1f - blink * 0.9f * a
                    oval(82f, 94f, 6f, 8f * open, Ink); oval(118f, 94f, 6f, 8f * open, Ink)
                    if (open > .5f) { oval(84f, 91f, 2.2f, 2.2f, Color.White); oval(120f, 91f, 2.2f, 2.2f, Color.White) }
                }
            }
            if (mood == Mood.DRIVE) {
                drawRoundRect(Ink, Offset(68f, 84f), Size(27f, 18f), androidx.compose.ui.geometry.CornerRadius(7f))
                drawRoundRect(Ink, Offset(105f, 84f), Size(27f, 18f), androidx.compose.ui.geometry.CornerRadius(7f))
                drawPath(Art.bridge, Ink, style = Stroke(4f))
                drawLine(Color.White, Offset(73f, 89f), Offset(80f, 89f), 2.5f, StrokeCap.Round)
            }
            if (mood == Mood.BUSY) {
                drawCircle(Paint.Marigold, 12f, Offset(82f, 93f), style = Stroke(3f))
                drawCircle(Paint.Marigold, 12f, Offset(118f, 93f), style = Stroke(3f))
                drawPath(Art.specBridge, Paint.Marigold, style = Stroke(3f))
            }
            if (mood in setOf(Mood.DUTY, Mood.DRIVE, Mood.BUSY, Mood.TALKING)) {
                drawPath(Art.band, Ink, style = Stroke(7f))
                for (x in listOf(48f, 138f)) {
                    drawRoundRect(Paint.Vermilion, Offset(x, 76f), Size(14f, 24f), androidx.compose.ui.geometry.CornerRadius(6f))
                    drawRoundRect(Ink, Offset(x, 76f), Size(14f, 24f), androidx.compose.ui.geometry.CornerRadius(6f), style = Stroke(3f))
                }
                drawPath(Art.boom, Ink, style = Stroke(4f, cap = StrokeCap.Round))
                if (mood == Mood.TALKING) drawCircle(Paint.Marigold.copy(alpha = .35f * a), 9f + 4f * talk * a, Offset(113f, 126f))
                drawCircle(Paint.Marigold, 6f, Offset(113f, 126f)); drawCircle(Ink, 6f, Offset(113f, 126f), style = Stroke(3f))
            }
        }
        if (sleeping) for (i in 0..1) {
            val phase = (drift * a + i * .5f) % 1f
            drawText(measurer, "z", Offset((148f + i * 18f) * k, (46f - phase * 30f - i * 8f) * k),
                TextStyle(color = Paint.Marigold.copy(alpha = if (still) 1f else 1f - phase), fontFamily = Sign, fontSize = ((22f - i * 6f) * k).toSp()))
        }
    }
}
