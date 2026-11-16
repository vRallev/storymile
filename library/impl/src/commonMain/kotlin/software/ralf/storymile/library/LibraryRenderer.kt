package software.ralf.storymile.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.stringResource
import software.ralf.app.platform.inject.ContributesRenderer
import software.ralf.app.platform.renderer.ComposeRenderer
import software.ralf.app.platform.renderer.Render
import software.ralf.storymile.library.LibraryPresenterImpl.Tab
import software.ralf.storymile.library.impl.generated.resources.Res
import software.ralf.storymile.library.impl.generated.resources.library_queue_removed
import software.ralf.storymile.library.impl.generated.resources.library_saved_removed
import software.ralf.storymile.library.impl.generated.resources.library_undo
import software.ralf.storymile.libraryqueue.LocalLibraryQueueListState
import software.ralf.storymile.librarysaved.LocalLibrarySavedListState
import software.ralf.storymile.podcastshows.supportsShowPanes
import software.ralf.storymile.presenternavigation.DefaultBackstackModel
import software.ralf.storymile.theme.AppTheme

@ContributesRenderer
class LibraryRenderer : ComposeRenderer<LibraryPresenterImpl.Model>() {
  @Composable
  override fun Compose(model: LibraryPresenterImpl.Model, modifier: Modifier) {
    CompositionLocalProvider(
      LocalLibraryQueueListState provides rememberLazyListState(),
      LocalLibrarySavedListState provides rememberLazyListState(),
    ) {
      BoxWithConstraints(
        modifier
          .fillMaxSize()
          .background(AppTheme.colorScheme.background)
          .testTag("library-screen"),
      ) {
        val shows = model.shows
        val selectedShow = shows.selectedShow
        val showsList = shows.list.takeUnless { shows.isEmpty }
        if (maxWidth.supportsShowPanes(LocalDensity.current.fontScale)) {
          if (model.selectedTab == Tab.SHOWS) {
            val openFirstShow by rememberUpdatedState(shows.onOpenFirstShow)
            LaunchedEffect(Unit) {
              snapshotFlow { openFirstShow }.filterNotNull().first().invoke()
            }
          }
          Render(
            model =
              LibraryPagesRenderer.Model(
                library = model,
                showsContent = selectedShow ?: showsList,
                onCloseShowEpisode = shows.onCloseEpisode,
              ),
            modifier = Modifier.fillMaxSize(),
          )
        } else {
          // The header and tabs leave with the list so details can use the full height.
          val pages =
            LibraryPagesRenderer.Model(
              library = model,
              showsContent = showsList,
            )
          val details =
            when (model.selectedTab) {
              Tab.QUEUE -> model.queue.openedEpisode
              Tab.SHOWS -> selectedShow
              Tab.SAVED -> model.saved.openedEpisode
              else -> null
            }
          Render(
            model =
              DefaultBackstackModel(
                backstack = listOfNotNull(pages, details),
                onBack =
                  when (model.selectedTab) {
                    Tab.QUEUE -> model.queue.onCloseEpisode
                    Tab.SAVED -> model.saved.onCloseEpisode
                    else -> shows.onCloseShow
                  },
              ),
            modifier = Modifier.fillMaxSize(),
          )
        }
        val snackbarHost = remember { SnackbarHostState() }
        RemovalSnackbarEffect(
          removal = model.queue.removal,
          host = snackbarHost,
          message = stringResource(Res.string.library_queue_removed),
        )
        RemovalSnackbarEffect(
          removal = model.saved.removal,
          host = snackbarHost,
          message = stringResource(Res.string.library_saved_removed),
        )
        SnackbarHost(
          hostState = snackbarHost,
          modifier = Modifier.align(Alignment.BottomCenter).widthIn(max = 560.dp),
        )
      }
    }
  }

  @Composable
  private fun RemovalSnackbarEffect(
    removal: LibraryRemovalUndo.Removal?,
    host: SnackbarHostState,
    message: String,
  ) {
    val undo = stringResource(Res.string.library_undo)
    LaunchedEffect(removal) {
      if (removal == null) {
        return@LaunchedEffect
      }
      host.currentSnackbarData?.dismiss()
      try {
        val result =
          host.showSnackbar(message = message, actionLabel = undo, duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) {
          removal.undo()
        }
      } finally {
        removal.dismiss()
      }
    }
  }
}
