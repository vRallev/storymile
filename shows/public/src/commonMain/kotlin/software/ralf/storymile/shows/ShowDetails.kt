package software.ralf.storymile.shows

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import software.ralf.storymile.shows.PodcastSharedElement.Companion.podcastSharedElement
import software.ralf.storymile.shows.generated.resources.Res
import software.ralf.storymile.shows.generated.resources.episodes
import software.ralf.storymile.shows.generated.resources.follow_error
import software.ralf.storymile.shows.generated.resources.hide_description
import software.ralf.storymile.shows.generated.resources.no_description
import software.ralf.storymile.shows.generated.resources.show_description
import software.ralf.storymile.theme.AppTheme

/** Displays show metadata, follow controls, and episodes inside its caller's navigation. */
@Composable
fun ShowDetails(
  model: ShowDetailsModel,
  modifier: Modifier = Modifier,
  pane: Boolean = false,
  transitionSource: String? = null,
  stackedArtwork: Boolean = false,
) {
  ShowDetailsLayout(
      pane = pane,
      transitionSource = transitionSource,
      stackedArtwork = stackedArtwork,
    )
    .Content(model, modifier)
}

private class ShowDetailsLayout(
  private val pane: Boolean,
  private val transitionSource: String?,
  private val stackedArtwork: Boolean,
) {
  @Composable
  fun Content(model: ShowDetailsModel, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
      val edgeToEdgeEpisodes = !pane && maxWidth < 600.dp
      val contentModifier = Modifier.padding(horizontal = if (edgeToEdgeEpisodes) 16.dp else 0.dp)
      val detailState = key(model.podcast.id) { rememberLazyListState() }
      Column(Modifier.fillMaxSize()) {
        Row(
          Modifier.weight(1f).padding(horizontal = if (pane || edgeToEdgeEpisodes) 0.dp else 32.dp),
        ) {
          LazyColumn(
            modifier = Modifier.weight(1.35f).testTag("show-detail"),
            state = detailState,
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
          ) {
            item {
              Column(contentModifier) {
                PodcastDetails(model = model, modifier = Modifier.fillMaxWidth())
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
            itemsIndexed(model.episodes.episodes, key = { _, episode -> episode.key }) {
              index,
              episode ->
              EpisodeRow(item = episode, index = index, edgeToEdge = edgeToEdgeEpisodes)
            }
          }
        }
      }
    }
  }

  @Composable
  private fun PodcastDetails(
    model: ShowDetailsModel,
    modifier: Modifier,
  ) {
    val podcast = model.podcast
    val source = transitionSource
    Box {
      BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (!stackedArtwork && (pane || maxWidth / LocalDensity.current.fontScale >= 440.dp)) {
          Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(Modifier.weight(if (pane) 0.6f else 0.8f)) {
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
              model = model,
              modifier = Modifier.weight(if (pane) 1.4f else 1.2f),
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
                .widthIn(max = if (stackedArtwork) 480.dp else 320.dp)
                .fillMaxWidth()
                .aspectRatio(if (stackedArtwork) 16f / 9f else 1f),
            )
            Metadata(model = model, modifier = Modifier.fillMaxWidth())
          }
        }
      }
    }
  }

  @Composable
  private fun Description(model: ShowDetailsModel) {
    Text(
      text = model.podcast.description ?: stringResource(Res.string.no_description),
      modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag("show-description"),
      style = AppTheme.typography.bodyLarge,
      color = AppTheme.colorScheme.onSurfaceVariant,
    )
  }

  @Composable
  private fun Metadata(
    model: ShowDetailsModel,
    modifier: Modifier,
  ) {
    val podcast = model.podcast
    val source = transitionSource
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = podcast.title,
            modifier =
              Modifier.weight(weight = 1f, fill = false)
                .then(if (pane) Modifier.animateContentSize() else Modifier)
                .podcastSharedElement(podcast, source, PodcastSharedElement.Title)
                .testTag("show-title")
                .semantics {
                  heading()
                },
            style = AppTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
          )
          IconToggleButton(
            checked = model.descriptionExpanded,
            onCheckedChange = { model.onToggleDescription() },
            modifier = Modifier.testTag("show-info"),
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
      FollowButton(
        followed = model.followed,
        enabled = model.followEnabled,
        onClick = model.onToggleFollow,
        modifier = Modifier.widthIn(min = 180.dp).testTag("show-follow"),
        compact = pane,
        identity = model.podcast.id,
      )
      if (model.followFailed) {
        Text(
          text = stringResource(Res.string.follow_error),
          modifier = Modifier.testTag("show-follow-error"),
          color = AppTheme.colorScheme.error,
          style = AppTheme.typography.bodyMedium,
        )
      }
    }
  }
}
