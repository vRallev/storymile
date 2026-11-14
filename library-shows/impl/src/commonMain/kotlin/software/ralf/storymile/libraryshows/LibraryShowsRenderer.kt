package software.ralf.storymile.libraryshows

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import software.ralf.app.platform.inject.ContributesRenderer
import software.ralf.app.platform.renderer.ComposeRenderer
import software.ralf.storymile.coil.PodcastArtwork
import software.ralf.storymile.design.ListDivider
import software.ralf.storymile.design.PodcastRow
import software.ralf.storymile.library.shows.impl.generated.resources.Res
import software.ralf.storymile.library.shows.impl.generated.resources.followed_shows
import software.ralf.storymile.library.shows.impl.generated.resources.followed_shows_error
import software.ralf.storymile.library.shows.impl.generated.resources.retry
import software.ralf.storymile.libraryshows.LibraryShowsPresenterImpl.FollowedShow
import software.ralf.storymile.theme.AppTheme

@ContributesRenderer
class LibraryShowsRenderer : ComposeRenderer<LibraryShowsPresenterImpl.Model>() {
  @Composable
  override fun Compose(model: LibraryShowsPresenterImpl.Model, modifier: Modifier) {
    val shows = model.shows
    val followedShows = shows?.getOrNull()
    Box(modifier.fillMaxSize()) {
      when {
        shows == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
        followedShows == null ->
          ShowsError(onRetry = model.onRetry, modifier = Modifier.align(Alignment.Center))
        else -> FollowedShows(followedShows)
      }
    }
  }

  @Composable
  private fun FollowedShows(shows: List<FollowedShow>) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
      contentPadding = PaddingValues(vertical = 16.dp),
    ) {
      item {
        Text(
          text = stringResource(Res.string.followed_shows),
          modifier = Modifier.padding(start = 8.dp, bottom = 8.dp).semantics { heading() },
          style = AppTheme.typography.titleLarge,
          fontWeight = FontWeight.SemiBold,
        )
      }
      itemsIndexed(shows, key = { _, show -> show.feedUrl }) { index, show ->
        Column {
          if (index > 0) {
            ListDivider(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
          }
          PodcastRow(title = show.title, author = show.author) { artworkModifier ->
            PodcastArtwork(
              artworkUrl = show.artworkUrl,
              modifier = artworkModifier,
              artworkId = show.feedUrl,
            )
          }
        }
      }
    }
  }

  @Composable
  private fun ShowsError(onRetry: () -> Unit, modifier: Modifier) {
    Column(
      modifier = modifier,
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = stringResource(Res.string.followed_shows_error),
        style = AppTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
      )
      Button(onClick = onRetry) { Text(stringResource(Res.string.retry)) }
    }
  }
}
