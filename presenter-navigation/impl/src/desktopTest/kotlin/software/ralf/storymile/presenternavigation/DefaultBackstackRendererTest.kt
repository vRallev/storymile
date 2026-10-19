package software.ralf.storymile.presenternavigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNull
import kotlin.test.Test
import software.ralf.storymile.templates.LocalNavigationEntryIdentity
import software.ralf.storymile.templates.NavigationEntryIdentity

class DefaultBackstackRendererTest {
  @Test
  fun `navigation identity links entries and survives their exit transition`() =
    runDesktopComposeUiTest(width = 400, height = 600) {
      val renderer = DefaultBackstackRenderer()
      var backstack by mutableStateOf(listOf(0))
      val onBack = { backstack = backstack.dropLast(1) }
      setContent {
        renderer.PresenterNavDisplay(
          backstack = backstack,
          onBack = onBack,
          modifier = Modifier.fillMaxSize(),
          entryProvider = { key ->
            entryWithIdentity(key = key, tag = "screen-$key", onBack = onBack)
          },
        )
      }
      waitForIdle()
      val rootIdentity = identity("screen-0")
      assertThat(rootIdentity.previous).isNull()

      runOnIdle { backstack = listOf(0, 1) }
      waitForIdle()
      val firstIdentity = identity("screen-1")
      assertThat(firstIdentity.current).isNotEqualTo(rootIdentity.current)
      assertThat(firstIdentity.previous).isEqualTo(rootIdentity.current)

      runOnIdle { backstack = listOf(0, 1, 2) }
      waitForIdle()
      val secondIdentity = identity("screen-2")
      assertThat(secondIdentity.current).isNotEqualTo(firstIdentity.current)
      assertThat(secondIdentity.previous).isEqualTo(firstIdentity.current)

      mainClock.autoAdvance = false
      onNodeWithTag("screen-2").performClick()
      mainClock.advanceTimeBy(80)
      assertThat(identity("screen-2")).isEqualTo(secondIdentity)
      assertThat(identity("screen-1")).isEqualTo(firstIdentity)
      mainClock.advanceTimeBy(500)
      onNodeWithTag("screen-2").assertDoesNotExist()
      assertThat(identity("screen-1")).isEqualTo(firstIdentity)

      onNodeWithTag("screen-1").performClick()
      mainClock.advanceTimeBy(80)
      assertThat(identity("screen-1")).isEqualTo(firstIdentity)
      assertThat(identity("screen-0")).isEqualTo(rootIdentity)
      mainClock.advanceTimeBy(500)
      onNodeWithTag("screen-1").assertDoesNotExist()
      assertThat(identity("screen-0")).isEqualTo(rootIdentity)
    }

  @Test
  fun `separate navigation hosts have distinct identities for the same entry key`() =
    runDesktopComposeUiTest(width = 400, height = 600) {
      val renderer = DefaultBackstackRenderer()
      setContent {
        Row(Modifier.fillMaxSize()) {
          listOf("first", "second").forEach { host ->
            renderer.PresenterNavDisplay(
              backstack = listOf(0),
              onBack = {},
              modifier = Modifier.weight(1f),
              entryProvider = { key -> entryWithIdentity(key = key, tag = host, onBack = {}) },
            )
          }
        }
      }
      waitForIdle()
      assertThat(identity("first").current).isNotEqualTo(identity("second").current)
    }

  @Test
  fun `forward and back slide short distances and fade in the layout direction`() {
    listOf(LayoutDirection.Ltr, LayoutDirection.Rtl).forEach { layoutDirection ->
      runDesktopComposeUiTest(width = 400, height = 600) {
        val renderer = DefaultBackstackRenderer()
        var backstack by mutableStateOf(listOf(0))
        val onBack = { backstack = backstack.dropLast(1) }
        setContent {
          CompositionLocalProvider(
            LocalDensity provides Density(1f),
            LocalLayoutDirection provides layoutDirection,
          ) {
            Box(Modifier.fillMaxSize().background(Color.Black).testTag("viewport")) {
              renderer.PresenterNavDisplay(
                backstack = backstack,
                onBack = onBack,
                modifier = Modifier.fillMaxSize(),
                entryProvider = { key ->
                  NavEntry(key) {
                    Box(
                      Modifier.fillMaxSize()
                        .background(Color.White)
                        .testTag("screen-$key")
                        .clickable(onClick = onBack),
                    )
                  }
                },
              )
            }
          }
        }
        waitForIdle()
        mainClock.autoAdvance = false
        val direction = if (layoutDirection == LayoutDirection.Ltr) 1 else -1

        runOnIdle { backstack = listOf(0, 1) }
        assertTransition(entering = 1, exiting = 0, direction = direction)

        onNodeWithTag("screen-1").performClick()
        assertTransition(entering = 0, exiting = 1, direction = -direction)
        assertThat(backstack).isEqualTo(listOf(0))
      }
    }
  }

  private fun ComposeUiTest.assertTransition(entering: Int, exiting: Int, direction: Int) {
    mainClock.advanceTimeBy(80)
    assertOffset(entering, direction)
    assertOffset(exiting, -direction)
    assertFaded()

    mainClock.advanceTimeBy(96)
    assertOffset(entering, direction)
    assertFaded()

    mainClock.advanceTimeBy(500)
    assertThat(onNodeWithTag("screen-$entering").getUnclippedBoundsInRoot().left).isEqualTo(0.dp)
    onNodeWithTag("screen-$exiting").assertDoesNotExist()
  }

  private fun ComposeUiTest.assertOffset(screen: Int, direction: Int) {
    val offset = onNodeWithTag("screen-$screen").getUnclippedBoundsInRoot().left * direction
    assertThat(offset).isGreaterThan(0.dp)
    assertThat(offset).isLessThan(31.dp)
  }

  private fun ComposeUiTest.assertFaded() {
    val brightness = onNodeWithTag("viewport").captureToImage().toPixelMap()[200, 300].red
    assertThat(brightness).isGreaterThan(0f)
    assertThat(brightness).isLessThan(1f)
  }

  private fun entryWithIdentity(key: Int, tag: String, onBack: () -> Unit): NavEntry<Int> =
    NavEntry(key) {
      val identity = checkNotNull(LocalNavigationEntryIdentity.current)
      Box(
        Modifier.fillMaxSize()
          .testTag(tag)
          .semantics { this[entryIdentity] = identity }
          .clickable(onClick = onBack),
      )
    }

  private fun ComposeUiTest.identity(tag: String): NavigationEntryIdentity =
    onNodeWithTag(tag).fetchSemanticsNode().config[entryIdentity]

  private companion object {
    val entryIdentity = SemanticsPropertyKey<NavigationEntryIdentity>("NavigationEntryIdentity")
  }
}
