package software.ralf.storymile.show

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import software.ralf.storymile.templates.LocalAnimatedVisibilityScope
import software.ralf.storymile.templates.LocalNavigationEntryIdentity
import software.ralf.storymile.templates.LocalSharedTransitionScope

/** Parts of the show header that can move from a list item to the detail screen. */
enum class ShowSharedElement {
  /** Cover art. */
  Artwork,
  /** Show name. */
  Title,
  /** Author or publisher. */
  Author;

  internal data class Key(
    val entry: Any,
    val podcastId: String,
    val source: ShowOrigin,
    val element: ShowSharedElement,
  )
}

/**
 * Matches [element] for [podcastId] between the source entry and the show header. Set
 * [fromPreviousEntry] on the header. Pass a null [source] for elements that should not match. Pass
 * [visibleInPane] for artwork when the list and detail remain visible side by side.
 */
@Composable
fun Modifier.showSharedElement(
  podcastId: String,
  source: ShowOrigin?,
  element: ShowSharedElement,
  fromPreviousEntry: Boolean = false,
  visibleInPane: Boolean? = null,
): Modifier {
  if (source == null) {
    return this
  }
  val sharedScope = LocalSharedTransitionScope.current ?: return this
  val visibilityScope = LocalAnimatedVisibilityScope.current
  val navigationEntry = LocalNavigationEntryIdentity.current ?: return this
  val entry =
    (if (fromPreviousEntry) navigationEntry.previous else navigationEntry.current) ?: return this
  return with(sharedScope) {
    val state = rememberSharedContentState(ShowSharedElement.Key(entry, podcastId, source, element))
    if (visibleInPane != null) {
      this@showSharedElement.sharedElementWithCallerManagedVisibility(
        sharedContentState = state,
        visible = visibleInPane,
        boundsTransform = { _, _ -> tween(350) },
      )
    } else if (visibilityScope == null) {
      this@showSharedElement
    } else if (element == ShowSharedElement.Artwork) {
      this@showSharedElement.sharedElement(
        sharedContentState = state,
        animatedVisibilityScope = visibilityScope,
        boundsTransform = { _, _ -> tween(350) },
      )
    } else {
      this@showSharedElement.sharedBounds(
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
