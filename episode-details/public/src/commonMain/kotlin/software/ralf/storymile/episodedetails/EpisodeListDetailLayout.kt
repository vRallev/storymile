package software.ralf.storymile.episodedetails

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.renderer.Render
import software.ralf.storymile.design.ListDetailLayout
import software.ralf.storymile.templates.LocalSharedTransitionScope

/**
 * Shows [detail] beside the episode [list] when it fits. Shared navigation transitions apply to the
 * single-pane list.
 */
@Composable
fun EpisodeListDetailLayout(
  loading: Boolean,
  detail: BaseModel?,
  modifier: Modifier = Modifier,
  list: @Composable (modifier: Modifier, showSelection: Boolean) -> Unit,
) {
  ListDetailLayout(
    loading = loading,
    modifier = modifier,
    detail = {
      if (detail != null) {
        CompositionLocalProvider(
          LocalEpisodeDetailsPane provides true,
          LocalSharedTransitionScope provides null,
        ) {
          Render(detail)
        }
      }
    },
  ) { listModifier, detailVisible ->
    CompositionLocalProvider(
      LocalSharedTransitionScope provides
        LocalSharedTransitionScope.current.takeUnless { detailVisible },
    ) {
      list(listModifier, detailVisible)
    }
  }
}
