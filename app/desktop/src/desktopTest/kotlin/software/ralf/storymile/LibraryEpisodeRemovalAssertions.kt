package software.ralf.storymile

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import assertk.assertThat
import assertk.assertions.isLessThan
import assertk.assertions.isNotEqualTo
import assertk.assertions.isTrue
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun ComposeUiTest.assertSurvivingEpisodeMovesWithoutClipping(
  tab: String,
  index: Int,
  remove: () -> Unit,
) {
  val original = episodeArt(tab = tab, index = index).fetchSemanticsNode().boundsInRoot
  mainClock.autoAdvance = false
  try {
    remove()
    waitUntil(timeoutMillis = 5_000) {
      mainClock.advanceTimeByFrame()
      onAllNodes(hasTestTag("library-$tab-episode-$index")).fetchSemanticsNodes().isEmpty()
    }
    val survivor = episodeArt(tab = tab, index = index - 1)
    val frames =
      List(6) {
        mainClock.advanceTimeByFrame()
        survivor.fetchSemanticsNode().boundsInRoot
      }
    mainClock.advanceTimeBy(1_000)
    val settled = survivor.fetchSemanticsNode().boundsInRoot

    assertThat(frames.any { it.top > settled.top + 1f && it.top < original.top - 1f }).isTrue()
    frames.forEach { frame ->
      assertThat(abs(frame.height - original.height), "visible artwork height").isLessThan(1f)
    }
  } finally {
    mainClock.autoAdvance = true
  }
}

internal fun ComposeUiTest.assertLastEpisodeFadesBeforeEmptyState(
  tab: String,
  emptyTitle: String,
  remove: () -> Unit,
) {
  val artwork = episodeArt(tab = tab, index = 0).fetchSemanticsNode().boundsInRoot
  val x = (artwork.left + artwork.width / 4).roundToInt()
  val y = artwork.center.y.roundToInt()
  val beforeRemoval = pixelAt(x = x, y = y)
  val empty = onNodeWithText(emptyTitle)
  mainClock.autoAdvance = false
  val duringExit =
    try {
      remove()
      waitUntil(timeoutMillis = 5_000) {
        mainClock.advanceTimeByFrame()
        onAllNodes(hasTestTag("library-$tab-episode-0")).fetchSemanticsNodes().isEmpty()
      }
      mainClock.advanceTimeBy(64)
      empty.assertDoesNotExist()
      pixelAt(x = x, y = y)
    } finally {
      mainClock.autoAdvance = true
    }
  waitUntilAtLeastOneExists(matcher = hasText(emptyTitle), timeoutMillis = 5_000)
  waitForIdle()
  assertThat(duringExit, "fading artwork pixel").isNotEqualTo(beforeRemoval)
  assertThat(duringExit, "artwork still drawn before the empty state")
    .isNotEqualTo(pixelAt(x = x, y = y))
}

private fun ComposeUiTest.episodeArt(tab: String, index: Int) =
  onNodeWithTag("library-$tab-artwork-$index", useUnmergedTree = true)

private fun ComposeUiTest.pixelAt(x: Int, y: Int): Color =
  onRoot().captureToImage().toPixelMap()[x, y]
