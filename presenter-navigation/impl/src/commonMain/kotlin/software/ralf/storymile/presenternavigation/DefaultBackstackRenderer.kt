package software.ralf.storymile.presenternavigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import software.ralf.app.platform.inject.ContributesRenderer
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.backstack.nav3.PresenterBackstackRenderer
import software.ralf.app.platform.renderer.Render
import software.ralf.storymile.templates.LocalAnimatedVisibilityScope
import software.ralf.storymile.theme.AppTheme

/**
 * Renders the active entry from [DefaultBackstackModel].
 *
 * Entry rendering delegates each [BaseModel] to its renderer through [Render].
 */
@ContributesRenderer
class DefaultBackstackRenderer : PresenterBackstackRenderer<DefaultBackstackModel>() {
  @Composable
  override fun ComposeBackstackEntry(model: BaseModel) {
    // NavDisplay is implemented with AnimatedContent. Forward its entry-specific scope so child
    // renderers can coordinate shared elements with the outgoing or incoming navigation entry.
    CompositionLocalProvider(
      LocalAnimatedVisibilityScope provides LocalNavAnimatedContentScope.current,
    ) {
      Surface(
        modifier = Modifier.fillMaxSize(),
        color = AppTheme.colorScheme.background,
        contentColor = AppTheme.colorScheme.onBackground,
      ) {
        Render(model)
      }
    }
  }
}
