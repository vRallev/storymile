package software.ralf.storymile.shows

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import software.ralf.storymile.discovery.DiscoveredPodcast
import software.ralf.storymile.templates.LocalAnimatedVisibilityScope
import software.ralf.storymile.templates.LocalSharedTransitionScope

/** Elements matched between a catalog row and its show header. */
enum class PodcastSharedElement {
  /** The show cover. */
  Artwork,
  /** The show title. */
  Title,
  /** The publisher name. */
  Author;

  /** Shared transition helpers. */
  companion object {
    /** Matches only elements from the same source; null disables matching. */
    @Composable
    fun Modifier.podcastSharedElement(
      podcast: DiscoveredPodcast,
      source: String?,
      element: PodcastSharedElement,
    ): Modifier {
      // Sidebar rows and episode artwork must not compete with the show header for a match.
      if (source == null) {
        return this
      }
      val sharedScope = LocalSharedTransitionScope.current ?: return this
      val visibilityScope = LocalAnimatedVisibilityScope.current ?: return this
      return with(sharedScope) {
        val state = rememberSharedContentState(Key(podcast.id, source, element))
        if (element == Artwork) {
          this@podcastSharedElement.sharedElement(
            sharedContentState = state,
            animatedVisibilityScope = visibilityScope,
            boundsTransform = { _, _ -> tween(350) },
          )
        } else {
          this@podcastSharedElement.sharedBounds(
            sharedContentState = state,
            animatedVisibilityScope = visibilityScope,
            enter = fadeIn(tween(100)),
            exit = fadeOut(tween(100)),
            boundsTransform = { _, _ -> tween(350) },
            resizeMode =
              SharedTransitionScope.ResizeMode.scaleToBounds(
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
              ),
          )
        }
      }
    }
  }

  private data class Key(
    val podcastId: String,
    val source: String,
    val element: PodcastSharedElement,
  )
}
