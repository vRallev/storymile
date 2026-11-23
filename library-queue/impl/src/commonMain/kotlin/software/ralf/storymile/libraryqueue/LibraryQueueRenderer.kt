package software.ralf.storymile.libraryqueue

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import software.ralf.app.platform.inject.ContributesRenderer
import software.ralf.app.platform.renderer.ComposeRenderer
import software.ralf.storymile.library.LibraryEpisodePanes

@ContributesRenderer
class LibraryQueueRenderer : ComposeRenderer<LibraryQueuePresenterImpl.ListModel>() {
  @Composable
  override fun Compose(model: LibraryQueuePresenterImpl.ListModel, modifier: Modifier) {
    val listState = LocalLibraryQueueListState.current ?: rememberLazyListState()
    LibraryEpisodePanes(
      loading = model.episodes == null,
      detail = model.selection?.content,
      modifier = modifier,
    ) { listModifier, showSelection ->
      LibraryQueueList(
        episodes = model.episodes.orEmpty(),
        selectedEpisodeKey = model.selection?.episodeKey.takeIf { showSelection },
        onSelect = model.onSelect,
        onReorder = model.onReorder,
        onRemove = model.onRemove,
        state = listState,
        modifier = listModifier,
      )
    }
  }
}
