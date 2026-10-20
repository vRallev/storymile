package software.ralf.storymile.discovery.presentation

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
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import software.ralf.app.platform.inject.ContributesRenderer
import software.ralf.app.platform.renderer.ComposeRenderer
import software.ralf.storymile.design.TitleBar
import software.ralf.storymile.discovery.impl.generated.resources.Res
import software.ralf.storymile.discovery.impl.generated.resources.discover_podcasts
import software.ralf.storymile.discovery.impl.generated.resources.search_results
import software.ralf.storymile.templates.LocalAnimatedVisibilityScope
import software.ralf.storymile.templates.LocalSharedTransitionScope

internal val LocalShowParentVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

internal val LocalShowPane = compositionLocalOf { false }

internal val ShowPresenter.Source.titleResource
  get() =
    when (this) {
      ShowPresenter.Source.Browse -> Res.string.discover_podcasts
      ShowPresenter.Source.Search -> Res.string.search_results
      is ShowPresenter.Source.Topic -> topic.titleResource
    }

@ContributesRenderer
class ShowRenderer : ComposeRenderer<ShowPresenter.Model>() {
  @Composable
  override fun Compose(model: ShowPresenter.Model, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxSize().discoveryScreenTransition().clipToBounds()) {
      val wide = maxWidth / LocalDensity.current.fontScale >= 760.dp
      val selectedIndex = model.content.podcasts.indexOfFirst { it.id == model.content.podcast.id }
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
        if (wide) {
          TitleBar(
            title = stringResource(model.content.source.titleResource),
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
