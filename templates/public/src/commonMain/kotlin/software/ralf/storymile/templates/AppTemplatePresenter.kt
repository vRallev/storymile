package software.ralf.storymile.templates

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withCompositionLocal
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.launch
import software.ralf.app.platform.presenter.compose.ComposePresenter
import software.ralf.app.platform.presenter.compose.backgesture.BackGestureDispatcherPresenter
import software.ralf.app.platform.presenter.compose.backgesture.LocalBackGestureDispatcherPresenter
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.RuntimeModeController

/**
 * Adapts a root feature presenter into the application template stream.
 *
 * It also installs the shared back-gesture dispatcher so nested navigation presenters can handle
 * platform back events without depending on a platform-specific entrypoint.
 */
@AssistedInject
class AppTemplatePresenter(
  private val backGestureDispatcherPresenter: BackGestureDispatcherPresenter,
  private val runtimeModeController: RuntimeModeController,
  @Assisted private val rootPresenter: ComposePresenter<Unit, *>,
) : ComposePresenter<Unit, AppTemplate> {
  @Composable
  override fun present(input: Unit): AppTemplate {
    val coroutineScope = rememberCoroutineScope()
    val template =
      withCompositionLocal(
        LocalBackGestureDispatcherPresenter provides backGestureDispatcherPresenter,
      ) {
        rootPresenter.present(Unit).toAppTemplate()
      }
    return when (template) {
      is AppTemplate.AdaptiveTemplate ->
        template.copy(
          onToggleRuntimeMode = {
            coroutineScope.launch {
              val mode =
                when (runtimeModeController.mode()) {
                  RuntimeMode.Real -> RuntimeMode.Fake
                  RuntimeMode.Fake -> RuntimeMode.Real
                }
              runtimeModeController.switchMode(mode)
            }
          },
        )
    }
  }

  /** Creates a template presenter around an assisted root feature presenter. */
  @AssistedFactory
  interface Factory {
    /** Creates the application template presenter for [rootPresenter]. */
    fun createAppTemplatePresenter(rootPresenter: ComposePresenter<Unit, *>): AppTemplatePresenter
  }
}
