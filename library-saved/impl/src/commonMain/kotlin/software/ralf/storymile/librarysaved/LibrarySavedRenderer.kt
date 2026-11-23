package software.ralf.storymile.librarysaved

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import software.ralf.app.platform.inject.ContributesRenderer
import software.ralf.app.platform.renderer.ComposeRenderer
import software.ralf.storymile.library.LibraryEpisodePanes

@ContributesRenderer
class LibrarySavedRenderer : ComposeRenderer<LibrarySavedPresenterImpl.ListModel>() {
  @Composable
  override fun Compose(model: LibrarySavedPresenterImpl.ListModel, modifier: Modifier) {
    val listState = LocalLibrarySavedListState.current ?: rememberLazyListState()
    LibraryEpisodePanes(
      loading = model.episodes == null,
      detail = model.selection?.content,
      modifier = modifier,
    ) { listModifier, showSelection ->
      LibrarySavedList(
        episodes = model.episodes.orEmpty(),
        selectedEpisodeKey = model.selection?.episodeKey.takeIf { showSelection },
        onSelect = model.onSelect,
        onRemove = model.onRemove,
        state = listState,
        modifier = listModifier,
      )
    }
  }
}
