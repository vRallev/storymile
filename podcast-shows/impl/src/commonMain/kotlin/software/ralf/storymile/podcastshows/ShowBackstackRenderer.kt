package software.ralf.storymile.podcastshows

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.backstack.nav3.PresenterBackstackRenderer
import software.ralf.app.platform.renderer.Render
import software.ralf.storymile.episodedetails.LocalEpisodeDetailsPane
import software.ralf.storymile.templates.LocalAnimatedVisibilityScope
import software.ralf.storymile.templates.LocalNavigationEntryIdentity
import software.ralf.storymile.templates.NavigationEntryIdentity
import software.ralf.storymile.templates.SharedAxisTransition

internal class ShowBackstackRenderer : PresenterBackstackRenderer<ShowPresenterImpl.Model>() {
  @Composable
  fun RenderBackstack(
    model: ShowPresenterImpl.Model,
    catalogState: LazyListState,
    modifier: Modifier,
  ) {
    CompositionLocalProvider(
      LocalCatalog provides Catalog(model.content, catalogState),
    ) {
      renderCompose(model, modifier)
    }
  }

  @Composable
  override fun PresenterNavDisplay(
    backstack: List<Int>,
    onBack: () -> Unit,
    entryProvider: (Int) -> NavEntry<Int>,
    modifier: Modifier,
  ) {
    val host = remember { Any() }
    val parentIdentity = LocalNavigationEntryIdentity.current
    val wide = LocalShowPane.current
    val currentWide by rememberUpdatedState(wide)
    val catalog = requireNotNull(LocalCatalog.current)
    val currentCatalog = rememberUpdatedState(catalog.model)
    val strategy =
      remember(wide, catalog.state) {
        SceneStrategy<Int> { entries ->
          if (wide) {
            ShowScene(
              entries = entries,
              previousEntries = entries.dropLast(1),
              catalog = currentCatalog,
              catalogState = catalog.state,
            )
          } else {
            null
          }
        }
      }
    val forwardOffset = SharedAxisTransition.forwardOffset()
    NavDisplay(
      backStack = backstack,
      onBack = onBack,
      entryProvider = { entryId ->
        val entry = entryProvider(entryId)
        val identity =
          if (entryId == backstack.first()) {
            parentIdentity
          } else {
            NavigationEntryIdentity(
              current = EntryIdentity(host, entryId),
              previous = parentIdentity?.current,
            )
          }
        NavEntry(navEntry = entry) {
          CompositionLocalProvider(
            LocalNavigationEntryIdentity provides
              if (currentWide && entryId != backstack.first()) null else identity,
            LocalEpisodeDetailsPane provides currentWide,
            LocalShowPane provides currentWide,
          ) {
            entry.Content()
          }
        }
      },
      sceneStrategies = listOf(strategy),
      modifier = modifier.clipToBounds(),
      transitionSpec = {
        if (wide) paneTransition() else SharedAxisTransition.transition(forwardOffset)
      },
      popTransitionSpec = {
        if (wide) paneTransition() else SharedAxisTransition.transition(-forwardOffset)
      },
      predictivePopTransitionSpec = {
        if (wide) paneTransition() else SharedAxisTransition.transition(-forwardOffset)
      },
    )
  }

  @Composable
  override fun ComposeBackstackEntry(model: BaseModel) {
    val navigationScope = LocalNavAnimatedContentScope.current
    val parentScope = LocalShowParentVisibilityScope.current
    val visibilityScope =
      if (model is ShowContentPresenter.Model && LocalShowPane.current) {
        parentScope ?: navigationScope
      } else {
        navigationScope
      }
    CompositionLocalProvider(LocalAnimatedVisibilityScope provides visibilityScope) {
      Box(Modifier.fillMaxSize()) { Render(model) }
    }
  }

  private fun paneTransition() = EnterTransition.None togetherWith ExitTransition.None

  private companion object {
    val LocalCatalog = compositionLocalOf<Catalog?> { null }
  }

  private data class Catalog(val model: ShowContentPresenter.Model, val state: LazyListState)

  private data class EntryIdentity(val host: Any, val entryId: Int)
}
