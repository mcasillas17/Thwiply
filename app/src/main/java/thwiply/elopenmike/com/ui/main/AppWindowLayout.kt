package thwiply.elopenmike.com.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import thwiply.elopenmike.com.R

internal val LocalFoldingBounds = staticCompositionLocalOf<List<IntRect>> { emptyList() }
internal val LocalCompactHeight = compositionLocalOf { false }

/**
 * One safe viewport per window. Insets are applied/consumed here, not again by nested
 * Scaffolds. The content stays at one composition site through every resize/posture.
 */
@Composable
internal fun AppViewport(
    maxContentWidth: Dp = 720.dp,
    content: @Composable () -> Unit,
) {
    val folds = LocalFoldingBounds.current
    var origin by remember { mutableStateOf(IntOffset.Zero) }
    Layout(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .onGloballyPositioned { origin = it.positionInWindow().round() }
            .testTag("safe-viewport"),
        content = {
            BoxWithConstraints(Modifier.fillMaxSize().testTag("adaptive-pane")) {
                CompositionLocalProvider(LocalCompactHeight provides (maxHeight < 480.dp)) {
                    content()
                }
            }
        },
    ) { measurables, constraints ->
        val available = IntRect(origin, IntSize(constraints.maxWidth, constraints.maxHeight))
        val pane = largestUnobstructedPane(available, folds)
        val width = minOf(pane.width, maxContentWidth.roundToPx())
        val child = measurables.single().measure(Constraints.fixed(width, pane.height))
        layout(constraints.maxWidth, constraints.maxHeight) {
            child.place(
                pane.left - origin.x + (pane.width - width) / 2,
                pane.top - origin.y,
            )
        }
    }
}

/** Unlike a fixed-height app bar, multiline titles remain readable at large font scales. */
@Composable
internal fun AppTopBar(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            content = content,
        )
    }
}

/** Forms and consent text share one scroll surface, including actions, in a short IME window. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        val light = MaterialTheme.colorScheme.background.luminance() > 0.5f
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = light
                    isAppearanceLightNavigationBars = light
                }
            }
        }
        val dismiss by rememberUpdatedState(onDismissRequest)
        val dialogPane = stringResource(R.string.dialog_pane)
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { dismiss() } }) {
            AppViewport(maxContentWidth = 560.dp) {
                Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                            .pointerInput(Unit) { detectTapGestures { } }
                            .semantics { paneTitle = dialogPane }
                            .testTag("adaptive-dialog"),
                    ) {
                        Column(
                            Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            ProvideTextStyle(MaterialTheme.typography.headlineSmall, title)
                            ProvideTextStyle(MaterialTheme.typography.bodyMedium, text)
                            FlowRow(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                            ) {
                                dismissButton?.invoke()
                                confirmButton()
                            }
                        }
                    }
                }
            }
        }
    }
}
