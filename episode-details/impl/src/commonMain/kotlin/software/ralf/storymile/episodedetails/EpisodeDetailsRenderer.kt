package software.ralf.storymile.episodedetails

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import software.ralf.app.platform.inject.ContributesRenderer
import software.ralf.app.platform.renderer.ComposeRenderer
import software.ralf.storymile.design.PodcastArtwork
import software.ralf.storymile.design.TitleBar
import software.ralf.storymile.episode.details.impl.generated.resources.Res
import software.ralf.storymile.episode.details.impl.generated.resources.episode_author
import software.ralf.storymile.episode.details.impl.generated.resources.episode_details
import software.ralf.storymile.episode.details.impl.generated.resources.episode_no_description
import software.ralf.storymile.episode.details.impl.generated.resources.episode_published
import software.ralf.storymile.screen.LocalScreenSize
import software.ralf.storymile.screen.ScreenSize
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
      val phone = LocalScreenSize.current.category == ScreenSize.Category.PHONE
      val padding = if (phone) 16.dp else 32.dp
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
