package dev.diego.expanda.ui.tutorial

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.diego.expanda.R
import dev.diego.expanda.ui.ProjectLinks

private const val ACTIONS_LOOP = 28_000
private const val ESPANSO_LOOP = 10_800
private const val SOURCE_LOOP = 14_800
private const val OPEN_SOURCE_LOOP = 9_600

private enum class ActionDemoStage { MATH, UPPERCASE, UNDERSCORE, SELECTED_MENU, SELECTION_RESULT }

@Composable
internal fun ActionsAnimation() {
    val elapsed = advancedTimeline(ACTIONS_LOOP)
    val stage = when {
        elapsed < 5_600f -> ActionDemoStage.MATH
        elapsed < 11_200f -> ActionDemoStage.UPPERCASE
        elapsed < 16_800f -> ActionDemoStage.UNDERSCORE
        elapsed < 21_500f -> ActionDemoStage.SELECTED_MENU
        else -> ActionDemoStage.SELECTION_RESULT
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FeatureHeading(Icons.Default.Calculate, "Transform text instantly", "Use shortcuts or Android’s selection menu.")
        AnimatedContent(
            targetState = stage,
            transitionSpec = {
                (fadeIn(tween(360)) + slideInHorizontally(tween(420)) { it / 6 })
                    .togetherWith(fadeOut(tween(240)) + slideOutHorizontally(tween(280)) { -it / 6 })
            },
            label = "actions tutorial stage",
        ) { current ->
            when (current) {
                ActionDemoStage.MATH -> ActionShortcutPreview(
                    title = "Calculate expression",
                    before = "12 * 8",
                    shortcut = "==",
                    after = "96",
                    localElapsed = elapsed,
                )
                ActionDemoStage.UPPERCASE -> ActionShortcutPreview(
                    title = "Uppercase",
                    before = "hello world",
                    shortcut = ",uu",
                    after = "HELLO WORLD",
                    localElapsed = elapsed - 5_600f,
                )
                ActionDemoStage.UNDERSCORE -> ActionShortcutPreview(
                    title = "Spaces to underscores",
                    before = "hello world",
                    shortcut = ",su",
                    after = "hello_world",
                    localElapsed = elapsed - 11_200f,
                )
                ActionDemoStage.SELECTED_MENU -> SelectionActionPreview(elapsed - 16_800f, showResult = false)
                ActionDemoStage.SELECTION_RESULT -> SelectionActionPreview(elapsed - 21_500f, showResult = true)
            }
        }
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            Text(
                "Typing actions are opt-in and configurable.",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun ActionShortcutPreview(
    title: String,
    before: String,
    shortcut: String,
    after: String,
    localElapsed: Float,
) {
    val baseStart = 350f
    val baseStep = 130f
    val shortcutStep = 580f
    val baseCount = (((localElapsed - baseStart) / baseStep).toInt()).coerceIn(0, before.length)
    val shortcutStart = baseStart + before.length * baseStep + 900f
    val shortcutCount = (((localElapsed - shortcutStart) / shortcutStep).toInt()).coerceIn(0, shortcut.length)
    val transformed = localElapsed >= shortcutStart + shortcut.length * shortcutStep + 850f
    val command = before.take(baseCount) + shortcut.take(shortcutCount)
    val cursor = tutorialCursorAlpha()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Surface(
            modifier = Modifier.fillMaxWidth().height(82.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Box(Modifier.padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) {
                AnimatedContent(
                    targetState = transformed,
                    transitionSpec = { fadeIn(tween(380)).togetherWith(fadeOut(tween(220))) },
                    label = "action transformation",
                ) { done ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (done) after else command,
                            fontFamily = if (done) FontFamily.Default else FontFamily.Monospace,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        TutorialCursor(cursor, 24)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Text(shortcut, Modifier.padding(horizontal = 11.dp, vertical = 7.dp), fontFamily = FontFamily.Monospace)
            }
            AnimatedVisibility(visible = transformed, enter = fadeIn() + scaleIn()) {
                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun SelectionActionPreview(localElapsed: Float, showResult: Boolean) {
    val menuPress = tutorialPressProgress(localElapsed, 750f, 1_150f, 1_550f)
    val actionPress = tutorialPressProgress(localElapsed, 2_500f, 2_950f, 3_400f)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Selected-text actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Surface(
            modifier = Modifier.fillMaxWidth().height(78.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Box(Modifier.padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) {
                if (showResult) {
                    Text("Weekly Project Update")
                } else {
                    Text(
                        buildAnnotatedString {
                            pushStyle(
                                SpanStyle(
                                    background = MaterialTheme.colorScheme.primaryContainer,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                            )
                            append("weekly project update")
                            pop()
                        },
                    )
                }
            }
        }
        if (!showResult) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            ) {
                Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Cut", Modifier.padding(7.dp), style = MaterialTheme.typography.labelMedium)
                    Text("Copy", Modifier.padding(7.dp), style = MaterialTheme.typography.labelMedium)
                    Box(contentAlignment = Alignment.Center) {
                        Text("Expanda actions", Modifier.padding(7.dp), style = MaterialTheme.typography.labelMedium)
                        TutorialTouch(menuPress)
                    }
                }
            }
            AnimatedVisibility(
                visible = localElapsed >= 1_100f,
                enter = fadeIn(tween(300)) + slideInVertically(tween(340)) { -it / 5 },
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(0.86f),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = 8.dp,
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Text("Expanda actions", Modifier.padding(8.dp), fontWeight = FontWeight.SemiBold)
                        Text("Uppercase", Modifier.fillMaxWidth().padding(8.dp))
                        Text("Spaces to underscores", Modifier.fillMaxWidth().padding(8.dp))
                        Box(contentAlignment = Alignment.Center) {
                            Text("Capitalize words", Modifier.fillMaxWidth().padding(8.dp))
                            TutorialTouch(actionPress)
                        }
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text("Capitalization applied", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
internal fun EspansoCompatibilityAnimation() {
    val elapsed = advancedTimeline(ESPANSO_LOOP)
    val imported = elapsed >= 3_000f
    val folderReady = elapsed >= 5_800f
    val desktopReady = elapsed >= 8_000f
    val arrowProgress = when (elapsed) {
        in 1_250f..2_650f -> (elapsed - 1_250f) / 1_400f
        in 6_000f..7_500f -> (elapsed - 6_000f) / 1_500f
        else -> 0f
    }.coerceIn(0f, 1f)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FeatureHeading(
            Icons.Default.Description,
            "Your snippets are plain-text .yml",
            "Plain-text files you can edit, copy or move.",
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilePreview("snippets.yml")
            Text(
                "↔",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f + arrowProgress * 0.65f),
            )
            Surface(
                modifier = Modifier.size(width = 112.dp, height = 174.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Default.PhoneAndroid, null, modifier = Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(10.dp))
                    Text("Expanda", fontWeight = FontWeight.SemiBold)
                    AnimatedVisibility(visible = imported, enter = fadeIn() + scaleIn()) {
                        Text(".yml source", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        AnimatedContent(
            targetState = when {
                desktopReady -> "Same .yml format Espanso uses on desktop"
                folderReady -> "Keep them in Expanda, or link a folder to sync"
                imported -> "Edit the same plain-text files inside Expanda"
                else -> "Your snippets are files you can inspect and move"
            },
            transitionSpec = { fadeIn(tween(320)).togetherWith(fadeOut(tween(220))) },
            label = "espanso source state",
        ) { message -> Text(message, color = MaterialTheme.colorScheme.primary) }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("YOUR CHOICE", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Keep snippets in Expanda for the simplest setup, or link a local folder later to sync with Espanso desktop.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
internal fun SourceEditingAnimation() {
    val elapsed = advancedTimeline(SOURCE_LOOP)
    val aiPromptCopied = elapsed >= 3_200f
    val sourceUpdated = elapsed >= 7_800f
    val validated = elapsed >= 11_100f
    val saved = elapsed >= 12_700f
    val aiPress = tutorialPressProgress(elapsed, 2_000f, 2_500f, 3_100f)
    val savePress = tutorialPressProgress(elapsed, 11_700f, 12_150f, 12_650f)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FeatureHeading(Icons.Default.Code, "Edit the real Espanso source", "Open, copy or edit the same .yml file—then validate and save.")
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("base.yml", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelMedium)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Row(
                                Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("AI prompt", style = MaterialTheme.typography.labelMedium)
                            }
                            TutorialTouch(aiPress)
                        }
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().height(174.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                ) {
                    AnimatedContent(
                        targetState = sourceUpdated,
                        transitionSpec = { fadeIn(tween(420)).togetherWith(fadeOut(tween(260))) },
                        label = "source edit",
                    ) { edited ->
                        Text(
                            if (edited) {
                                "matches:\n  - trigger: ;tomorrow\n    replace: \"Tomorrow is {{date}}\"\n    vars:\n      - name: date\n        type: date\n        params: {format: \"%Y-%m-%d\", offset: 86400}"
                            } else {
                                "# Work snippets\nmatches:\n  - trigger: ;standup\n    replace: \"Daily update: $|$\""
                            },
                            modifier = Modifier.padding(12.dp),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (edited) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (validated) Text("Valid YAML", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = if (saved) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (saved) {
                                    Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(5.dp))
                                }
                                Text(if (saved) "Saved" else "Save", style = MaterialTheme.typography.labelMedium)
                            }
                            TutorialTouch(savePress)
                        }
                    }
                }
            }
        }
        AnimatedVisibility(visible = aiPromptCopied && !sourceUpdated, enter = fadeIn() + slideInVertically { it / 4 }) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ) {
                Text(
                    "Add a ;tomorrow snippet with tomorrow’s date.",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Text(
            "Same file, no conversion. Untouched comments and formatting stay as they are.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun OpenSourceProjectAnimation() {
    val timeline = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        timeline.animateTo(
            targetValue = OPEN_SOURCE_LOOP.toFloat(),
            animationSpec = tween(OPEN_SOURCE_LOOP, easing = LinearEasing),
        )
    }
    val elapsed = timeline.value
    val showLocal = elapsed >= 1_200f
    val showSource = elapsed >= 2_600f
    val showNote = elapsed >= 4_200f
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FeatureHeading(Icons.Default.Code, "Open source, local first", "Use Expanda, inspect it and help improve it.")
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    modifier = Modifier.size(76.dp),
                    shape = RoundedCornerShape(21.dp),
                    color = Color.White,
                    shadowElevation = 4.dp,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_app_panda),
                        contentDescription = "Expanda logo",
                        modifier = Modifier.padding(9.dp),
                    )
                }
                AnimatedVisibility(visible = showLocal, enter = fadeIn(tween(420)) + slideInVertically { it / 4 }) {
                    ProjectValue(Icons.Default.Lock, "No Internet permission, ads or analytics")
                }
                AnimatedVisibility(visible = showSource, enter = fadeIn(tween(420)) + slideInVertically { it / 4 }) {
                    ProjectValue(Icons.Default.Code, "GPLv3 source code you can inspect and change")
                }
                AnimatedVisibility(visible = showNote, enter = fadeIn(tween(420))) {
                    Text(
                        "I built Expanda for my own use with extensive AI help. Bugs may remain, and contributions are welcome.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        OutlinedButton(onClick = { uriHandler.openUri(ProjectLinks.REPOSITORY) }) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
            Spacer(Modifier.width(8.dp))
            Text("Open GitHub repository")
        }
    }
}

@Composable
private fun ProjectValue(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun FilePreview(name: String) {
    Surface(
        modifier = Modifier.size(width = 134.dp, height = 126.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Default.Description, null, modifier = Modifier.size(30.dp), tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(8.dp))
            Text(name, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(5.dp))
            Text("- trigger: ;hello", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun advancedTimeline(durationMillis: Int): Float {
    val transition = rememberInfiniteTransition(label = "advanced tutorial")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis, easing = LinearEasing)),
        label = "advanced tutorial timeline",
    )
    return progress * durationMillis
}
