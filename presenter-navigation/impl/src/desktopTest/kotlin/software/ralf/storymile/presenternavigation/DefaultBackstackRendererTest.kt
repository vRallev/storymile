@file:OptIn(ExperimentalTestApi::class)

package software.ralf.storymile.presenternavigation

import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.reflect.KClass
import kotlin.test.Test
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.renderer.ComposeRenderer
import software.ralf.app.platform.renderer.LocalRendererFactory
import software.ralf.app.platform.renderer.Renderer
import software.ralf.app.platform.renderer.RendererFactory
import software.ralf.storymile.theme.StorymileColorSchemes

class DefaultBackstackRendererTest {
  @Test
  fun `predictive back keeps the top entry opaque until it is popped`() {
    listOf(StorymileColorSchemes.paperTide, StorymileColorSchemes.harborNight).forEach { colors ->
      listOf(NavigationEvent.EDGE_LEFT, NavigationEvent.EDGE_RIGHT).forEach { edge ->
        runDesktopComposeUiTest(width = 400, height = 600) {
          val fixture = Fixture()
          val renderer = DefaultBackstackRenderer()
          var backstack by mutableStateOf(listOf(Entry(previous = true), Entry(previous = false)))
          setContent {
            CompositionLocalProvider(
              LocalRendererFactory provides fixture,
              LocalNavigationEventDispatcherOwner provides fixture,
            ) {
              DisposableEffect(fixture) {
                fixture.navigationEventDispatcher.addInput(fixture.input)
                onDispose { fixture.navigationEventDispatcher.removeInput(fixture.input) }
              }
              MaterialTheme(colorScheme = colors) {
                // Desktop has no default transitions. Exercise overlapping, scaled entries here.
                NavDisplay(
                  backStack = backstack,
                  onBack = { backstack = backstack.dropLast(1) },
                  predictivePopTransitionSpec = {
                    scaleIn(initialScale = 0.9f) togetherWith scaleOut(targetScale = 0.9f)
                  },
                  entryProvider = { entry ->
                    NavEntry(entry) { renderer.ComposeBackstackEntry(it) }
                  },
                )
              }
            }
          }

          repeat(2) { attempt ->
            runOnIdle { fixture.input.start(edge) }
            listOf(0.1f, 0.5f, 0.9f, 0.3f).forEach { progress ->
              runOnIdle { fixture.input.progress(edge, progress) }
              waitForIdle()
              val pixels = onRoot().captureToImage().toPixelMap()
              assertThat(pixels[pixels.width / 2, pixels.height / 2]).isEqualTo(colors.background)
            }
            runOnIdle {
              if (attempt == 0) {
                fixture.input.cancel()
              } else {
                fixture.input.complete()
              }
            }
            waitForIdle()
            assertThat(backstack.size).isEqualTo(if (attempt == 0) 2 else 1)
            val pixels = onRoot().captureToImage().toPixelMap()
            assertThat(pixels[pixels.width / 2, pixels.height / 2])
              .isEqualTo(if (attempt == 0) colors.background else Color.Magenta)
          }
        }
      }
    }
  }

  private data class Entry(val previous: Boolean) : BaseModel

  private class Fixture : RendererFactory, NavigationEventDispatcherOwner {
    override val navigationEventDispatcher = NavigationEventDispatcher()
    val input = BackInput()
    private val entryRenderer =
      object : ComposeRenderer<Entry>() {
        @Composable
        override fun Compose(model: Entry, modifier: Modifier) {
          if (model.previous) {
            Box(modifier.fillMaxSize().background(Color.Magenta))
          } else {
            Box(modifier.fillMaxSize()) { Box(Modifier.size(24.dp).background(Color.Blue)) }
          }
        }
      }

    @Suppress("UNCHECKED_CAST")
    override fun <T : BaseModel> createRenderer(modelType: KClass<out T>): Renderer<T> {
      check(modelType == Entry::class)
      return entryRenderer as Renderer<T>
    }

    override fun <T : BaseModel> getRenderer(
      modelType: KClass<out T>,
      rendererId: Int,
    ): Renderer<T> = createRenderer(modelType)
  }

  private class BackInput : NavigationEventInput() {
    fun start(edge: Int) = dispatchOnBackStarted(NavigationEvent(swipeEdge = edge))

    fun progress(edge: Int, progress: Float) =
      dispatchOnBackProgressed(NavigationEvent(swipeEdge = edge, progress = progress))

    fun cancel() = dispatchOnBackCancelled()

    fun complete() = dispatchOnBackCompleted()
  }
}
