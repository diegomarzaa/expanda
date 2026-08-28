package dev.diego.expanda.ui.tutorial

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.diego.expanda.data.AppSettings
import dev.diego.expanda.ui.settings.SuggestionSettingsPanel
import dev.diego.expanda.ui.suggestion.SuggestionOverlaySpec

private const val POPUP_LOOP = 9_600
private const val POPUP_TRIGGER = ";reply"
private const val PREVIEW_RESERVED_HEIGHT_DP = 548

private data class ReplySuggestion(val trigger: String, val replacement: String)

private val replyOptions = listOf(
    ReplySuggestion(";reply-soon", "Thanks — I’ll get back to you soon."),
    ReplySuggestion(";reply-yes", "Sounds good. See you then!"),
    ReplySuggestion(";reply-busy", "I’m unavailable right now."),
    ReplySuggestion(";reply-details", "Could you share a few more details?"),
    ReplySuggestion(";reply-thanks", "Thanks for the update!"),
)

@Composable
internal fun SuggestionTutorialContent(
    settings: AppSettings,
    onEnabledChanged: (Boolean) -> Unit,
    onCompactChanged: (Boolean) -> Unit,
    onMinCharsChanged: (Int) -> Unit,
    onMaxHeightChanged: (Int) -> Unit,
    onWidthChanged: (Float) -> Unit,
    onResizeHandleChanged: (Boolean) -> Unit,
) {
    var previewWidth by remember(settings.suggestionWidthFraction) {
        mutableFloatStateOf(settings.suggestionWidthFraction)
    }
    var previewHeight by remember(settings.suggestionMaxHeightDp) {
        mutableIntStateOf(settings.suggestionMaxHeightDp)
    }
    val previewSettings = settings.copy(
        suggestionWidthFraction = previewWidth,
        suggestionMaxHeightDp = previewHeight,
    )
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Suggestions you can see", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Don’t memorize every trigger.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AnimatedVisibility(
            visible = settings.suggestionEnabled,
            enter = fadeIn(tween(360)) + scaleIn(tween(420), initialScale = 0.94f),
            exit = fadeOut(tween(220)) + scaleOut(tween(220), targetScale = 0.96f),
        ) {
            SuggestionOverlayAnimation(previewSettings)
        }
        if (!settings.suggestionEnabled) {
            Surface(
                modifier = Modifier.fillMaxWidth().height(116.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    Modifier.padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Enable the overlay below to try the live preview.")
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            SuggestionSettingsPanel(
                settings = settings,
                onEnabledChanged = onEnabledChanged,
                onCompactChanged = onCompactChanged,
                onMinCharsChanged = onMinCharsChanged,
                onMaxHeightChanged = onMaxHeightChanged,
                onWidthChanged = onWidthChanged,
                onResizeHandleChanged = onResizeHandleChanged,
                compactPresentation = true,
                onWidthPreviewChanged = { previewWidth = it },
                onHeightPreviewChanged = { previewHeight = it },
            )
        }
    }
}

@Composable
private fun SuggestionOverlayAnimation(settings: AppSettings) {
    val transition = rememberInfiniteTransition(label = "suggestion overlay tutorial")
    val progress by transition.animateFloat(
        0f,
        1f,
        animationSpec = infiniteRepeatable(tween(POPUP_LOOP, easing = LinearEasing)),
        label = "suggestion timeline",
    )
    val elapsed = progress * POPUP_LOOP
    val typedCount = (((elapsed - 450f) / 210f).toInt()).coerceIn(0, POPUP_TRIGGER.length)
    val enoughCharacters = typedCount >= settings.suggestionMinChars
    val resultSelected = elapsed >= 7_550f && enoughCharacters
    val popupVisible = enoughCharacters && !resultSelected
    val dragProgress = ((elapsed - 2_600f) / 1_150f).coerceIn(0f, 1f)
    val dragTouch = when (elapsed) {
        in 2_350f..2_750f -> (elapsed - 2_350f) / 400f
        in 2_750f..3_650f -> 1f
        in 3_650f..4_050f -> 1f - (elapsed - 3_650f) / 400f
        else -> 0f
    }.coerceIn(0f, 1f)
    val clickTouch = tutorialPressProgress(elapsed, 6_650f, 7_050f, 7_500f)
    val cursor = tutorialCursorAlpha()
    val viewportHeight = suggestionViewportHeight(settings)
    val popupSlotHeight = viewportHeight + SuggestionOverlaySpec.TOP_PADDING_DP +
        SuggestionOverlaySpec.BOTTOM_PADDING_DP + SuggestionOverlaySpec.HANDLE_HEIGHT_DP
    val heightProgress = ((viewportHeight - 120f) / (366f - 120f)).coerceIn(0f, 1f)
    val topPadding by animateDpAsState(
        targetValue = (28f + heightProgress * 30f).dp,
        animationSpec = tween(420),
        label = "adaptive suggestion preview position",
    )

    Box(
        modifier = Modifier.fillMaxWidth().height(PREVIEW_RESERVED_HEIGHT_DP.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = topPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimatedContent(
                targetState = popupVisible,
                modifier = Modifier.fillMaxWidth().height(popupSlotHeight.dp),
                contentAlignment = Alignment.BottomCenter,
                transitionSpec = {
                    (fadeIn(tween(280)) + slideInVertically(tween(320)) { it / 5 })
                        .togetherWith(fadeOut(tween(220)) + slideOutVertically(tween(250)) { -it / 7 })
                },
                label = "suggestion popup visibility",
            ) { visible ->
                if (visible) {
                    SuggestionPopupPreview(
                        settings = settings,
                        elapsed = elapsed,
                        dragProgress = dragProgress,
                        dragTouch = dragTouch,
                        clickTouch = clickTouch,
                    )
                } else {
                    Spacer(Modifier.size(1.dp))
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth().height(68.dp),
                shape = RoundedCornerShape(17.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                    AnimatedContent(
                        targetState = resultSelected,
                        transitionSpec = { fadeIn(tween(360)).togetherWith(fadeOut(tween(200))) },
                        label = "selected reply",
                    ) { selected ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (selected) replyOptions[1].replacement else POPUP_TRIGGER.take(typedCount),
                                fontFamily = if (selected) FontFamily.Default else FontFamily.Monospace,
                            )
                            TutorialCursor(cursor, 23)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionPopupPreview(
    settings: AppSettings,
    elapsed: Float,
    dragProgress: Float,
    dragTouch: Float,
    clickTouch: Float,
) {
    val contentHeight = suggestionContentHeight(settings)
    val viewportHeight = suggestionViewportHeight(settings)
    val scrollProgress = when (elapsed) {
        in 4_250f..5_250f -> (elapsed - 4_250f) / 1_000f
        in 5_250f..5_850f -> 1f
        in 5_850f..6_450f -> 1f - (elapsed - 5_850f) / 600f
        else -> 0f
    }.coerceIn(0f, 1f)
    val listState = rememberLazyListState()
    val scrollStage = when {
        elapsed < 4_250f || elapsed >= 6_450f -> 0
        elapsed < 5_850f -> 1
        else -> 2
    }
    LaunchedEffect(scrollStage, viewportHeight, settings.suggestionCompactList) {
        when (scrollStage) {
            0 -> listState.scrollToItem(0)
            1 -> listState.animateScrollToItem(replyOptions.lastIndex)
            else -> listState.animateScrollToItem(0)
        }
    }
    val scrolling = elapsed in 4_250f..6_450f && contentHeight > viewportHeight
    val scrollTouch = if (scrolling) {
        when (elapsed) {
            in 4_250f..4_550f -> (elapsed - 4_250f) / 300f
            in 6_150f..6_450f -> 1f - (elapsed - 6_150f) / 300f
            else -> 1f
        }.coerceIn(0f, 1f)
    } else 0f
    val scrollTouchY = ((viewportHeight - 44) * (1f - scrollProgress)).coerceAtLeast(0f)

    Surface(
        modifier = Modifier
            .fillMaxWidth(settings.suggestionWidthFraction)
            .offset(x = (-18f * dragProgress).dp, y = (-22f * dragProgress).dp),
        shape = RoundedCornerShape(SuggestionOverlaySpec.PANEL_RADIUS_DP.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 10.dp,
    ) {
        Column(
            Modifier.padding(
                start = SuggestionOverlaySpec.HORIZONTAL_PADDING_DP.dp,
                top = SuggestionOverlaySpec.TOP_PADDING_DP.dp,
                end = SuggestionOverlaySpec.HORIZONTAL_PADDING_DP.dp,
                bottom = SuggestionOverlaySpec.BOTTOM_PADDING_DP.dp,
            ),
        ) {
            Box(
                Modifier.fillMaxWidth().height(viewportHeight.dp).clipToBounds(),
            ) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(SuggestionOverlaySpec.ROW_GAP_DP.dp),
                ) {
                    itemsIndexed(replyOptions) { index, option ->
                        SuggestionRow(
                            option = option,
                            compact = settings.suggestionCompactList,
                            touch = if (index == 1) clickTouch else 0f,
                        )
                    }
                }
                TutorialTouch(
                    progress = scrollTouch,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = (-24).dp, y = scrollTouchY.dp),
                )
            }
            Box(
                Modifier.fillMaxWidth().requiredHeight(SuggestionOverlaySpec.HANDLE_HEIGHT_DP.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (settings.suggestionResizeHandleEnabled) {
                    Text(
                        "↙",
                        modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 15.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Text(
                    "⠿",
                    modifier = Modifier.rotate(90f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "×",
                    modifier = Modifier.align(Alignment.CenterEnd).padding(horizontal = 15.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium,
                )
                TutorialTouch(dragTouch)
            }
        }
    }
}

private fun suggestionRowHeight(settings: AppSettings): Int =
    if (settings.suggestionCompactList) 48 else 70

private fun suggestionContentHeight(settings: AppSettings): Int =
    replyOptions.size * suggestionRowHeight(settings) +
        (replyOptions.size - 1) * SuggestionOverlaySpec.ROW_GAP_DP

private fun suggestionViewportHeight(settings: AppSettings): Int =
    minOf(suggestionContentHeight(settings), settings.suggestionMaxHeightDp).coerceAtLeast(48)

@Composable
private fun SuggestionRow(option: ReplySuggestion, compact: Boolean, touch: Float) {
    Box(contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(if (compact) 48.dp else 70.dp),
            shape = RoundedCornerShape(SuggestionOverlaySpec.ROW_RADIUS_DP.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(
                Modifier.padding(
                    horizontal = 10.dp,
                    vertical = if (compact) {
                        SuggestionOverlaySpec.COMPACT_ROW_VERTICAL_PADDING_DP.dp
                    } else {
                        SuggestionOverlaySpec.COMFORTABLE_ROW_VERTICAL_PADDING_DP.dp
                    },
                ),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    option.trigger,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!compact) {
                    Text(
                        option.replacement,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        TutorialTouch(touch)
    }
}
