@file:OptIn(InternalComposeUiApi::class)

package software.ralf.storymile.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalPlatformWindowInsets
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.PlatformInsets
import androidx.compose.ui.platform.PlatformWindowInsets
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.cash.turbine.test
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThan
import assertk.assertions.isLessThanOrEqualTo
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest as runCoroutineTest
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.compose.backgesture.BackGestureDispatcherPresenter
import software.ralf.app.platform.renderer.ComposeRenderer
import software.ralf.app.platform.renderer.LocalRendererFactory
import software.ralf.app.platform.renderer.Renderer
import software.ralf.app.platform.renderer.RendererFactory
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.RuntimeModeController
import software.ralf.storymile.screen.DefaultScreenSizeProvider
import software.ralf.storymile.screen.ScreenSize
import software.ralf.storymile.screen.ScreenSizeProvider
import software.ralf.storymile.theme.LocalDarkThemeOverride
import software.ralf.storymile.theme.ThemeEnvironment

class AppTemplateRendererTest {
  @Test
  fun `playback slides up and down while navigation remains available`() {
    listOf(400, 1000).forEach { width ->
      runDesktopComposeUiTest(width = width, height = 900) {
        val fixture = Fixture()
        val populated = template()
        var model by mutableStateOf(populated.copy(playback = null, expandedPlayback = null))
        setContent { fixture.Render(model) }
        onNodeWithTag("playback").assertDoesNotExist()
        val tabs = onNodeWithTag("tabs").getUnclippedBoundsInRoot()
        val content = onNodeWithTag("content").getUnclippedBoundsInRoot()
        mainClock.autoAdvance = false

        runOnIdle { model = populated }
        mainClock.advanceTimeBy(64)
        val entering = onNodeWithTag("playback-sheet").getUnclippedBoundsInRoot().top
        mainClock.advanceTimeBy(1000)
        val shown = onNodeWithTag("playback-sheet").getUnclippedBoundsInRoot().top
        assertThat(entering).isGreaterThan(shown)
        assertThat(entering).isLessThan(900.dp)
        assertThat(onNodeWithTag("tabs").getUnclippedBoundsInRoot()).isEqualTo(tabs)

        runOnIdle { model = populated.copy(playback = null, expandedPlayback = null) }
        mainClock.advanceTimeBy(64)
        val leaving = onNodeWithTag("playback-sheet").getUnclippedBoundsInRoot().top
        assertThat(leaving).isGreaterThan(shown)
        assertThat(leaving).isLessThan(900.dp)
        mainClock.advanceTimeBy(1000)
        onNodeWithTag("playback").assertDoesNotExist()
        assertThat(onNodeWithTag("content").getUnclippedBoundsInRoot()).isEqualTo(content)
        assertThat(onNodeWithTag("tabs").getUnclippedBoundsInRoot()).isEqualTo(tabs)
      }
    }
  }

  @Test
  fun `expanded playback can disappear and an interrupted exit preserves expansion`() {
    listOf(400, 1000).forEach { width ->
      runDesktopComposeUiTest(width = width, height = 900) {
        val fixture = Fixture()
        val populated = template()
        var model by mutableStateOf(populated)
        setContent { fixture.Render(model) }
        onNodeWithTag("playback").performSemanticsAction(SemanticsActions.Expand) { it() }
        assertBounds("playback-screen", left = 0, top = 0, right = width, bottom = 900)
        mainClock.autoAdvance = false

        runOnIdle { model = populated.copy(playback = null, expandedPlayback = null) }
        mainClock.advanceTimeBy(64)
        val leaving = onNodeWithTag("playback-sheet").getUnclippedBoundsInRoot().top
        assertThat(leaving).isGreaterThan(0.dp)
        assertThat(leaving).isLessThan(900.dp)
        runOnIdle { model = populated }
        mainClock.advanceTimeBy(1000)
        assertThat(onNodeWithTag("playback-sheet").getUnclippedBoundsInRoot().top).isEqualTo(0.dp)
        assertBounds("playback-screen", left = 0, top = 0, right = width, bottom = 900)

        mainClock.autoAdvance = true
        runOnIdle { model = populated.copy(playback = null, expandedPlayback = null) }
        onNodeWithTag("playback").assertDoesNotExist()
        onNodeWithTag("playback-screen").assertDoesNotExist()
        runOnIdle { model = populated }
        onNodeWithTag("playback-screen").assertDoesNotExist()
        assertThat(onNodeWithTag("playback-sheet").getUnclippedBoundsInRoot().top)
          .isEqualTo(if (width == 400) 756.dp else 836.dp)
      }
    }
  }

  @Test
  fun `phone surfaces reach the edges while controls avoid cutouts and system bars`() =
    runDesktopComposeUiTest(width = 900, height = 400) {
      val fixture =
        Fixture(
          insets = PlatformInsets(left = 32, top = 24, right = 16, bottom = 32),
          forwardModifiers = false,
        )
      setContent { fixture.Render(template()) }

      assertBounds("tabs", left = 0, top = 288, right = 900, bottom = 400)
      assertBounds("playback", left = 0, top = 224, right = 900, bottom = 400)
      assertBounds("content", left = 32, top = 24, right = 884, bottom = 224)
      assertBounds("playback-content", left = 32, top = 224, right = 884, bottom = 288)

      onNodeWithTag("playback").performSemanticsAction(SemanticsActions.Expand) { it() }
      assertBounds("playback-screen", left = 0, top = 0, right = 900, bottom = 400)
      assertBounds("expanded-content", left = 32, top = 24, right = 884, bottom = 368)

      onNodeWithTag("playback-screen").performSemanticsAction(SemanticsActions.Collapse) { it() }
      assertBounds("playback-content", left = 32, top = 224, right = 884, bottom = 288)
    }

  @Test
  fun `side navigation consumes its inset once and optional chrome keeps content safe`() =
    runDesktopComposeUiTest(width = 1000, height = 600) {
      val fixture =
        Fixture(
          insets = PlatformInsets(left = 32, top = 24, right = 16, bottom = 32),
          forwardModifiers = false,
        )
      var model by mutableStateOf(template().copy(overlay = SlotModel("overlay")))
      setContent { fixture.Render(model) }

      assertBounds("tabs", left = 0, top = 0, right = 128, bottom = 600)
      assertBounds("content", left = 128, top = 24, right = 984, bottom = 504)
      assertBounds("playback-content", left = 32, top = 504, right = 984, bottom = 568)
      assertBounds("overlay", left = 32, top = 24, right = 984, bottom = 568)

      runOnIdle { model = model.copy(tabs = null, overlay = null) }
      assertBounds("content", left = 32, top = 24, right = 984, bottom = 504)
      assertBounds("playback-content", left = 32, top = 504, right = 984, bottom = 568)

      runOnIdle { model = model.copy(playback = null) }
      assertBounds("content", left = 32, top = 24, right = 984, bottom = 568)
    }

  @Test
  fun `phone layers bottom tabs over playback while reserving content space`() =
    runDesktopComposeUiTest(width = 400, height = 900) {
      val fixture = Fixture()
      setContent { fixture.Render(template()) }

      assertBounds("content", left = 0, top = 0, right = 400, bottom = 756)
      assertBounds("playback", left = 0, top = 756, right = 400, bottom = 900)
      assertBounds("tabs", left = 0, top = 820, right = 400, bottom = 900)
      onNodeWithTag("tabs").assertTextEquals("tabs 0 BOTTOM")

      onNodeWithTag("tabs").performTouchInput { click(center) }
      onNodeWithTag("tabs").assertTextEquals("tabs 1 BOTTOM")
      onNodeWithTag("playback-content").assertTextEquals("playback-content 0")
      onNodeWithTag("playback-content").performTouchInput { click(Offset(center.x, 32f)) }
      onNodeWithTag("playback-content").assertTextEquals("playback-content 1")
    }

  @Test
  fun `wide portrait extends the rail behind full width playback`() =
    runDesktopComposeUiTest(width = 600, height = 900) {
      val fixture = Fixture()
      setContent { fixture.Render(template()) }

      assertBounds("tabs", left = 0, top = 0, right = 96, bottom = 900)
      assertBounds("content", left = 96, top = 0, right = 600, bottom = 836)
      assertBounds("playback", left = 0, top = 836, right = 600, bottom = 900)
      onNodeWithTag("tabs").assertTextEquals("tabs 0 START")

      onNodeWithTag("playback-content").performTouchInput { click(Offset(48f, center.y)) }
      onNodeWithTag("playback-content").assertTextEquals("playback-content 1")
      onNodeWithTag("tabs").assertTextEquals("tabs 0 START")
    }

  @Test
  fun `landscape places the rail at logical start in right to left layouts`() =
    runDesktopComposeUiTest(width = 1000, height = 600) {
      val fixture = Fixture()
      setContent { fixture.Render(template(), layoutDirection = LayoutDirection.Rtl) }

      assertBounds("tabs", left = 904, top = 0, right = 1000, bottom = 600)
      assertBounds("content", left = 0, top = 0, right = 904, bottom = 536)
      assertBounds("playback", left = 0, top = 536, right = 1000, bottom = 600)
      onNodeWithTag("tabs").assertTextEquals("tabs 0 START")
    }

  @Test
  fun `optional chrome reserves and releases space without resetting content`() =
    runDesktopComposeUiTest(width = 400, height = 900) {
      val fixture = Fixture()
      var model by mutableStateOf(AppTemplate.AdaptiveTemplate(content = SlotModel("content")))
      setContent { fixture.Render(model) }

      assertBounds("content", left = 0, top = 0, right = 400, bottom = 900)
      onNodeWithTag("content").performClick()
      runOnIdle { model = template() }
      assertBounds("content", left = 0, top = 0, right = 400, bottom = 756)

      runOnIdle { model = model.copy(tabs = null) }
      assertBounds("content", left = 0, top = 0, right = 400, bottom = 836)
      assertBounds("playback", left = 0, top = 836, right = 400, bottom = 900)
      runOnIdle { model = model.copy(playback = null) }
      assertBounds("content", left = 0, top = 0, right = 400, bottom = 900)
      onNodeWithTag("content").assertTextEquals("content 1")
    }

  @Test
  fun `playback stays collapsed without expanded content and returns when it is removed`() =
    runDesktopComposeUiTest(width = 400, height = 900) {
      val fixture = Fixture()
      var model by mutableStateOf(template().copy(expandedPlayback = null))
      setContent { fixture.Render(model) }

      onNodeWithTag("playback")
        .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.Expand))
        .performTouchInput { swipeUp(startY = 32f, endY = -400f, durationMillis = 500) }
      onNodeWithTag("playback-screen").assertDoesNotExist()
      assertBounds("playback", left = 0, top = 756, right = 400, bottom = 900)

      runOnIdle { model = model.copy(expandedPlayback = SlotModel("expanded-content")) }
      onNodeWithTag("playback").performSemanticsAction(SemanticsActions.Expand) { it() }
      assertBounds("playback-screen", left = 0, top = 0, right = 400, bottom = 900)

      runOnIdle { model = model.copy(expandedPlayback = null) }
      onNodeWithTag("playback-screen").assertDoesNotExist()
      assertBounds("playback", left = 0, top = 756, right = 400, bottom = 900)
      assertBounds("content", left = 0, top = 0, right = 400, bottom = 756)
    }

  @Test
  fun `playback corners flatten during drag and fill the expanded screen`() {
    listOf(400, 1440).forEach { width ->
      listOf(false, true).forEach { darkTheme ->
        runDesktopComposeUiTest(width = width, height = 900) {
          val fixture = Fixture()
          setContent {
            CompositionLocalProvider(LocalDarkThemeOverride provides darkTheme) {
              fixture.Render(
                AppTemplate.AdaptiveTemplate(
                  content = SlotModel("content", Color.Blue),
                  tabs = SlotModel("tabs", Color.Blue),
                  playback = SlotModel("playback-content", Color.Green),
                  expandedPlayback = SlotModel("expanded-content", Color.Green),
                ),
              )
            }
          }

          val originalTop = onNodeWithTag("playback-sheet").fetchSemanticsNode().boundsInRoot.top
          val originalCorners = playbackCornerGaps()
          originalCorners.forEach { assertThat(it).isGreaterThan(1) }
          mainClock.autoAdvance = false
          try {
            onNodeWithTag("playback").performTouchInput { down(Offset(center.x, 32f)) }
            mainClock.advanceTimeBy(1000)
            onRoot().performTouchInput { moveBy(Offset(0f, -originalTop / 2)) }
            mainClock.advanceTimeByFrame()
            val halfExpandedCorners = playbackCornerGaps()
            halfExpandedCorners.zip(originalCorners).forEach { (current, original) ->
              assertThat(current).isGreaterThan(1)
              assertThat(current).isLessThan(original)
            }

            onRoot().performTouchInput { moveBy(Offset(0f, originalTop / 4)) }
            mainClock.advanceTimeByFrame()
            playbackCornerGaps().zip(halfExpandedCorners).forEach { (current, previous) ->
              assertThat(current).isGreaterThan(previous)
            }
            onRoot().performTouchInput { moveBy(Offset(0f, originalTop / 2)) }
          } finally {
            onRoot().performTouchInput {
              advanceEventTime(100)
              up()
            }
            mainClock.autoAdvance = true
          }
          playbackCornerGaps().zip(originalCorners).forEach { (current, original) ->
            assertThat(abs(current - original)).isLessThanOrEqualTo(1)
          }

          onNodeWithTag("playback").performSemanticsAction(SemanticsActions.Expand) { it() }
          playbackCornerGaps().forEach { assertThat(it).isLessThanOrEqualTo(1) }
          onNodeWithTag("playback-screen").performSemanticsAction(SemanticsActions.Collapse) {
            it()
          }
          playbackCornerGaps().zip(originalCorners).forEach { (current, original) ->
            assertThat(abs(current - original)).isLessThanOrEqualTo(1)
          }
        }
      }
    }
  }

  @Test
  fun `resizing preserves child state and delivers updated model callbacks`() =
    runDesktopComposeUiTest(width = 800, height = 900) {
      val windowInfo = TestWindowInfo(DpSize(599.dp, 900.dp))
      val fixture = Fixture(windowInfo)
      var originalClicks = 0
      val latestClicks = mutableListOf<String>()
      var model by mutableStateOf(template { originalClicks++ })
      setContent { fixture.Render(model, Modifier.size(windowInfo.containerDpSize)) }

      listOf("content", "tabs", "playback-content").forEach {
        onNodeWithTag(it).performTouchInput { click(Offset(center.x, 32f)) }
      }
      runOnIdle {
        assertThat(originalClicks).isEqualTo(3)
        windowInfo.containerDpSize = DpSize(600.dp, 900.dp)
      }

      onNodeWithTag("content").assertTextEquals("content 1")
      onNodeWithTag("tabs").assertTextEquals("tabs 1 START")
      onNodeWithTag("playback-content").assertTextEquals("playback-content 1")

      runOnIdle { model = template { latestClicks += it } }
      listOf("content", "tabs", "playback-content").forEach {
        onNodeWithTag(it).performTouchInput { click(Offset(center.x, 32f)) }
      }

      onNodeWithTag("content").assertTextEquals("content 2")
      onNodeWithTag("tabs").assertTextEquals("tabs 2 START")
      onNodeWithTag("playback-content").assertTextEquals("playback-content 2")
      runOnIdle {
        assertThat(originalClicks).isEqualTo(3)
        assertThat(latestClicks).isEqualTo(listOf("content", "tabs", "playback"))
        windowInfo.containerDpSize = DpSize(599.dp, 900.dp)
      }
      onNodeWithTag("content").assertTextEquals("content 2")
      onNodeWithTag("tabs").assertTextEquals("tabs 2 BOTTOM")
      onNodeWithTag("playback-content").assertTextEquals("playback-content 2")
      assertBounds("content", left = 0, top = 0, right = 599, bottom = 756)
      assertBounds("playback", left = 0, top = 756, right = 599, bottom = 900)
    }

  @Test
  fun `window resizing and rotation reach observers and update tab placement`() =
    runDesktopComposeUiTest(width = 1200, height = 1200) {
      val windowInfo = TestWindowInfo(DpSize(400.dp, 900.dp))
      val fixture = Fixture(windowInfo)

      runCoroutineTest {
        fixture.screenSizeProvider.screenSize.test {
          assertThat(awaitItem()).isEqualTo(ScreenSize.Zero)
          setContent { fixture.Render(template(), Modifier.size(windowInfo.containerDpSize)) }
          waitForIdle()
          assertThat(awaitItem()).isEqualTo(ScreenSize.from(400.dp, 900.dp))

          listOf(
              DpSize(900.dp, 400.dp) to "BOTTOM",
              DpSize(600.dp, 900.dp) to "START",
              DpSize(700.dp, 900.dp) to "START",
              DpSize(900.dp, 700.dp) to "START",
              DpSize(840.dp, 1200.dp) to "START",
              DpSize(1199.dp, 900.dp) to "START",
              DpSize(1200.dp, 900.dp) to "START_EXPANDED",
              DpSize(1200.dp, 839.dp) to "START_EXPANDED",
              DpSize(1200.dp, 840.dp) to "START_EXPANDED",
              DpSize(1200.dp, 400.dp) to "START_EXPANDED",
              DpSize(1199.dp, 400.dp) to "BOTTOM",
              DpSize(400.dp, 900.dp) to "BOTTOM",
            )
            .forEach { (size, placement) ->
              runOnIdle { windowInfo.containerDpSize = size }
              waitForIdle()
              val screenSize = awaitItem()
              assertThat(screenSize).isEqualTo(ScreenSize.from(size.width, size.height))
              onNodeWithTag("tabs").assertTextEquals("tabs 0 $placement")
            }
        }
      }
    }

  @Test
  fun `overlay covers content playback and tabs without moving them`() =
    runDesktopComposeUiTest(width = 400, height = 900) {
      val fixture = Fixture()
      setContent { fixture.Render(template().copy(overlay = SlotModel("overlay"))) }

      assertBounds("overlay", left = 0, top = 0, right = 400, bottom = 900)
      assertBounds("content", left = 0, top = 0, right = 400, bottom = 756)
      listOf("content", "playback", "tabs").forEach {
        onNodeWithTag(it).performTouchInput { click(center) }
      }

      onNodeWithTag("overlay").assertTextEquals("overlay 3")
      onNodeWithTag("content").assertTextEquals("content 0")
      onNodeWithTag("playback-content").assertTextEquals("playback-content 0")
      onNodeWithTag("tabs").assertTextEquals("tabs 0 BOTTOM")
    }

  @Test
  fun `expanded playback survives rotation and cancelled back before returning to its bar`() =
    runDesktopComposeUiTest(width = 1200, height = 1200) {
      val windowInfo = TestWindowInfo(DpSize(400.dp, 900.dp))
      val fixture = Fixture(windowInfo)
      setContent { fixture.Render(template(), Modifier.size(windowInfo.containerDpSize)) }
      onNodeWithTag("content").performClick()
      onNodeWithTag("playback-content").performClick()
      onNodeWithTag("playback").performSemanticsAction(SemanticsActions.Expand) { it() }
      assertBounds("playback-screen", left = 0, top = 0, right = 400, bottom = 900)
      onNodeWithTag("expanded-content").assertTextEquals("expanded-content 0")

      runOnIdle { windowInfo.containerDpSize = DpSize(900.dp, 700.dp) }
      assertBounds("playback-screen", left = 0, top = 0, right = 900, bottom = 700)
      runCoroutineTest {
        assertFailure {
          fixture.backGestureDispatcher.onPredictiveBack(flow { throw CancellationException() })
        }
          .isInstanceOf<CancellationException>()
      }
      assertBounds("playback-screen", left = 0, top = 0, right = 900, bottom = 700)

      runCoroutineTest { fixture.backGestureDispatcher.onPredictiveBack(emptyFlow()) }
      onNodeWithTag("playback-screen").assertDoesNotExist()
      assertBounds("playback", left = 0, top = 636, right = 900, bottom = 700)
      onNodeWithTag("content").assertTextEquals("content 1")
      onNodeWithTag("playback-content").assertTextEquals("playback-content 1")
    }

  private fun template(onClick: (String) -> Unit = {}): AppTemplate.AdaptiveTemplate =
    AppTemplate.AdaptiveTemplate(
      content = SlotModel("content") { onClick("content") },
      tabs = SlotModel("tabs") { onClick("tabs") },
      playback = SlotModel("playback-content") { onClick("playback") },
      expandedPlayback = SlotModel("expanded-content") { onClick("expanded") },
    )

  private fun ComposeUiTest.assertBounds(
    tag: String,
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
  ) {
    assertThat(onNodeWithTag(tag).getUnclippedBoundsInRoot())
      .isEqualTo(DpRect(left.dp, top.dp, right.dp, bottom.dp))
  }

  private fun ComposeUiTest.playbackCornerGaps(): List<Int> {
    val top = onNodeWithTag("playback-sheet").fetchSemanticsNode().boundsInRoot.top
    val pixels = onRoot().captureToImage().toPixelMap()
    val y = ceil(top).toInt() + 3
    // Detect slot coverage, allowing for the border, antialiasing, and pressed ripple.
    fun covered(x: Int): Boolean {
      val color = pixels[x, y]
      return color.green > color.red + 0.25f && color.green > color.blue + 0.25f
    }
    return listOf(
      (0 until pixels.width / 2).first { covered(it) },
      (0 until pixels.width / 2).first { covered(pixels.width - 1 - it) },
    )
  }

  private data class SlotModel(
    val name: String,
    val color: Color? = null,
    val onClick: () -> Unit = {},
  ) : BaseModel

  private class TestWindowInfo(initialSize: DpSize) : WindowInfo {
    override val isWindowFocused = true
    override var containerDpSize by mutableStateOf(initialSize)
  }

  private class Fixture(
    private val windowInfo: WindowInfo? = null,
    insets: PlatformInsets = PlatformInsets.Zero,
    forwardModifiers: Boolean = true,
  ) : RendererFactory {
    private val windowInsets =
      object : PlatformWindowInsets {
        override val systemBars = insets
      }
    private val mutableScreenSizeProvider = DefaultScreenSizeProvider()
    val screenSizeProvider: ScreenSizeProvider = mutableScreenSizeProvider
    val backGestureDispatcher = BackGestureDispatcherPresenter.createNewInstance()
    private val templateRenderer =
      AppTemplateRenderer(
        backGestureDispatcher,
        mutableScreenSizeProvider,
        object : ThemeEnvironment {
          @Composable
          override fun Provide(darkTheme: Boolean, content: @Composable () -> Unit) {
            content()
          }
        },
        object : RuntimeModeController {
          override val mode: RuntimeMode
            get() = error("Not used")

          override suspend fun switchMode(mode: RuntimeMode): Unit = error("Not used")
        },
      )
    private val slotRenderer = SlotRenderer(forwardModifiers)

    @Composable
    fun Render(
      template: AppTemplate,
      modifier: Modifier = Modifier,
      layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ) {
      CompositionLocalProvider(
        LocalRendererFactory provides this,
        LocalDensity provides Density(1f),
        LocalLayoutDirection provides layoutDirection,
        LocalPlatformWindowInsets provides windowInsets,
        LocalWindowInfo provides (windowInfo ?: LocalWindowInfo.current),
      ) {
        Box(modifier) { templateRenderer.renderCompose(template) }
      }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : BaseModel> createRenderer(modelType: KClass<out T>): Renderer<T> {
      check(modelType == SlotModel::class)
      return slotRenderer as Renderer<T>
    }

    override fun <T : BaseModel> getRenderer(
      modelType: KClass<out T>,
      rendererId: Int,
    ): Renderer<T> = createRenderer(modelType)
  }

  private class SlotRenderer(private val forwardModifiers: Boolean) : ComposeRenderer<SlotModel>() {
    @Composable
    override fun Compose(model: SlotModel, modifier: Modifier) {
      var clicks by remember { mutableIntStateOf(0) }
      val placement = LocalTabPlacement.current
      val slotSize =
        when {
          model.name == "playback-content" -> Modifier.fillMaxWidth().height(64.dp)
          model.name == "tabs" && placement == TabPlacement.BOTTOM ->
            Modifier.fillMaxWidth().height(80.dp)
          model.name == "tabs" ->
            Modifier.width(if (placement == TabPlacement.START_EXPANDED) 280.dp else 96.dp)
              .fillMaxHeight()
          else -> Modifier.fillMaxSize()
        }
      Box(
        (if (forwardModifiers) modifier else Modifier).testTag(model.name).clickable {
          clicks++
          model.onClick()
        },
      ) {
        val insets =
          if (model.name == "tabs") {
            WindowInsets.safeDrawing.only(
              if (placement == TabPlacement.BOTTOM) {
                WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
              } else {
                WindowInsetsSides.Start + WindowInsetsSides.Vertical
              },
            )
          } else {
            WindowInsets(0, 0, 0, 0)
          }
        Box(
          Modifier.windowInsetsPadding(insets)
            .then(slotSize)
            .then(model.color?.let { Modifier.background(it) } ?: Modifier),
        ) {
          if (model.color == null) {
            val suffix = if (model.name == "tabs") " ${placement.name}" else ""
            Text("${model.name} $clicks$suffix")
          }
        }
      }
    }
  }
}
