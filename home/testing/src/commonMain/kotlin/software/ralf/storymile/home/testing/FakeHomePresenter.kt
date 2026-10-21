package software.ralf.storymile.home.testing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import software.ralf.storymile.home.HomePresenter

/** Renders [content] and tracks whether the presenter is in composition. */
class FakeHomePresenter<ModelT : HomePresenter.Model>(
  private val content: @Composable () -> ModelT,
) : HomePresenter<ModelT> {
  var active = false
    private set

  @Composable
  override fun present(input: Unit): ModelT {
    DisposableEffect(Unit) {
      active = true
      onDispose { active = false }
    }
    return content()
  }
}
