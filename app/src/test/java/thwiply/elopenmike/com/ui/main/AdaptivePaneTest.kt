package thwiply.elopenmike.com.ui.main

import androidx.compose.ui.unit.IntRect
import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptivePaneTest {
    @Test fun fullyOccludedViewportFailsClosedInsteadOfDrawingBehindTheHinge() {
        assertEquals(IntRect.Zero,
            largestUnobstructedPane(IntRect(0, 24, 100, 200), listOf(IntRect(0, 0, 100, 300))))
    }

    @Test fun zeroAvailableSpaceRemainsZero() {
        assertEquals(IntRect.Zero, largestUnobstructedPane(IntRect.Zero, emptyList()))
    }

    @Test fun noFoldKeepsTheAvailableWindow() {
        val window = IntRect(0, 24, 1000, 750)
        assertEquals(window, largestUnobstructedPane(window, emptyList()))
    }

    @Test fun verticalHingeUsesTheLargerSide() {
        assertEquals(
            IntRect(420, 24, 1000, 750),
            largestUnobstructedPane(IntRect(0, 24, 1000, 750), listOf(IntRect(400, 0, 420, 800))),
        )
    }

    @Test fun zeroWidthSeparatingFoldStillDividesTheWindow() {
        assertEquals(
            IntRect(0, 24, 500, 750),
            largestUnobstructedPane(IntRect(0, 24, 1000, 750), listOf(IntRect(500, 0, 500, 800))),
        )
    }

    @Test fun tabletopUsesTopPaneWhenKeyboardOccupiesBottomPane() {
        assertEquals(
            IntRect(0, 24, 1000, 400),
            largestUnobstructedPane(IntRect(0, 24, 1000, 550), listOf(IntRect(0, 400, 1000, 420))),
        )
    }

    @Test fun foldOutsideResizedWindowDoesNotConsumeSpace() {
        val window = IntRect(0, 24, 400, 700)
        assertEquals(window, largestUnobstructedPane(window, listOf(IntRect(500, 0, 520, 800))))
    }

    @Test fun multipleOcclusionsDoNotPutContentAcrossAnotherHinge() {
        assertEquals(
            IntRect(320, 24, 680, 750),
            largestUnobstructedPane(
                IntRect(0, 24, 1000, 750),
                listOf(IntRect(300, 0, 320, 800), IntRect(680, 0, 700, 800)),
            ),
        )
    }
}
