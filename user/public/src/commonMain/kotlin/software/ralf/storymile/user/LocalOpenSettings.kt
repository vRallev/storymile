package software.ralf.storymile.user

import androidx.compose.runtime.compositionLocalOf

/**
 * Opens Settings on the backstack owned by [UserPresenter].
 *
 * For example, when a user opens an episode inside a show, `LocalBackstackScope` gives the episode
 * presenter the show's backstack. Pushing Settings there could open it in the show's detail pane.
 * This action opens Settings across the full content area and keeps the episode on the show's
 * backstack. Pressing Back returns to that episode.
 *
 * Screen presenters read this action and expose it on their models for the profile button.
 * Intermediate presenters do not need to pass it through. A null action disables the button.
 *
 * The callback gives each screen one action and keeps control of the user backstack in
 * [UserPresenter]. If feature presenters need to choose among several user-level destinations,
 * exposing the stack as `LocalUserBackstack` would make the target stack explicit at each push.
 */
val LocalOpenSettings = compositionLocalOf<(() -> Unit)?> { null }
