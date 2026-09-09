package thwiply.elopenmike.com.ui.main

import androidx.compose.ui.unit.IntRect

/** Window coordinates, including zero-width separating folds. Ties prefer the left/top pane. */
internal fun largestUnobstructedPane(available: IntRect, obstructions: List<IntRect>): IntRect {
    var panes = listOf(available)
    for (fold in obstructions) {
        panes = panes.flatMap { pane ->
            if (fold.right <= pane.left || fold.left >= pane.right ||
                fold.bottom <= pane.top || fold.top >= pane.bottom
            ) {
                listOf(pane)
            } else {
                listOf(
                    IntRect(pane.left, pane.top, fold.left.coerceIn(pane.left, pane.right), pane.bottom),
                    IntRect(fold.right.coerceIn(pane.left, pane.right), pane.top, pane.right, pane.bottom),
                    IntRect(pane.left, pane.top, pane.right, fold.top.coerceIn(pane.top, pane.bottom)),
                    IntRect(pane.left, fold.bottom.coerceIn(pane.top, pane.bottom), pane.right, pane.bottom),
                ).filter { it.width > 0 && it.height > 0 }
            }
        }
    }
    return panes.maxByOrNull { it.width.toLong() * it.height } ?: IntRect.Zero
}
