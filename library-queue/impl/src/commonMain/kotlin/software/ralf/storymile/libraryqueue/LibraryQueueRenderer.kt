package software.ralf.storymile.libraryqueue

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import software.ralf.app.platform.inject.ContributesRenderer
import software.ralf.app.platform.renderer.ComposeRenderer
import software.ralf.app.platform.renderer.Render
import software.ralf.storymile.design.PaneDivider
import software.ralf.storymile.episodedetails.LocalEpisodeDetailsPane
import software.ralf.storymile.podcastshows.supportsShowPanes
import software.ralf.storymile.templates.LocalSharedTransitionScope

@ContributesRenderer
class LibraryQueueRenderer : ComposeRenderer<LibraryQueuePresenterImpl.ListModel>() {
  @Composable
  override fun Compose(model: LibraryQueuePresenterImpl.ListModel, modifier: Modifier) {
    val listState = LocalLibraryQueueListState.current ?: rememberLazyListState()
    BoxWithConstraints(modifier.fillMaxSize()) {
      val episodes = model.episodes
      if (episodes == null) {
        CircularProgressIndicator(Modifier.align(Alignment.Center))
      } else {
        val sidePadding = if (maxWidth < 600.dp) 20.dp else 32.dp
        val wide = maxWidth.supportsShowPanes(LocalDensity.current.fontScale)
        val selection = model.selection.takeIf { wide }
        CompositionLocalProvider(
          LocalSharedTransitionScope provides
            LocalSharedTransitionScope.current.takeUnless { wide },
        ) {
          Row(modifier = Modifier.fillMaxSize()) {
            LibraryQueueList(
              episodes = episodes,
              selectedEpisodeKey = selection?.episodeKey,
              onSelect = model.onSelect,
              onReorder = model.onReorder,
              onRemove = model.onRemove,
              state = listState,
              modifier = Modifier.weight(1.25f).fillMaxHeight().padding(horizontal = sidePadding),
            )
            if (selection != null) {
              PaneDivider()
              Box(Modifier.weight(1f).fillMaxHeight()) {
                CompositionLocalProvider(LocalEpisodeDetailsPane provides true) {
                  Render(selection.content)
                }
              }
            }
          }
        }
      }
    }
  }
}
