package software.ralf.storymile.library

import androidx.compose.runtime.Composable
import software.ralf.app.platform.presenter.BaseModel

class FakeLibraryPresenter(var model: BaseModel) : LibraryPresenter {
  @Composable override fun present(input: Unit): BaseModel = model
}
