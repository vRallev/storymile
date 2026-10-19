package software.ralf.storymile.episodedetails

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import software.ralf.storymile.templates.LocalAnimatedVisibilityScope
import software.ralf.storymile.templates.LocalNavigationEntryIdentity
import software.ralf.storymile.templates.LocalSharedTransitionScope

/** Content matched between an episode row and its details screen. */
enum class EpisodeSharedElement {
  /** Episode artwork. */
  Artwork,
  /** Episode title. */
  Title,
  /** Podcast name below the episode title. */
  Subtitle,
  /** Description preview and full text. */
  Description,
  /** Formatted episode duration. */
  Duration;

  internal data class Key(val source: Any, val episode: String, val element: EpisodeSharedElement)
}

/**
 * Animates [element] for [episodeKey] within the current navigation entry. Set [fromPreviousEntry]
 * on details to match the preceding entry's episode row. Does nothing without a matching navigation
 * identity and shared transition scopes.
 */
@Composable
fun Modifier.episodeSharedElement(
  episodeKey: String,
  element: EpisodeSharedElement,
  fromPreviousEntry: Boolean = false,
): Modifier {
  val sharedScope = LocalSharedTransitionScope.current ?: return this
  val visibilityScope = LocalAnimatedVisibilityScope.current ?: return this
  val entry = LocalNavigationEntryIdentity.current ?: return this
  val source = (if (fromPreviousEntry) entry.previous else entry.current) ?: return this
  return with(sharedScope) {
    val state = rememberSharedContentState(EpisodeSharedElement.Key(source, episodeKey, element))
    if (element == EpisodeSharedElement.Artwork) {
      this@episodeSharedElement.sharedElement(
        sharedContentState = state,
        animatedVisibilityScope = visibilityScope,
      )
    } else {
      this@episodeSharedElement.sharedBounds(
          sharedContentState = state,
          animatedVisibilityScope = visibilityScope,
          enter = fadeIn(tween(100)),
          exit = fadeOut(tween(100)),
          resizeMode =
            SharedTransitionScope.ResizeMode.scaleToBounds(
              contentScale =
                if (element == EpisodeSharedElement.Description) {
                  ContentScale.FillWidth
                } else {
                  ContentScale.Fit
                },
              alignment = Alignment.TopStart,
            ),
        )
        .clipToBounds()
    }
  }
}
