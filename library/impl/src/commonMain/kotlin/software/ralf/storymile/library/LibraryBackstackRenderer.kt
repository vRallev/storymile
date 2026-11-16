package software.ralf.storymile.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import software.ralf.app.platform.inject.ContributesRenderer
import software.ralf.app.platform.renderer.ComposeRenderer
import software.ralf.app.platform.renderer.Render

@ContributesRenderer
class LibraryBackstackRenderer : ComposeRenderer<LibraryPresenterImpl.BackstackModel>() {
  @Composable
  override fun Compose(model: LibraryPresenterImpl.BackstackModel, modifier: Modifier) {
    Render(model.content, modifier)
  }
}
