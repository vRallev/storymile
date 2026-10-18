package software.ralf.storymile.templates

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** Elements matched across navigation destinations. */
enum class SharedNavigationElement {
  /** The title in the screen's app bar. */
  APP_BAR_TITLE,
  /** The profile icon in the screen's app bar. */
  PROFILE_ICON,
  /** The search entry point or editable search field. */
  SEARCH_FIELD,
}

/**
 * Moves matching [element] bounds and crossfades their content between navigation entries. Leaves
 * content unchanged outside a shared navigation scope.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedNavigationBounds(element: SharedNavigationElement): Modifier {
  val sharedScope = LocalSharedTransitionScope.current ?: return this
  val visibilityScope = LocalAnimatedVisibilityScope.current ?: return this
  return with(sharedScope) {
    sharedBounds(
      sharedContentState = rememberSharedContentState(element),
      animatedVisibilityScope = visibilityScope,
      resizeMode =
        if (element == SharedNavigationElement.SEARCH_FIELD) {
          // Keep the icon and text insets fixed while the field changes width.
          SharedTransitionScope.ResizeMode.RemeasureToBounds
        } else {
          SharedTransitionScope.ResizeMode.scaleToBounds(alignment = Alignment.CenterStart)
        },
    )
  }
}
