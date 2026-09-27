@file:OptIn(ExperimentalTestApi::class)

package software.ralf.storymile.templates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.reflect.KClass
import kotlin.test.Test
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.compose.backgesture.BackGestureDispatcherPresenter
import software.ralf.app.platform.renderer.ComposeRenderer
import software.ralf.app.platform.renderer.LocalRendererFactory
import software.ralf.app.platform.renderer.Renderer
import software.ralf.app.platform.renderer.RendererFactory
import software.ralf.storymile.screen.DefaultScreenSizeProvider

class AppTemplateRendererTest {
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
      onNodeWithTag("playback").assertTextEquals("playback 0")
      onNodeWithTag("playback").performTouchInput { click(Offset(center.x, 32f)) }
      onNodeWithTag("playback").assertTextEquals("playback 1")
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

      onNodeWithTag("playback").performTouchInput { click(Offset(48f, center.y)) }
      onNodeWithTag("playback").assertTextEquals("playback 1")
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
  fun `resizing preserves child state and delivers updated model callbacks`() =
    runDesktopComposeUiTest(width = 800, height = 900) {
      val fixture = Fixture()
      var width by mutableStateOf(599.dp)
      var originalClicks = 0
      val latestClicks = mutableListOf<String>()
      var model by mutableStateOf(template { originalClicks++ })
      setContent { fixture.Render(model, Modifier.size(width, 900.dp)) }

      listOf("content", "tabs", "playback").forEach {
        onNodeWithTag(it).performTouchInput { click(Offset(center.x, 32f)) }
      }
      runOnIdle {
        assertThat(originalClicks).isEqualTo(3)
        width = 600.dp
      }

      onNodeWithTag("content").assertTextEquals("content 1")
      onNodeWithTag("tabs").assertTextEquals("tabs 1 START")
      onNodeWithTag("playback").assertTextEquals("playback 1")

      runOnIdle { model = template { latestClicks += it } }
      listOf("content", "tabs", "playback").forEach {
        onNodeWithTag(it).performTouchInput { click(Offset(center.x, 32f)) }
      }

      onNodeWithTag("content").assertTextEquals("content 2")
      onNodeWithTag("tabs").assertTextEquals("tabs 2 START")
      onNodeWithTag("playback").assertTextEquals("playback 2")
      runOnIdle {
        assertThat(originalClicks).isEqualTo(3)
        assertThat(latestClicks).isEqualTo(listOf("content", "tabs", "playback"))
        width = 599.dp
      }
      onNodeWithTag("content").assertTextEquals("content 2")
      onNodeWithTag("tabs").assertTextEquals("tabs 2 BOTTOM")
      onNodeWithTag("playback").assertTextEquals("playback 2")
      assertBounds("content", left = 0, top = 0, right = 599, bottom = 756)
      assertBounds("playback", left = 0, top = 756, right = 599, bottom = 900)
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
      onNodeWithTag("playback").assertTextEquals("playback 0")
      onNodeWithTag("tabs").assertTextEquals("tabs 0 BOTTOM")
    }

  private fun template(onClick: (String) -> Unit = {}): AppTemplate.AdaptiveTemplate =
    AppTemplate.AdaptiveTemplate(
      content = SlotModel("content") { onClick("content") },
      tabs = SlotModel("tabs") { onClick("tabs") },
      playback = SlotModel("playback") { onClick("playback") },
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

  private data class SlotModel(val name: String, val onClick: () -> Unit = {}) : BaseModel

  private class Fixture : RendererFactory {
    private val templateRenderer =
      AppTemplateRenderer(
        BackGestureDispatcherPresenter.createNewInstance(),
        DefaultScreenSizeProvider(),
      )
    private val slotRenderer = SlotRenderer()

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

  private class SlotRenderer : ComposeRenderer<SlotModel>() {
    @Composable
    override fun Compose(model: SlotModel, modifier: Modifier) {
      var clicks by remember { mutableIntStateOf(0) }
      val placement = LocalTabPlacement.current
      val slotSize =
        when {
          model.name == "playback" ->
            Modifier.fillMaxWidth().height(64.dp + LocalPlaybackBottomInset.current)
          model.name == "tabs" && placement == TabPlacement.BOTTOM ->
            Modifier.fillMaxWidth().height(80.dp)
          model.name == "tabs" -> Modifier.width(96.dp).fillMaxHeight()
          else -> Modifier.fillMaxSize()
        }
      Box(
        modifier.then(slotSize).testTag(model.name).clickable {
          clicks++
          model.onClick()
        }
      ) {
        val suffix = if (model.name == "tabs") " ${placement.name}" else ""
        Text("${model.name} $clicks$suffix")
      }
    }
  }
}
