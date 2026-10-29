package software.ralf.storymile.episodedetails

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import software.ralf.app.platform.inject.ContributesRenderer
import software.ralf.app.platform.renderer.ComposeRenderer
import software.ralf.storymile.coil.PodcastArtwork
import software.ralf.storymile.design.TitleBar
import software.ralf.storymile.episode.details.impl.generated.resources.Res
import software.ralf.storymile.episode.details.impl.generated.resources.episode_add_to_queue
import software.ralf.storymile.episode.details.impl.generated.resources.episode_author
import software.ralf.storymile.episode.details.impl.generated.resources.episode_details
import software.ralf.storymile.episode.details.impl.generated.resources.episode_download
import software.ralf.storymile.episode.details.impl.generated.resources.episode_more
import software.ralf.storymile.episode.details.impl.generated.resources.episode_no_description
import software.ralf.storymile.episode.details.impl.generated.resources.episode_play_now
import software.ralf.storymile.episode.details.impl.generated.resources.episode_published
import software.ralf.storymile.episode.details.impl.generated.resources.episode_save
import software.ralf.storymile.episode.details.impl.generated.resources.episode_show_notes
import software.ralf.storymile.templates.LocalAnimatedVisibilityScope
import software.ralf.storymile.theme.AppTheme

@ContributesRenderer
class EpisodeDetailsRenderer : ComposeRenderer<EpisodeDetailsPresenterImpl.Model>() {
  @Composable
  override fun Compose(model: EpisodeDetailsPresenterImpl.Model, modifier: Modifier) {
    BoxWithConstraints(
      modifier
        .fillMaxSize()
        .then(if (LocalEpisodeDetailsPane.current) Modifier else Modifier.screenTransition())
        .testTag("episode-details-screen"),
    ) {
      val scrollState = key(model.details.key) { rememberScrollState() }
      val artworkSize = if (maxHeight < 500.dp) 160.dp else 280.dp
      val padding = if (maxWidth < 600.dp) 16.dp else 32.dp
      Column(Modifier.fillMaxSize()) {
        if (!LocalEpisodeDetailsPane.current) {
          TitleBar(
            title = stringResource(Res.string.episode_details),
            onBack = model.onBack,
            backButtonModifier = Modifier.testTag("episode-details-back"),
          )
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
          Column(
            modifier =
              Modifier.widthIn(max = 800.dp)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .testTag("episode-details-content")
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(20.dp),
          ) {
            PodcastArtwork(
              artworkUrl = model.details.artworkUrl,
              modifier =
                Modifier.align(Alignment.CenterHorizontally)
                  .episodeSharedElement(
                    episodeKey = model.details.key,
                    element = EpisodeSharedElement.Artwork,
                    fromPreviousEntry = true,
                  )
                  .testTag("episode-details-artwork")
                  .widthIn(max = artworkSize)
                  .fillMaxWidth()
                  .aspectRatio(1f),
            )
            Metadata(model)
            Actions(model)
            HorizontalDivider(color = AppTheme.colorScheme.outlineVariant)
            Text(
              text = stringResource(Res.string.episode_show_notes),
              modifier = Modifier.semantics { heading() },
              style = AppTheme.typography.titleMedium,
            )
            Text(
              text =
                model.details.description.ifBlank {
                  stringResource(Res.string.episode_no_description)
                },
              modifier =
                Modifier.episodeSharedElement(
                    episodeKey = model.details.key,
                    element = EpisodeSharedElement.Description,
                    fromPreviousEntry = true,
                  )
                  .testTag("episode-details-description"),
              style = AppTheme.typography.bodyLarge.copy(textMotion = TextMotion.Animated),
            )
          }
        }
      }
    }
  }

  @Composable
  private fun Actions(model: EpisodeDetailsPresenterImpl.Model) {
    Column(
      modifier = Modifier.testTag("episode-details-actions"),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      BoxWithConstraints(Modifier.fillMaxWidth()) {
        val buttonsPerRow = if (maxWidth < 320.dp * LocalDensity.current.fontScale) 1 else 2
        FlowRow(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
          maxItemsInEachRow = buttonsPerRow,
        ) {
          OutlinedButton(
            onClick = model.onPlayNow,
            modifier =
              Modifier.weight(1f)
                .episodeSharedElement(
                  episodeKey = model.details.key,
                  element = EpisodeSharedElement.Play,
                  fromPreviousEntry = true,
                )
                .testTag("episode-details-play"),
            border = BorderStroke(1.dp, AppTheme.colorScheme.primary),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.PlayArrow,
              contentDescription = null,
              modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(Res.string.episode_play_now), textAlign = TextAlign.Center)
          }
          Button(
            onClick = model.onAddToQueue,
            modifier = Modifier.weight(1f).testTag("episode-details-add-to-queue"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
              contentDescription = null,
              modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(Res.string.episode_add_to_queue), textAlign = TextAlign.Center)
          }
        }
      }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        SecondaryAction(
          label = stringResource(Res.string.episode_save),
          icon = Icons.Outlined.BookmarkBorder,
          tag = "episode-details-save",
          onClick = model.onSave,
          modifier = Modifier.weight(1f),
        )
        SecondaryAction(
          label = stringResource(Res.string.episode_download),
          icon = Icons.Outlined.FileDownload,
          tag = "episode-details-download",
          onClick = model.onDownload,
          modifier = Modifier.weight(1f),
        )
        SecondaryAction(
          label = stringResource(Res.string.episode_more),
          icon = Icons.Filled.MoreVert,
          tag = "episode-details-more",
          onClick = model.onMore,
          modifier = Modifier.weight(1f),
        )
      }
    }
  }

  @Composable
  private fun SecondaryAction(
    label: String,
    icon: ImageVector,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier,
  ) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
      OutlinedIconButton(
        onClick = onClick,
        modifier = Modifier.testTag(tag),
        border = BorderStroke(1.dp, AppTheme.colorScheme.outlineVariant),
        colors =
          IconButtonDefaults.outlinedIconButtonColors(contentColor = AppTheme.colorScheme.primary),
      ) {
        Icon(imageVector = icon, contentDescription = label)
      }
      Text(
        text = label,
        modifier = Modifier.clearAndSetSemantics {},
        style = AppTheme.typography.labelMedium,
        color = AppTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
    }
  }

  @Composable
  private fun Metadata(model: EpisodeDetailsPresenterImpl.Model) {
    val details = model.details
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
        text = details.episode.title,
        modifier =
          Modifier.episodeSharedElement(
              episodeKey = model.details.key,
              element = EpisodeSharedElement.Title,
              fromPreviousEntry = true,
            )
            .testTag("episode-details-title")
            .semantics { heading() },
        style = AppTheme.typography.headlineSmall.copy(textMotion = TextMotion.Animated),
      )
      Text(
        text = details.podcastTitle,
        modifier =
          Modifier.episodeSharedElement(
              episodeKey = model.details.key,
              element = EpisodeSharedElement.Subtitle,
              fromPreviousEntry = true,
            )
            .testTag("episode-details-subtitle"),
        style = AppTheme.typography.titleMedium.copy(textMotion = TextMotion.Animated),
      )
      details.duration?.let { duration ->
        Text(
          text = duration,
          modifier =
            Modifier.episodeSharedElement(
                episodeKey = model.details.key,
                element = EpisodeSharedElement.Duration,
                fromPreviousEntry = true,
              )
              .testTag("episode-details-duration"),
          style = AppTheme.typography.bodyMedium.copy(textMotion = TextMotion.Animated),
          color = AppTheme.colorScheme.onSurfaceVariant,
        )
      }
      details.episode.author
        ?.takeIf { it.isNotBlank() }
        ?.let { author ->
          Text(
            text = stringResource(Res.string.episode_author, author),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colorScheme.onSurfaceVariant,
          )
        }
      details.episode.publishedAt
        ?.takeIf { it.isNotBlank() }
        ?.let { date ->
          Text(
            text = stringResource(Res.string.episode_published, date),
            style = AppTheme.typography.bodySmall,
            color = AppTheme.colorScheme.onSurfaceVariant,
          )
        }
    }
  }

  @Composable
  private fun Modifier.screenTransition(): Modifier {
    val visibilityScope = LocalAnimatedVisibilityScope.current
    val transition =
      if (visibilityScope == null) {
        this
      } else {
        with(visibilityScope) {
          animateEnterExit(
            enter = fadeIn(tween(durationMillis = 100, delayMillis = 350)),
            exit = fadeOut(tween(100)),
          )
        }
      }
    return transition.background(AppTheme.colorScheme.background)
  }
}
