package software.ralf.storymile.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import software.ralf.app.platform.inject.ContributesRenderer
import software.ralf.app.platform.renderer.ComposeRenderer
import software.ralf.app.platform.renderer.Render
import software.ralf.storymile.library.LibraryPresenterImpl.Tab
import software.ralf.storymile.podcastshows.supportsShowPanes
import software.ralf.storymile.presenternavigation.DefaultBackstackModel
import software.ralf.storymile.theme.AppTheme

@ContributesRenderer
class LibraryRenderer : ComposeRenderer<LibraryPresenterImpl.Model>() {
  @Composable
  override fun Compose(model: LibraryPresenterImpl.Model, modifier: Modifier) {
    BoxWithConstraints(
      modifier.fillMaxSize().background(AppTheme.colorScheme.background).testTag("library-screen"),
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
              onCloseEpisode = shows.onCloseEpisode,
            ),
          modifier = Modifier.fillMaxSize(),
        )
      } else {
        // The header and tabs leave with the list. The show entry can use the full height.
        val pages =
          LibraryPagesRenderer.Model(
            library = model,
            showsContent = showsList,
          )
        Render(
          model =
            DefaultBackstackModel(
              backstack =
                listOfNotNull(pages, selectedShow.takeIf { model.selectedTab == Tab.SHOWS }),
              onBack = shows.onCloseShow,
            ),
          modifier = Modifier.fillMaxSize(),
        )
      }
    }
  }
}
