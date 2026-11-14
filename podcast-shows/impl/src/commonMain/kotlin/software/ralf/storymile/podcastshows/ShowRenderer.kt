package software.ralf.storymile.podcastshows

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import software.ralf.app.platform.inject.ContributesRenderer
import software.ralf.app.platform.renderer.ComposeRenderer
import software.ralf.storymile.design.TitleBar
import software.ralf.storymile.templates.LocalAnimatedVisibilityScope
import software.ralf.storymile.templates.LocalSharedTransitionScope

internal val LocalShowParentVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

internal val LocalShowPane = compositionLocalOf { false }

@ContributesRenderer
class ShowRenderer : ComposeRenderer<ShowPresenterImpl.Model>() {
  @Composable
  override fun Compose(model: ShowPresenterImpl.Model, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxSize().podcastScreenTransition().clipToBounds()) {
      val wide = maxWidth.supportsShowPanes(LocalDensity.current.fontScale)
      val selectedIndex =
        when (val pane = model.content.pane) {
          is ShowListPane.Discovery ->
            pane.podcasts.indexOfFirst { it.id == model.content.podcast.id }
          is ShowListPane.Saved ->
            pane.shows.indexOfFirst { it.feedUrl == model.content.podcast.id }
        }
      val catalogState =
        rememberLazyListState(
          initialFirstVisibleItemIndex = if (selectedIndex <= 0) 0 else selectedIndex + 1,
        )
      val renderer = remember { ShowBackstackRenderer() }
      Column(
        Modifier.fillMaxSize()
          .then(
            if (wide) Modifier.testTag("show-screen") else Modifier,
          ),
      ) {
        if (wide && model.content.source != ShowSource.Library) {
          TitleBar(
            title = model.content.source.title(),
            onBack = model.onBack,
            backButtonModifier = Modifier.testTag("show-back"),
          )
        }
        CompositionLocalProvider(
          LocalShowPane provides wide,
          LocalShowParentVisibilityScope provides LocalAnimatedVisibilityScope.current,
          LocalSharedTransitionScope provides
            if (wide && model.backstack.size > 1) null else LocalSharedTransitionScope.current,
        ) {
          renderer.RenderBackstack(model, catalogState, Modifier.weight(1f))
        }
      }
    }
  }
}
