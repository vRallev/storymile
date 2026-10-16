@file:OptIn(ExperimentalTestApi::class)

package software.ralf.storymile

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.MouseInjectionScope
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.NavigationEventState
import androidx.navigationevent.compose.rememberNavigationEventState
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isInstanceOf
import kotlin.test.Test

class DesktopBackGestureTest {
  @Test
  fun `both edges preview reverse cancel and then complete exactly once`() {
    listOf(NavigationEvent.EDGE_LEFT, NavigationEvent.EDGE_RIGHT).forEach { edge ->
      runDesktopComposeUiTest(width = 400, height = 400) {
        val fixture = Fixture()
        setContent { fixture.Render() }
        val start = if (edge == NavigationEvent.EDGE_LEFT) 2f else 398f
        val direction = if (edge == NavigationEvent.EDGE_LEFT) 1f else -1f
        mouse {
          moveTo(Offset(start, 200f))
          press()
          moveTo(Offset(start + 140f * direction, 205f))
        }
        runOnIdle {
          assertThat(fixture.event().swipeEdge).isEqualTo(edge)
          assertThat(fixture.event().progress).isCloseTo(0.7f, 0.01f)
          assertThat(fixture.completed).isEqualTo(0)
        }
        mouse { moveTo(Offset(start + 40f * direction, 200f)) }
        runOnIdle { assertThat(fixture.event().progress).isCloseTo(0.2f, 0.01f) }
        mouse { release() }
        runOnIdle {
          assertThat(fixture.cancelled).isEqualTo(1)
          assertThat(fixture.completed).isEqualTo(0)
          assertThat(fixture.clicks).isEqualTo(0)
          assertThat(fixture.state.transitionState).isEqualTo(NavigationEventTransitionState.Idle)
        }

        mouse {
          moveTo(Offset(start, 200f))
          press()
          moveTo(Offset(start + 250f * direction, 200f))
        }
        runOnIdle { assertThat(fixture.event().progress).isEqualTo(1f) }
        mouse { release() }
        runOnIdle {
          assertThat(fixture.completed).isEqualTo(1)
          assertThat(fixture.cancelled).isEqualTo(1)
          assertThat(fixture.clicks).isEqualTo(0)
        }
      }
    }
  }

  @Test
  fun `edge clicks vertical scrolling and non-edge drags still reach content`() =
    runDesktopComposeUiTest(width = 400, height = 400) {
      val fixture = Fixture()
      setContent { fixture.Render() }
      mouse {
        moveTo(Offset(2f, 200f))
        press()
        release()
        moveTo(Offset(398f, 200f))
        press()
        release()
      }
      runOnIdle { assertThat(fixture.clicks).isEqualTo(2) }
      mouse {
        moveTo(Offset(2f, 300f))
        press()
        moveTo(Offset(2f, 100f))
        release()
      }
      runOnIdle { assertThat(fixture.scroll.value).isGreaterThan(0) }
      mouse {
        moveTo(Offset(100f, 200f))
        press()
        moveTo(Offset(300f, 200f))
        release()
      }
      runOnIdle {
        assertThat(fixture.completed).isEqualTo(0)
        assertThat(fixture.cancelled).isEqualTo(0)
        assertThat(fixture.state.transitionState).isEqualTo(NavigationEventTransitionState.Idle)
      }
    }

  @Test
  fun `secondary buttons and disabled back handlers do not start a preview`() =
    runDesktopComposeUiTest(width = 400, height = 400) {
      val fixture = Fixture()
      setContent { fixture.Render() }
      mouse {
        moveTo(Offset(2f, 200f))
        press(MouseButton.Secondary)
        moveTo(Offset(200f, 200f))
        release(MouseButton.Secondary)
      }
      runOnIdle { fixture.enabled = false }
      mouse {
        moveTo(Offset(2f, 200f))
        press()
        moveTo(Offset(200f, 200f))
        release()
      }
      runOnIdle {
        assertThat(fixture.completed).isEqualTo(0)
        assertThat(fixture.cancelled).isEqualTo(0)
        assertThat(fixture.state.transitionState).isEqualTo(NavigationEventTransitionState.Idle)
      }
    }

  @Test
  fun `escape cancels a preview before release`() =
    runDesktopComposeUiTest(width = 400, height = 400) {
      val fixture = Fixture()
      setContent { fixture.Render() }
      mouse {
        moveTo(Offset(2f, 200f))
        press()
        moveTo(Offset(160f, 200f))
      }
      runOnUiThread { onRoot().performKeyInput { pressKey(Key.Escape) } }
      mouse { release() }
      runOnIdle {
        assertThat(fixture.cancelled).isEqualTo(1)
        assertThat(fixture.completed).isEqualTo(0)
      }
    }

  @Test
  fun `focus loss resizing and removal cancel a preview`() {
    listOf<(Fixture) -> Unit>(
        { it.focused = false },
        { it.width = 350 },
        { it.visible = false },
      )
      .forEachIndexed { index, interrupt ->
        runDesktopComposeUiTest(width = 400, height = 400) {
          val fixture = Fixture()
          setContent { fixture.Render() }
          mouse {
            moveTo(Offset(2f, 200f))
            press()
            moveTo(Offset(160f, 200f))
          }
          runOnIdle { interrupt(fixture) }
          waitForIdle()
          mouse { release() }
          runOnIdle {
            assertThat(fixture.cancelled, "interruption $index").isEqualTo(1)
            assertThat(fixture.completed).isEqualTo(0)
          }
        }
      }
  }

  private fun ComposeUiTest.mouse(block: MouseInjectionScope.() -> Unit) {
    runOnUiThread { onRoot().performMouseInput(block) }
    waitForIdle()
  }

  private class Fixture {
    lateinit var state: NavigationEventState<NavigationEventInfo>
    val scroll = ScrollState(0)
    var enabled by mutableStateOf(true)
    var focused by mutableStateOf(true)
    var visible by mutableStateOf(true)
    var width by mutableStateOf(400)
    var completed = 0
    var cancelled = 0
    var clicks = 0

    @Composable
    fun Render() {
      state = rememberNavigationEventState<NavigationEventInfo>(NavigationEventInfo.None)
      NavigationBackHandler(
        state = state,
        isBackEnabled = enabled,
        onBackCancelled = { cancelled++ },
        onBackCompleted = { completed++ },
      )
      val nativeWindow = LocalWindowInfo.current
      val window =
        remember(nativeWindow) {
          object : WindowInfo by nativeWindow {
            override val isWindowFocused: Boolean
              get() = focused
          }
        }
      val focusRequester = remember { FocusRequester() }
      CompositionLocalProvider(LocalWindowInfo provides window) {
        Box(Modifier.requiredSize(width.dp, 400.dp).focusRequester(focusRequester).focusable()) {
          if (visible) {
            DesktopBackGesture {
              Column(
                Modifier.fillMaxSize()
                  .verticalScroll(scroll)
                  .draggable(
                    rememberDraggableState { scroll.dispatchRawDelta(-it) },
                    Orientation.Vertical,
                  )
                  .clickable { clicks++ },
              ) {
                repeat(30) { BasicText("Row $it", Modifier.height(40.dp)) }
              }
            }
          }
        }
      }
      LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }

    fun event(): NavigationEvent {
      assertThat(state.transitionState).isInstanceOf<NavigationEventTransitionState.InProgress>()
      return (state.transitionState as NavigationEventTransitionState.InProgress).latestEvent
    }
  }
}
