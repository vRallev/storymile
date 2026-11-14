package software.ralf.storymile.libraryshows

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import software.ralf.app.platform.inject.ContributesRenderer
import software.ralf.app.platform.renderer.ComposeRenderer
import software.ralf.storymile.podcastshows.FollowedShows

@ContributesRenderer
class LibraryShowsRenderer : ComposeRenderer<LibraryShowsPresenterImpl.ListModel>() {
  @Composable
  override fun Compose(model: LibraryShowsPresenterImpl.ListModel, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
      val shows = model.shows
      if (shows == null) {
        CircularProgressIndicator(Modifier.align(Alignment.Center))
      } else {
        Box(Modifier.padding(horizontal = if (maxWidth < 600.dp) 20.dp else 32.dp)) {
          FollowedShows(
            shows = shows,
            onShowSelected = model.onSelect,
            modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
          )
        }
      }
    }
  }
}
