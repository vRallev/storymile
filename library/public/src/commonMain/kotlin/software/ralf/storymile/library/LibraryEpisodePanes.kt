package software.ralf.storymile.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.renderer.Render
import software.ralf.storymile.design.PaneDivider
import software.ralf.storymile.episodedetails.LocalEpisodeDetailsPane
import software.ralf.storymile.podcastshows.supportsShowPanes
import software.ralf.storymile.templates.LocalSharedTransitionScope

/**
 * Renders the Library episode list beside [detail] when both panes fit. The [list] receives its
 * layout modifier and whether to highlight the selection. A loading list shows a progress
 * indicator.
 */
@Composable
fun LibraryEpisodePanes(
  loading: Boolean,
  detail: BaseModel?,
  modifier: Modifier = Modifier,
  list: @Composable (modifier: Modifier, showSelection: Boolean) -> Unit,
) {
  BoxWithConstraints(modifier.fillMaxSize()) {
    if (loading) {
      CircularProgressIndicator(Modifier.align(Alignment.Center))
    } else {
      val sidePadding = if (maxWidth < 600.dp) 20.dp else 32.dp
      val wide = maxWidth.supportsShowPanes(LocalDensity.current.fontScale)
      CompositionLocalProvider(
        LocalSharedTransitionScope provides LocalSharedTransitionScope.current.takeUnless { wide },
      ) {
        Row(Modifier.fillMaxSize()) {
          list(Modifier.weight(1.25f).fillMaxHeight().padding(horizontal = sidePadding), wide)
          if (wide) {
            PaneDivider()
            Box(Modifier.weight(1f).fillMaxHeight()) {
              if (detail != null) {
                CompositionLocalProvider(LocalEpisodeDetailsPane provides true) { Render(detail) }
              }
            }
          }
        }
      }
    }
  }
}
