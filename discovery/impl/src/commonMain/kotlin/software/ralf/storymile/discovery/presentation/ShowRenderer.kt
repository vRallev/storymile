package software.ralf.storymile.discovery.presentation

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import software.ralf.app.platform.inject.ContributesRenderer
import software.ralf.app.platform.renderer.ComposeRenderer
import software.ralf.storymile.design.TitleBar
import software.ralf.storymile.discovery.DiscoveredPodcast
import software.ralf.storymile.discovery.impl.generated.resources.Res
import software.ralf.storymile.discovery.impl.generated.resources.discover_podcasts
import software.ralf.storymile.discovery.impl.generated.resources.episodes
import software.ralf.storymile.discovery.impl.generated.resources.follow
import software.ralf.storymile.discovery.impl.generated.resources.hide_description
import software.ralf.storymile.discovery.impl.generated.resources.no_description
import software.ralf.storymile.discovery.impl.generated.resources.podcasts_to_explore
import software.ralf.storymile.discovery.impl.generated.resources.search_results
import software.ralf.storymile.discovery.impl.generated.resources.show_description
import software.ralf.storymile.discovery.presentation.PodcastSharedElement.Companion.podcastSharedElement
import software.ralf.storymile.theme.AppTheme

@ContributesRenderer
class ShowRenderer : ComposeRenderer<ShowPresenter.Model>() {
  @Composable
  override fun Compose(model: ShowPresenter.Model, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxSize().discoveryScreenTransition().testTag("show-screen")) {
      val wide = maxWidth / LocalDensity.current.fontScale >= 760.dp
      val edgeToEdgeEpisodes = maxWidth < 600.dp
      val contentModifier = Modifier.padding(horizontal = if (edgeToEdgeEpisodes) 16.dp else 0.dp)
      val selectedIndex = model.podcasts.indexOfFirst { it.id == model.podcast.id }.coerceAtLeast(0)
      val listState =
        rememberLazyListState(
          initialFirstVisibleItemIndex = if (selectedIndex == 0) 0 else selectedIndex + 1,
        )
      Column(Modifier.fillMaxSize()) {
        TitleBar(
          title =
            stringResource(
              when (val source = model.source) {
                ShowPresenter.Source.Browse -> Res.string.discover_podcasts
                ShowPresenter.Source.Search -> Res.string.search_results
                is ShowPresenter.Source.Topic -> source.topic.titleResource
              },
            ),
          onBack = model.onBack,
          backButtonModifier = Modifier.testTag("show-back"),
        )
        Row(
          Modifier.weight(1f).padding(horizontal = if (edgeToEdgeEpisodes) 0.dp else 32.dp),
          horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
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
          key(model.podcast.id) {
            EpisodeList(
              model = model,
              edgeToEdgeEpisodes = edgeToEdgeEpisodes,
              contentModifier = contentModifier,
              modifier = Modifier.weight(1.35f),
            )
          }
        }
      }
    }
  }

  @Composable
  private fun EpisodeList(
    model: ShowPresenter.Model,
    edgeToEdgeEpisodes: Boolean,
    contentModifier: Modifier,
    modifier: Modifier,
  ) {
    val detailState = rememberLazyListState()
    var titleVisible by remember { mutableStateOf(true) }
    val titleScrolledAway by remember {
      derivedStateOf { detailState.firstVisibleItemIndex > 0 || !titleVisible }
    }
    val scrollBehavior =
      TopAppBarDefaults.enterAlwaysScrollBehavior(
        state = rememberTopAppBarState(initialHeightOffset = -Float.MAX_VALUE),
      )
    val scrollConnection =
      remember(scrollBehavior) {
        object : NestedScrollConnection by scrollBehavior.nestedScrollConnection {
          // Keep the episodes moving while the title returns above them.
          override fun onPreScroll(available: Offset, source: NestedScrollSource) = Offset.Zero
        }
      }
    Box(modifier.clipToBounds()) {
      LazyColumn(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollConnection).testTag("show-detail"),
        state = detailState,
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
      ) {
        item {
          Column(contentModifier) {
            Box(Modifier.fillMaxWidth()) {
              PodcastDetails(
                podcast = model.podcast,
                source = model.source,
                modifier = Modifier.padding(end = 48.dp),
                titleModifier =
                  Modifier.onGloballyPositioned { titleVisible = it.boundsInRoot().height > 0f },
              )
              IconToggleButton(
                checked = model.descriptionExpanded,
                onCheckedChange = { model.onToggleDescription() },
                modifier = Modifier.align(Alignment.TopEnd).testTag("show-info"),
              ) {
                Icon(
                  imageVector = Icons.Outlined.Info,
                  contentDescription =
                    stringResource(
                      if (model.descriptionExpanded) {
                        Res.string.hide_description
                      } else {
                        Res.string.show_description
                      },
                    ),
                )
              }
            }
            AnimatedVisibility(visible = model.descriptionExpanded) {
              Description(model)
            }
          }
        }
        item {
          HorizontalDivider(
            modifier = contentModifier,
            color = AppTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
          )
        }
        item {
          Column(contentModifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            EpisodesHeading(stringResource(Res.string.episodes))
            EpisodesStatus(model.episodes)
          }
        }
        itemsIndexed(model.episodes.episodes, key = { _, episode -> episode.key }) { index, episode
          ->
          EpisodeRow(item = episode, index = index, edgeToEdge = edgeToEdgeEpisodes)
        }
      }
      Surface(
        modifier =
          Modifier.fillMaxWidth()
            .graphicsLayer {
              translationY =
                if (titleScrolledAway) scrollBehavior.state.heightOffset else -size.height
            }
            .onSizeChanged {
              scrollBehavior.state.heightOffsetLimit = -it.height.toFloat()
              scrollBehavior.state.heightOffset =
                scrollBehavior.state.heightOffset.coerceIn(-it.height.toFloat(), 0f)
            }
            .testTag("show-sticky-title"),
        color = AppTheme.colorScheme.background,
        shadowElevation = 2.dp,
      ) {
        Text(
          text = model.podcast.title,
          modifier = contentModifier.padding(vertical = 16.dp).semantics { heading() },
          style = AppTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }

  @Composable
  private fun PodcastDetails(
    podcast: DiscoveredPodcast,
    source: ShowPresenter.Source,
    modifier: Modifier,
    titleModifier: Modifier,
  ) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
      if (maxWidth / LocalDensity.current.fontScale >= 440.dp) {
        Row(
          modifier = modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(24.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(Modifier.weight(0.8f)) {
            PodcastArtwork(
              podcast,
              Modifier.podcastSharedElement(podcast, source, PodcastSharedElement.Artwork)
                .testTag("show-artwork")
                .widthIn(max = 240.dp)
                .fillMaxWidth()
                .aspectRatio(1f),
            )
          }
          Metadata(
            podcast = podcast,
            source = source,
            modifier = Modifier.weight(1.2f),
            titleModifier = titleModifier,
          )
        }
      } else {
        Column(
          modifier = modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(16.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          PodcastArtwork(
            podcast,
            Modifier.podcastSharedElement(podcast, source, PodcastSharedElement.Artwork)
              .testTag("show-artwork")
              .widthIn(max = 320.dp)
              .fillMaxWidth()
              .aspectRatio(1f),
          )
          Metadata(
            podcast = podcast,
            source = source,
            modifier = Modifier.fillMaxWidth(),
            titleModifier = titleModifier,
          )
        }
      }
    }
  }

  @Composable
  private fun Description(model: ShowPresenter.Model) {
    Text(
      text = model.podcast.description ?: stringResource(Res.string.no_description),
      modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag("show-description"),
      style = AppTheme.typography.bodyLarge,
      color = AppTheme.colorScheme.onSurfaceVariant,
    )
  }

  @Composable
  private fun Metadata(
    podcast: DiscoveredPodcast,
    source: ShowPresenter.Source,
    modifier: Modifier,
    titleModifier: Modifier,
  ) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = podcast.title,
          modifier =
            Modifier.podcastSharedElement(podcast, source, PodcastSharedElement.Title)
              .then(titleModifier)
              .testTag("show-title")
              .semantics {
                heading()
              },
          style = AppTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
        )
        podcast.author?.let { author ->
          Text(
            text = author,
            modifier =
              Modifier.podcastSharedElement(podcast, source, PodcastSharedElement.Author)
                .testTag("show-author"),
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
