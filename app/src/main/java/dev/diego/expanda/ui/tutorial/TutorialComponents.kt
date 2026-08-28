package dev.diego.expanda.ui.tutorial

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

internal enum class TutorialKey { NONE, SPACE, BACKSPACE, RETURN }

@Composable
internal fun TutorialKeyboard(
    pressedKey: TutorialKey = TutorialKey.NONE,
    pressProgress: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val keyColor = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(
        modifier = modifier.fillMaxWidth(0.94f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(9) { Key(modifier = Modifier.weight(1f), color = keyColor) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(7) { Key(modifier = Modifier.weight(1f), color = keyColor) }
            InteractiveKey(
                label = "⌫",
                modifier = Modifier.weight(1.35f),
                progress = if (pressedKey == TutorialKey.BACKSPACE) pressProgress else 0f,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Key(modifier = Modifier.weight(0.7f), color = keyColor)
            InteractiveKey(
                label = "space",
                modifier = Modifier.weight(3f),
                progress = if (pressedKey == TutorialKey.SPACE) pressProgress else 0f,
            )
            InteractiveKey(
                label = "↵",
                modifier = Modifier.weight(0.9f),
                progress = if (pressedKey == TutorialKey.RETURN) pressProgress else 0f,
            )
        }
    }
}

@Composable
private fun InteractiveKey(label: String, modifier: Modifier, progress: Float) {
    val normalized = progress.coerceIn(0f, 1f)
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val pressed = MaterialTheme.colorScheme.primaryContainer
    Box(modifier.height(48.dp), contentAlignment = Alignment.BottomCenter) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(36.dp),
            shape = RoundedCornerShape(9.dp),
            color = lerp(base, pressed, normalized),
            border = BorderStroke(
                1.dp,
                lerp(MaterialTheme.colorScheme.outlineVariant, MaterialTheme.colorScheme.primary, normalized),
            ),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = lerp(
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        MaterialTheme.colorScheme.onPrimaryContainer,
                        normalized,
                    ),
                )
            }
        }
        TutorialTouch(
            progress = normalized,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
internal fun TutorialTouch(progress: Float, modifier: Modifier = Modifier) {
    val normalized = progress.coerceIn(0f, 1f)
    Box(
        modifier
            .offset(y = (-5).dp)
            .size(46.dp)
            .scale(0.72f + normalized * 0.45f)
            .alpha(normalized)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.20f))
            .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.72f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(17.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.52f),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f)),
        ) {}
    }
}

@Composable
internal fun TutorialCursor(alpha: Float, heightDp: Int) {
    Box(
        Modifier.padding(start = 2.dp).width(2.dp).height(heightDp.dp).alpha(alpha)
            .background(MaterialTheme.colorScheme.primary),
    )
}

@Composable
internal fun tutorialCursorAlpha(): Float {
    val infinite = rememberInfiniteTransition(label = "tutorial cursor")
    val alpha by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1_520
                1f at 0
                1f at 760
                0.15f at 761
                0.15f at 1_520
            },
        ),
        label = "cursor blink",
    )
    return alpha
}

internal fun tutorialPressProgress(
    elapsed: Float,
    start: Float,
    holdUntil: Float,
    end: Float,
): Float = when (elapsed) {
    in start..holdUntil -> ((elapsed - start) / (holdUntil - start).coerceAtLeast(1f)).coerceAtMost(1f)
    in holdUntil..end -> (1f - (elapsed - holdUntil) / (end - holdUntil).coerceAtLeast(1f)).coerceAtLeast(0f)
    else -> 0f
}.coerceIn(0f, 1f)

@Composable
private fun Key(modifier: Modifier, color: Color) {
    Surface(modifier = modifier.height(34.dp), shape = RoundedCornerShape(8.dp), color = color) {}
}
