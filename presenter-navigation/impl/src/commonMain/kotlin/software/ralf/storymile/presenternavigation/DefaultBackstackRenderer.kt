package software.ralf.storymile.presenternavigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import software.ralf.app.platform.inject.ContributesRenderer
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.backstack.nav3.PresenterBackstackRenderer
import software.ralf.app.platform.renderer.Render
import software.ralf.storymile.templates.LocalAnimatedVisibilityScope
import software.ralf.storymile.templates.LocalNavigationEntryIdentity
import software.ralf.storymile.templates.NavigationEntryIdentity
import software.ralf.storymile.templates.SharedAxisTransition

/**
 * Renders the active entry from [DefaultBackstackModel].
 *
 * Entry rendering delegates each [BaseModel] to its renderer through [Render].
 */
@ContributesRenderer
class DefaultBackstackRenderer : PresenterBackstackRenderer<DefaultBackstackModel>() {
  @Composable
  override fun PresenterNavDisplay(
    backstack: List<Int>,
    onBack: () -> Unit,
    entryProvider: (Int) -> NavEntry<Int>,
    modifier: Modifier,
  ) {
    val host = remember { Any() }
    val forwardOffset = SharedAxisTransition.forwardOffset()
    NavDisplay(
      backStack = backstack,
      onBack = onBack,
      entryProvider = { entryId ->
        val entry = entryProvider(entryId)
        // Capture before pop removes the entry. Navigation 3 retains this closure for its exit.
        val identity =
          NavigationEntryIdentity(
            current = EntryIdentity(host, entryId),
            previous =
              backstack.getOrNull(backstack.indexOf(entryId) - 1)?.let { EntryIdentity(host, it) },
          )
        NavEntry(navEntry = entry) {
          CompositionLocalProvider(LocalNavigationEntryIdentity provides identity) {
            entry.Content()
          }
        }
      },
      modifier = modifier.clipToBounds(),
      transitionSpec = { SharedAxisTransition.transition(forwardOffset) },
      popTransitionSpec = { SharedAxisTransition.transition(-forwardOffset) },
      predictivePopTransitionSpec = { SharedAxisTransition.transition(-forwardOffset) },
    )
  }

  @Composable
  override fun ComposeBackstackEntry(model: BaseModel) {
    // NavDisplay is implemented with AnimatedContent. Forward its entry-specific scope so child
    // renderers can coordinate shared elements with the outgoing or incoming navigation entry.
    CompositionLocalProvider(
      LocalAnimatedVisibilityScope provides LocalNavAnimatedContentScope.current,
    ) {
      Render(model)
    }
  }

  private data class EntryIdentity(val host: Any, val entryId: Int)
}
