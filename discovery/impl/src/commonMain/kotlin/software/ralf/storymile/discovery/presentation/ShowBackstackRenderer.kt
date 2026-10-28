package software.ralf.storymile.discovery.presentation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.PathEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
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

internal class ShowBackstackRenderer : PresenterBackstackRenderer<ShowPresenter.Model>() {
  @Composable
  fun RenderBackstack(model: ShowPresenter.Model, catalogState: LazyListState, modifier: Modifier) {
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
    val distance = with(LocalDensity.current) { 30.dp.roundToPx() }
    val forwardOffset =
      if (LocalLayoutDirection.current == LayoutDirection.Ltr) distance else -distance
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
      transitionSpec = { if (wide) paneTransition() else sharedAxisTransition(forwardOffset) },
      popTransitionSpec = {
        if (wide) paneTransition() else sharedAxisTransition(-forwardOffset)
      },
      predictivePopTransitionSpec = {
        if (wide) paneTransition() else sharedAxisTransition(-forwardOffset)
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

  private fun sharedAxisTransition(offset: Int): ContentTransform {
    return (slideInHorizontally(tween(450, easing = emphasized)) { offset } +
      fadeIn(tween(450, easing = fadeInEasing))) togetherWith
      (slideOutHorizontally(tween(450, easing = emphasized)) { -offset } +
        fadeOut(tween(450, easing = fadeOutEasing)))
  }

  private companion object {
    val LocalCatalog = compositionLocalOf<Catalog?> { null }
    val emphasized =
      PathEasing(
        Path().apply {
          moveTo(0f, 0f)
          cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
          cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
        },
      )
    val fadeInEasing = Easing { ((emphasized.transform(it) - 0.35f) / 0.65f).coerceIn(0f, 1f) }
    val fadeOutEasing = Easing { (emphasized.transform(it) / 0.35f).coerceIn(0f, 1f) }
  }

  private data class Catalog(val model: ShowContentPresenter.Model, val state: LazyListState)

  private data class EntryIdentity(val host: Any, val entryId: Int)
}
