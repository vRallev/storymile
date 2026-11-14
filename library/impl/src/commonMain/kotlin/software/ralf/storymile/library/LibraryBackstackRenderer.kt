package software.ralf.storymile.library

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import software.ralf.app.platform.inject.ContributesRenderer
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.backstack.nav3.PresenterBackstackRenderer
import software.ralf.app.platform.renderer.ComposeRenderer
import software.ralf.app.platform.renderer.Render
import software.ralf.storymile.library.LibraryPresenterImpl.NavigationModel
import software.ralf.storymile.show.LocalShowInPane
import software.ralf.storymile.show.ShowPresenter
import software.ralf.storymile.templates.LocalAnimatedVisibilityScope
import software.ralf.storymile.templates.LocalNavigationEntryIdentity
import software.ralf.storymile.templates.NavigationEntryIdentity
import software.ralf.storymile.theme.AppTheme

@ContributesRenderer
class LibraryBackstackRenderer : ComposeRenderer<NavigationModel>() {
  @Composable
  override fun Compose(model: NavigationModel, modifier: Modifier) {
    val renderer = remember { BackstackRenderer() }
    CompositionLocalProvider(
      LocalShowOpen provides (model.backstack.getOrNull(1) is ShowPresenter.Model),
    ) {
      renderer.renderCompose(model, modifier)
    }
  }

  private class BackstackRenderer : PresenterBackstackRenderer<NavigationModel>() {
    @Composable
    override fun PresenterNavDisplay(
      backstack: List<Int>,
      onBack: () -> Unit,
      entryProvider: (Int) -> NavEntry<Int>,
      modifier: Modifier,
    ) {
      val host = remember { Any() }
      BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth / LocalDensity.current.fontScale >= 740.dp
        val allowPane = LocalShowOpen.current
        val strategy =
          remember(wide, allowPane) {
            SceneStrategy<Int> { entries ->
              if (wide && allowPane && entries.size == 2) {
                ShowPaneScene(entries)
              } else {
                null
              }
            }
          }
        NavDisplay(
          backStack = backstack,
          onBack = onBack,
          entryProvider = { id ->
            val entry = entryProvider(id)
            val index = backstack.indexOf(id)
            val identity =
              NavigationEntryIdentity(
                current = EntryIdentity(host = host, id = id),
                previous =
                  backstack.getOrNull(index - 1)?.let { EntryIdentity(host = host, id = it) },
              )
            NavEntry(navEntry = entry) {
              CompositionLocalProvider(LocalNavigationEntryIdentity provides identity) {
                entry.Content()
              }
            }
          },
          modifier = Modifier.fillMaxSize().clipToBounds(),
          sceneStrategies = listOf(strategy),
          transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(350)) },
          popTransitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(350)) },
          predictivePopTransitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(350)) },
        )
      }
    }

    @Composable
    override fun ComposeBackstackEntry(model: BaseModel) {
      CompositionLocalProvider(
        LocalAnimatedVisibilityScope provides LocalNavAnimatedContentScope.current,
      ) {
        Render(model, Modifier.fillMaxSize())
      }
    }
  }

  private companion object {
    val LocalShowOpen = compositionLocalOf { false }
  }

  private data class EntryIdentity(val host: Any, val id: Int)

  private data class ShowPaneScene(override val entries: List<NavEntry<Int>>) : Scene<Int> {
    override val key = entries.last().contentKey
    override val previousEntries = entries.take(1)

    override val content: @Composable () -> Unit = {
      Row(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalShowInPane provides true) {
          Box(Modifier.weight(0.9f).clipToBounds()) { entries.first().Content() }
          VerticalDivider(color = AppTheme.colorScheme.outlineVariant)
          Box(Modifier.weight(1.1f).clipToBounds().testTag("library-show-pane")) {
            entries.last().Content()
          }
        }
      }
    }
  }
}
