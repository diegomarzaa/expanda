package dev.diego.expanda.ui.tutorial

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DynamicForm
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private const val UNDO_LOOP = 12_600
private const val FEATURE_LOOP = 7_600
private const val FORM_LOOP = 17_800
private const val REGEX_LOOP = 11_000

private enum class UndoStage { TYPING, EXPANDED, RESTORED, IGNORED, CONTINUING }
private enum class FormStage { TRIGGER, FORM, RESULT }

@Composable
internal fun UndoExpansionAnimation() {
    val elapsed = tutorialTimeline(UNDO_LOOP)
    val typedCount = (((elapsed - 450f) / 230f).toInt()).coerceIn(0, 6)
    val stage = when {
        elapsed < 2_900f -> UndoStage.TYPING
        elapsed < 5_250f -> UndoStage.EXPANDED
        elapsed < 7_650f -> UndoStage.RESTORED
        elapsed < 8_650f -> UndoStage.IGNORED
        else -> UndoStage.CONTINUING
    }
    val firstSpacePress = tutorialPressProgress(elapsed, 2_150f, 2_550f, 2_900f)
    val backspacePress = tutorialPressProgress(elapsed, 4_450f, 4_850f, 5_250f)
    val secondSpacePress = tutorialPressProgress(elapsed, 6_850f, 7_250f, 7_650f)
    val continued = "later".take((((elapsed - 8_650f) / 260f).toInt()).coerceIn(0, 5))
    val pressedKey = when {
        firstSpacePress > 0f || secondSpacePress > 0f -> TutorialKey.SPACE
        backspacePress > 0f -> TutorialKey.BACKSPACE
        else -> TutorialKey.NONE
    }
    val press = maxOf(firstSpacePress, backspacePress, secondSpacePress)
    val cursor = tutorialCursorAlpha()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        FeatureHeading(Icons.AutoMirrored.Filled.Undo, "Undo in one tap", "Backspace restores the original trigger.")
        TutorialTextField {
            AnimatedContent(
                targetState = stage,
                transitionSpec = {
                    (fadeIn(tween(360)) + slideInHorizontally(tween(360)) { it / 6 })
                        .togetherWith(fadeOut(tween(220)) + slideOutHorizontally(tween(260)) { -it / 6 })
                },
                label = "undo expansion",
            ) { current ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when (current) {
                            UndoStage.TYPING -> ";hello".take(typedCount)
                            UndoStage.EXPANDED -> "Hello! Thanks for getting in touch."
                            UndoStage.RESTORED -> ";hello"
                            UndoStage.IGNORED -> ";hello "
                            UndoStage.CONTINUING -> ";hello $continued"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = if (current == UndoStage.EXPANDED) FontFamily.Default else FontFamily.Monospace,
                    )
                    TutorialCursor(alpha = cursor, heightDp = 24)
                }
            }
        }
        TutorialKeyboard(pressedKey = pressedKey, pressProgress = press)
        AnimatedVisibility(visible = stage >= UndoStage.RESTORED, enter = fadeIn() + scaleIn()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (stage == UndoStage.RESTORED) "Trigger restored" else "Next space is ignored",
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
internal fun DynamicValuesAnimation() {
    val elapsed = tutorialTimeline(FEATURE_LOOP)
    val typedCount = (((elapsed - 450f) / 210f).toInt()).coerceIn(0, 6)
    val expanded = elapsed >= 2_350f
    val showDate = elapsed >= 2_900f
    val showTime = elapsed >= 3_450f
    val showRandom = elapsed >= 4_000f
    val cursor = tutorialCursorAlpha()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FeatureHeading(Icons.Default.CalendarMonth, "Dynamic text", "Generate fresh values every time.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VariableBadge(Icons.Default.CalendarMonth, "Date")
            VariableBadge(Icons.Default.Schedule, "Time")
            VariableBadge(Icons.Default.Casino, "Random")
        }
        TutorialTextField(minHeight = 112) {
            if (!expanded) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(";brief".take(typedCount), fontFamily = FontFamily.Monospace)
                    TutorialCursor(alpha = cursor, heightDp = 23)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Daily brief", fontWeight = FontWeight.SemiBold)
                    AnimatedValue(showDate, "2026-08-25", MaterialTheme.colorScheme.primary)
                    AnimatedValue(showTime, "09:30", MaterialTheme.colorScheme.secondary)
                    AnimatedValue(showRandom, "Reference · Q7M4", MaterialTheme.colorScheme.tertiary)
                }
            }
        }
    }
}

@Composable
internal fun FormChoiceAnimation() {
    val elapsed = tutorialTimeline(FORM_LOOP)
    val stage = when {
        elapsed < 3_450f -> FormStage.TRIGGER
        elapsed < 13_450f -> FormStage.FORM
        else -> FormStage.RESULT
    }
    val trigger = ";meeting"
    val triggerCount = (((elapsed - 450f) / 230f).toInt()).coerceIn(0, trigger.length)
    val spacePress = tutorialPressProgress(elapsed, 2_450f, 2_850f, 3_250f)
    val name = "Sam".take((((elapsed - 4_350f) / 420f).toInt()).coerceIn(0, 3))
    val choiceOpen = elapsed in 6_200f..7_550f
    val choiceSelected = elapsed >= 7_100f
    val choicePress = tutorialPressProgress(elapsed, 6_200f, 6_550f, 6_900f)
    val choiceItemPress = tutorialPressProgress(elapsed, 6_900f, 7_200f, 7_550f)
    val clockOpen = elapsed in 8_450f..11_750f
    val timePress = tutorialPressProgress(elapsed, 7_850f, 8_200f, 8_550f)
    val clockPress = tutorialPressProgress(elapsed, 10_000f, 10_400f, 10_850f)
    val timeChanged = elapsed >= 10_500f
    val clockDonePress = tutorialPressProgress(elapsed, 10_950f, 11_300f, 11_700f)
    val insertPress = tutorialPressProgress(elapsed, 12_300f, 12_700f, 13_250f)
    val cursor = tutorialCursorAlpha()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FeatureHeading(Icons.Default.DynamicForm, "Ask only when needed", "Complete a form before inserting text.")
        AnimatedContent(
            targetState = stage,
            transitionSpec = {
                (fadeIn(tween(380)) + slideInVertically(tween(380)) { it / 8 })
                    .togetherWith(fadeOut(tween(220)) + slideOutVertically(tween(260)) { -it / 8 })
            },
            label = "form tutorial",
        ) { current ->
            when (current) {
                FormStage.TRIGGER -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    TutorialTextField {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(trigger.take(triggerCount), fontFamily = FontFamily.Monospace)
                            TutorialCursor(cursor, 23)
                        }
                    }
                    TutorialKeyboard(TutorialKey.SPACE, spacePress)
                }
                FormStage.FORM -> FormPreview(
                    choicePress = choicePress,
                    choiceItemPress = choiceItemPress,
                    choiceOpen = choiceOpen,
                    choiceSelected = choiceSelected,
                    insertPress = insertPress,
                    name = name,
                    nameCursor = elapsed in 4_150f..5_900f,
                    timePress = timePress,
                    clockOpen = clockOpen,
                    clockPress = clockPress,
                    clockDonePress = clockDonePress,
                    timeChanged = timeChanged,
                )
                FormStage.RESULT -> TutorialTextField(minHeight = 100) {
                    Text("Meeting with Sam · Online\n2026-08-26 at 10:30")
                }
            }
        }
    }
}

@Composable
internal fun RegexAnimation() {
    val elapsed = tutorialTimeline(REGEX_LOOP)
    val input = ";eta 15 Sam"
    val typedCount = (((elapsed - 650f) / 185f).toInt()).coerceIn(0, input.length)
    val expanded = elapsed >= 5_100f
    val cursor = tutorialCursorAlpha()
    val triggerRule = buildAnnotatedString {
        append(";eta\\s+")
        pushStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold))
        append("(?P<minutes>\\d+)")
        pop()
        append("\\s+")
        pushStyle(SpanStyle(color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold))
        append("(?P<name>\\w+)")
        pop()
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FeatureHeading(Icons.Default.Code, "Reuse changing details", "Use regular expressions.")
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("Trigger", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(triggerRule, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RegexCaptureBadge("15", "{{minutes}}", MaterialTheme.colorScheme.primaryContainer)
                    RegexCaptureBadge("Sam", "{{name}}", MaterialTheme.colorScheme.tertiaryContainer)
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    "Hi {{name}} — I’m about {{minutes}} minutes away. I’ll message you when I arrive.",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        TutorialTextField {
            AnimatedContent(
                targetState = expanded,
                transitionSpec = { fadeIn(tween(420)).togetherWith(fadeOut(tween(240))) },
                label = "regex expansion",
            ) { showResult ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (showResult) {
                            "Hi Sam — I’m about 15 minutes away. I’ll message you when I arrive."
                        } else input.take(typedCount),
                        modifier = Modifier.weight(1f, fill = false),
                        fontFamily = if (showResult) FontFamily.Default else FontFamily.Monospace,
                    )
                    TutorialCursor(alpha = cursor, heightDp = 23)
                }
            }
        }
    }
}

@Composable
private fun RegexCaptureBadge(value: String, token: String, color: androidx.compose.ui.graphics.Color) {
    Surface(shape = RoundedCornerShape(10.dp), color = color) {
        Text(
            "$value → $token",
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun FormPreview(
    choicePress: Float,
    choiceItemPress: Float,
    choiceOpen: Boolean,
    choiceSelected: Boolean,
    insertPress: Float,
    name: String,
    nameCursor: Boolean,
    timePress: Float,
    clockOpen: Boolean,
    clockPress: Float,
    clockDonePress: Float,
    timeChanged: Boolean,
) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.fillMaxWidth().alpha(if (clockOpen) 0.38f else 1f),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 8.dp,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Complete snippet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Meeting with", color = MaterialTheme.colorScheme.onSurfaceVariant)
                InlineField(
                    value = name,
                    placeholder = "Name",
                    showCursor = nameCursor,
                    modifier = Modifier.fillMaxWidth(),
                )
                ChoiceField(
                    open = choiceOpen,
                    selected = choiceSelected,
                    fieldTouch = choicePress,
                    itemTouch = choiceItemPress,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    InlineField("2026-08-26")
                    Text("at")
                    Box(contentAlignment = Alignment.Center) {
                        InlineField(if (timeChanged) "10:30" else "10:00")
                        TutorialTouch(timePress)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text("Cancel", modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(contentAlignment = Alignment.Center) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ) { Text("Insert", modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) }
                        TutorialTouch(insertPress)
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = clockOpen,
            enter = fadeIn(tween(320)) + scaleIn(tween(360), initialScale = 0.88f),
            exit = fadeOut(tween(220)),
        ) {
            ClockPickerPreview(
                selectedMinute = if (timeChanged) 30 else 0,
                clockPress = clockPress,
                donePress = clockDonePress,
            )
        }
    }
}

@Composable
private fun ChoiceField(open: Boolean, selected: Boolean, fieldTouch: Float, itemTouch: Float) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(if (selected) "Online" else "Choose location")
                Text(if (open) "▲" else "▼", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TutorialTouch(fieldTouch)
            }
        }
        AnimatedVisibility(visible = open, enter = fadeIn(tween(220)) + slideInVertically { -it / 4 }) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 6.dp,
            ) {
                Column {
                    Box(contentAlignment = Alignment.Center) {
                        Text("Online", modifier = Modifier.fillMaxWidth().padding(11.dp))
                        TutorialTouch(itemTouch)
                    }
                    Text("In person", modifier = Modifier.fillMaxWidth().padding(11.dp))
                }
            }
        }
    }
}

@Composable
private fun InlineField(
    value: String,
    placeholder: String = value,
    showCursor: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                value.ifEmpty { placeholder },
                color = if (value.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            if (showCursor) TutorialCursor(tutorialCursorAlpha(), 20)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ClockPickerPreview(selectedMinute: Int, clockPress: Float, donePress: Float) {
    val pickerState = rememberTimePickerState(initialHour = 10, initialMinute = 0, is24Hour = true)
    LaunchedEffect(selectedMinute) { pickerState.minute = selectedMinute }
    Surface(
        modifier = Modifier.fillMaxWidth(0.96f),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 12.dp,
    ) {
        Column(
            Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Select time", modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelLarge)
            Box(contentAlignment = Alignment.Center) {
                TimePicker(state = pickerState)
                TutorialTouch(
                    clockPress,
                    Modifier.align(Alignment.BottomCenter).offset(y = (-54).dp),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(contentAlignment = Alignment.Center) {
                    Text("OK", modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp), color = MaterialTheme.colorScheme.primary)
                    TutorialTouch(donePress)
                }
            }
        }
    }
}

@Composable
private fun VariableBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun AnimatedValue(visible: Boolean, value: String, color: androidx.compose.ui.graphics.Color) {
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(320)) + slideInHorizontally { it / 5 }) {
        Text(value, color = color)
    }
}

@Composable
internal fun FeatureHeading(icon: ImageVector, title: String, subtitle: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.padding(12.dp).size(24.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TutorialTextField(minHeight: Int = 76, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(minHeight.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) {
            content()
        }
    }
}

@Composable
private fun tutorialTimeline(durationMillis: Int): Float {
    val transition = rememberInfiniteTransition(label = "feature tutorial")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis, easing = LinearEasing)),
        label = "feature timeline",
    )
    return progress * durationMillis
}
