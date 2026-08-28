package dev.diego.expanda.ui.tutorial

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.diego.expanda.R
import dev.diego.expanda.data.AppSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 11
private const val LOOP_MILLIS = 6_800
private const val TITLE_TRIGGER = ",xpd"
private const val SUBTITLE_TRIGGER = "/tlsm"
private const val TRIGGER = ";hello"
private const val EXPANSION = "Hello! Thanks for getting in touch."

@Composable
fun TutorialScreen(
    step: Int,
    settings: AppSettings,
    onStepChange: (Int) -> Unit,
    onSuggestionEnabledChanged: (Boolean) -> Unit,
    onSuggestionCompactChanged: (Boolean) -> Unit,
    onSuggestionMinCharsChanged: (Int) -> Unit,
    onSuggestionMaxHeightChanged: (Int) -> Unit,
    onSuggestionWidthChanged: (Float) -> Unit,
    onSuggestionResizeHandleChanged: (Boolean) -> Unit,
    onDone: () -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = step.coerceIn(0, PAGE_COUNT - 1),
        pageCount = { PAGE_COUNT },
    )
    val scope = rememberCoroutineScope()
    val animationVisits = remember { mutableStateMapOf<Int, Int>() }
    var titleCharacters by rememberSaveable { mutableIntStateOf(0) }
    var titleExpanded by rememberSaveable { mutableStateOf(false) }
    var logoVisible by rememberSaveable { mutableStateOf(false) }
    var subtitleVisible by rememberSaveable { mutableStateOf(false) }
    var subtitleCharacters by rememberSaveable { mutableIntStateOf(0) }
    var subtitleExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (titleCharacters == 0) delay(700)
        while (titleCharacters < TITLE_TRIGGER.length) {
            titleCharacters++
            delay(250)
        }
        if (!titleExpanded) {
            delay(400)
            titleExpanded = true
            logoVisible = true
        } else if (!logoVisible) {
            logoVisible = true
        }
        if (!subtitleVisible) {
            delay(400)
            subtitleVisible = true
        }
        while (subtitleCharacters < SUBTITLE_TRIGGER.length) {
            subtitleCharacters++
            delay(290)
        }
        if (!subtitleExpanded) {
            delay(400)
            subtitleExpanded = true
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                onStepChange(page)
                if (page > 0) animationVisits[page] = (animationVisits[page] ?: 0) + 1
            }
    }

    fun goTo(page: Int) {
        scope.launch { pagerState.animateScrollToPage(page.coerceIn(0, PAGE_COUNT - 1)) }
    }
    // Storage/source pages are part of setup even when the feature tour is skipped.
    fun skipToStorage() = goTo(9)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            key = { it },
        ) { page ->
            if (page == 0) {
                WelcomePage(
                    titleCharacters = titleCharacters,
                    titleExpanded = titleExpanded,
                    logoVisible = logoVisible,
                    subtitleVisible = subtitleVisible,
                    subtitleCharacters = subtitleCharacters,
                    subtitleExpanded = subtitleExpanded,
                    onGetStarted = { goTo(1) },
                )
            } else {
                key(animationVisits[page] ?: 0) {
                    when (page) {
                        1 -> TutorialPage(
                            page = page,
                            tip = "Set triggers to expand instantly—no space required.",
                            onSkip = ::skipToStorage,
                            onNext = { goTo(2) },
                        ) { ShortcutExpansionAnimation() }
                        2 -> TutorialPage(
                            page = page,
                            onSkip = ::skipToStorage,
                            onNext = { goTo(3) },
                        ) { UndoExpansionAnimation() }
                        3 -> TutorialPage(
                            page = page,
                            onSkip = ::skipToStorage,
                            onNext = { goTo(4) },
                        ) {
                            SuggestionTutorialContent(
                                settings = settings,
                                onEnabledChanged = onSuggestionEnabledChanged,
                                onCompactChanged = onSuggestionCompactChanged,
                                onMinCharsChanged = onSuggestionMinCharsChanged,
                                onMaxHeightChanged = onSuggestionMaxHeightChanged,
                                onWidthChanged = onSuggestionWidthChanged,
                                onResizeHandleChanged = onSuggestionResizeHandleChanged,
                            )
                        }
                        4 -> TutorialPage(
                            page = page,
                            tip = "Date, time and random values refresh on every expansion.",
                            onSkip = ::skipToStorage,
                            onNext = { goTo(5) },
                        ) { DynamicValuesAnimation() }
                        5 -> TutorialPage(
                            page = page,
                            onSkip = ::skipToStorage,
                            onNext = { goTo(6) },
                        ) { FormChoiceAnimation() }
                        6 -> TutorialPage(
                            page = page,
                            tip = "Regex triggers do not appear in popup suggestions",
                            onSkip = ::skipToStorage,
                            onNext = { goTo(7) },
                        ) { RegexAnimation() }
                        7 -> TutorialPage(
                            page = page,
                            onSkip = ::skipToStorage,
                            onNext = { goTo(8) },
                        ) { ActionsAnimation() }
                        8 -> TutorialPage(
                            page = page,
                            onSkip = ::skipToStorage,
                            onNext = { goTo(9) },
                        ) { OpenSourceProjectAnimation() }
                        9 -> TutorialPage(
                            page = page,
                            onSkip = ::skipToStorage,
                            showSkip = false,
                            onNext = { goTo(10) },
                        ) { EspansoCompatibilityAnimation() }
                        else -> TutorialPage(
                            page = page,
                            onSkip = ::skipToStorage,
                            showSkip = false,
                            onNext = onDone,
                            nextLabel = "Continue",
                        ) { SourceEditingAnimation() }
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomePage(
    titleCharacters: Int,
    titleExpanded: Boolean,
    logoVisible: Boolean,
    subtitleVisible: Boolean,
    subtitleCharacters: Int,
    subtitleExpanded: Boolean,
    onGetStarted: () -> Unit,
) {
    val cursorAlpha = tutorialCursorAlpha()
    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = logoVisible,
                transitionSpec = {
                    (fadeIn(tween(500)) + scaleIn(tween(600), initialScale = 0.72f))
                        .togetherWith(fadeOut(tween(180)))
                },
                label = "logo appearance",
            ) { visible ->
                if (visible) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(32.dp),
                        color = Color.White,
                        shadowElevation = 8.dp,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_app_panda),
                            contentDescription = "Expanda logo",
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                } else {
                    Spacer(Modifier.fillMaxSize())
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(
                targetState = titleExpanded,
                transitionSpec = {
                    (fadeIn(tween(520)) + slideInHorizontally(tween(520)) { it / 6 })
                        .togetherWith(fadeOut(tween(260)) + slideOutHorizontally(tween(300)) { -it / 6 })
                },
                label = "title expansion",
            ) { expanded ->
                Text(
                    if (expanded) "expanda" else TITLE_TRIGGER.take(titleCharacters),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    fontFamily = if (expanded) FontFamily.Default else FontFamily.Monospace,
                )
            }
            TutorialCursor(alpha = cursorAlpha, heightDp = 38)
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.height(32.dp), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = subtitleVisible,
                transitionSpec = { fadeIn(tween(320)).togetherWith(fadeOut(tween(160))) },
                label = "subtitle appearance",
            ) { visible ->
                if (visible) {
                    AnimatedContent(
                        targetState = subtitleExpanded,
                        transitionSpec = { fadeIn(tween(500)).togetherWith(fadeOut(tween(240))) },
                        label = "subtitle expansion",
                    ) { expanded ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (expanded) "Type less, say more." else SUBTITLE_TRIGGER.take(subtitleCharacters),
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = if (expanded) FontFamily.Default else FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (!expanded) TutorialCursor(alpha = cursorAlpha, heightDp = 22)
                        }
                    }
                } else {
                    Spacer(Modifier.fillMaxSize())
                }
            }
        }
        Spacer(Modifier.weight(1f))
        PageDots(currentPage = 0)
        Spacer(Modifier.height(22.dp))
        NextButton(label = "Get Started", onClick = onGetStarted)
    }
}

@Composable
private fun TutorialPage(
    page: Int,
    onSkip: () -> Unit,
    onNext: () -> Unit,
    tip: String? = null,
    nextLabel: String = "Next",
    showSkip: Boolean = true,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (showSkip) TextButton(onClick = onSkip) { Text("Skip") }
        }
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) { content() }
        if (tip != null) {
            TutorialTip(text = tip)
            Spacer(Modifier.height(16.dp))
        }
        PageDots(currentPage = page)
        Spacer(Modifier.height(22.dp))
        NextButton(label = nextLabel, onClick = onNext)
    }
}

@Composable
internal fun TutorialTip(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Lightbulb, contentDescription = null, modifier = Modifier.size(18.dp))
            Column {
                Text("Tip", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(text, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun NextButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(label, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
    }
}

@Composable
private fun PageDots(currentPage: Int) {
    Row(
        modifier = Modifier.height(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(PAGE_COUNT) { index ->
            Box(
                Modifier
                    .size(if (index == currentPage) 9.dp else 7.dp)
                    .clip(CircleShape)
                    .background(
                        if (index == currentPage) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                    ),
            )
        }
    }
}

@Composable
private fun ShortcutExpansionAnimation() {
    val infinite = rememberInfiniteTransition(label = "shortcut demonstration")
    val timeline by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(LOOP_MILLIS, easing = LinearEasing)),
        label = "animation timeline",
    )
    val cursorAlpha = tutorialCursorAlpha()
    val elapsed = timeline * LOOP_MILLIS
    val typedCount = (((elapsed - 650f) / 260f).toInt()).coerceIn(0, TRIGGER.length)
    val expanded = elapsed >= 3_250f
    val celebration = ((elapsed - 3_250f) / 600f).coerceIn(0f, 1f)
    val pressProgress = tutorialPressProgress(elapsed, 2_400f, 2_850f, 3_250f)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Surface(
            modifier = Modifier.size(58.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Icon(
                Icons.Default.Bolt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(15.dp),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Expand as you type",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Type a shortcut and press space.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth().height(76.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(
                1.dp,
                lerp(MaterialTheme.colorScheme.outlineVariant, MaterialTheme.colorScheme.primary, pressProgress),
            ),
        ) {
            Box(Modifier.fillMaxSize().padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) {
                AnimatedContent(
                    targetState = expanded,
                    transitionSpec = {
                        (fadeIn(tween(420)) + slideInHorizontally(tween(420)) { it / 5 })
                            .togetherWith(fadeOut(tween(220)) + slideOutHorizontally(tween(260)) { -it / 5 })
                    },
                    label = "expanded text",
                ) { showExpansion ->
                    if (showExpansion) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(EXPANSION, style = MaterialTheme.typography.bodyLarge)
                            AnimatedVisibility(
                                visible = celebration < 0.95f,
                                enter = fadeIn() + scaleIn(),
                                exit = fadeOut() + scaleOut(),
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 6.dp).size(20.dp)
                                        .scale(0.8f + celebration * 0.35f),
                                )
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                TRIGGER.take(typedCount),
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            TutorialCursor(alpha = cursorAlpha, heightDp = 24)
                        }
                    }
                }
            }
        }
        TutorialKeyboard(pressedKey = TutorialKey.SPACE, pressProgress = pressProgress)
    }
}
