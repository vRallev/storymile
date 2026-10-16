package software.ralf.storymile.discovery.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import software.ralf.app.platform.inject.ContributesRenderer
import software.ralf.app.platform.renderer.ComposeRenderer
import software.ralf.storymile.discovery.DiscoveredPodcast
import software.ralf.storymile.discovery.impl.generated.resources.Res
import software.ralf.storymile.discovery.impl.generated.resources.discover_podcasts
import software.ralf.storymile.discovery.impl.generated.resources.episodes
import software.ralf.storymile.discovery.impl.generated.resources.follow
import software.ralf.storymile.discovery.impl.generated.resources.podcasts_to_explore
import software.ralf.storymile.discovery.impl.generated.resources.search_results
import software.ralf.storymile.theme.AppTheme

@ContributesRenderer
class ShowRenderer : ComposeRenderer<ShowPresenter.Model>() {
  @Composable
  override fun Compose(model: ShowPresenter.Model, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxSize().discoveryScreenTransition().testTag("show-screen")) {
      val wide = maxWidth / LocalDensity.current.fontScale >= 760.dp
      val padding = if (maxWidth < 600.dp) 16.dp else 32.dp
      val selectedIndex = model.podcasts.indexOfFirst { it.id == model.podcast.id }.coerceAtLeast(0)
      val listState =
        rememberLazyListState(
          initialFirstVisibleItemIndex = if (selectedIndex == 0) 0 else selectedIndex + 1,
        )
      val detailState = rememberLazyListState()
      Column(Modifier.fillMaxSize().padding(horizontal = padding)) {
        DiscoveryHeader(
          title =
            stringResource(
              when (val source = model.source) {
                ShowPresenter.Source.Browse -> Res.string.discover_podcasts
                ShowPresenter.Source.Search -> Res.string.search_results
                is ShowPresenter.Source.Topic -> source.topic.titleResource
              },
            ),
          backTag = "show-back",
          onBack = model.onBack,
        )
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
          if (wide) {
            LazyColumn(
              modifier = Modifier.weight(0.85f).testTag("show-list"),
              state = listState,
              contentPadding = PaddingValues(vertical = 16.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              item {
                Text(
                  text = stringResource(Res.string.podcasts_to_explore),
                  modifier = Modifier.padding(bottom = 8.dp).semantics { heading() },
                  style = AppTheme.typography.titleLarge,
                  fontWeight = FontWeight.SemiBold,
                )
              }
              items(model.podcasts, key = { it.id }) { podcast ->
                PodcastRow(
                  podcast = podcast,
                  onClick = { model.onPodcastSelected(podcast) },
                  selected = model.podcast.id == podcast.id,
                )
              }
            }
            VerticalDivider(color = AppTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
          }
          LazyColumn(
            modifier = Modifier.weight(1.35f).testTag("show-detail"),
            state = detailState,
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
          ) {
            item { PodcastDetails(model.podcast, model.source) }
            item {
              HorizontalDivider(color = AppTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
            item {
              SampleEpisodes(
                title = stringResource(Res.string.episodes),
                podcasts = List(3) { model.podcast },
                modifier = Modifier.fillMaxWidth(),
              )
            }
          }
        }
      }
      LaunchedEffect(model.podcast.id) { detailState.scrollToItem(0) }
    }
  }

  @Composable
  private fun PodcastDetails(podcast: DiscoveredPodcast, source: ShowPresenter.Source) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
      if (maxWidth / LocalDensity.current.fontScale >= 440.dp) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(24.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(Modifier.weight(0.8f)) {
            PodcastArtwork(
              podcast,
              PodcastSharedElement.Artwork.modifier(podcast, source)
                .testTag("show-artwork")
                .widthIn(max = 240.dp)
                .fillMaxWidth()
                .aspectRatio(1f),
            )
          }
          Metadata(podcast, source, Modifier.weight(1.2f))
        }
      } else {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(16.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          PodcastArtwork(
            podcast,
            PodcastSharedElement.Artwork.modifier(podcast, source)
              .testTag("show-artwork")
              .widthIn(max = 320.dp)
              .fillMaxWidth()
              .aspectRatio(1f),
          )
          Metadata(podcast, source, Modifier.fillMaxWidth())
        }
      }
    }
  }

  @Composable
  private fun Metadata(
    podcast: DiscoveredPodcast,
    source: ShowPresenter.Source,
    modifier: Modifier,
  ) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = podcast.title,
          modifier =
            PodcastSharedElement.Title.modifier(podcast, source).testTag("show-title").semantics {
              heading()
            },
          style = AppTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
        )
        podcast.author?.let { author ->
          Text(
            text = author,
            modifier = PodcastSharedElement.Author.modifier(podcast, source).testTag("show-author"),
            style = AppTheme.typography.bodyLarge,
            color = AppTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      Button(
        onClick = {},
        modifier = Modifier.widthIn(min = 180.dp).testTag("show-follow"),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
      ) {
        Icon(Icons.Filled.Add, null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(Res.string.follow))
      }
    }
  }
}
