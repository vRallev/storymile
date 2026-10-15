package software.ralf.storymile.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import software.ralf.app.platform.inject.ContributesRenderer
import software.ralf.app.platform.renderer.ComposeRenderer
import software.ralf.app.platform.renderer.Render

@ContributesRenderer
class HomeRenderer : ComposeRenderer<HomePresenterImpl.Model>() {
  @Composable
  override fun Compose(model: HomePresenterImpl.Model, modifier: Modifier) {
    Render(model.backstack, modifier)
  }
}
