package software.ralf.storymile.discovery.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.jetbrains.compose.resources.stringResource
import software.ralf.app.platform.inject.ContributesRenderer
import software.ralf.app.platform.renderer.ComposeRenderer
import software.ralf.storymile.design.TitleBar
import software.ralf.storymile.shows.ShowDetails
import software.ralf.storymile.shows.ShowDetailsModel

@ContributesRenderer
class ShowContentRenderer : ComposeRenderer<ShowContentPresenter.Model>() {
  @Composable
  override fun Compose(model: ShowContentPresenter.Model, modifier: Modifier) {
    val pane = LocalShowPane.current
    Column(
      modifier
        .fillMaxSize()
        .then(if (pane) Modifier else Modifier.discoveryScreenTransition())
        .testTag(if (pane) "show-content" else "show-screen"),
    ) {
      if (!pane) {
        TitleBar(
          title = stringResource(model.source.titleResource),
          onBack = model.onBack,
          backButtonModifier = Modifier.testTag("show-back"),
        )
      }
      ShowDetails(
        model =
          ShowDetailsModel(
            podcast = model.podcast,
            episodes = model.episodes,
            descriptionExpanded = model.descriptionExpanded,
            followed = model.followed,
            followEnabled = model.followEnabled,
            followFailed = model.followFailed,
            onToggleFollow = model.onToggleFollow,
            onToggleDescription = model.onToggleDescription,
          ),
        modifier = Modifier.weight(1f),
        pane = pane,
        transitionSource = model.source.transitionKey,
        headerVisibilityScope = LocalShowParentVisibilityScope.current,
      )
    }
  }
}
