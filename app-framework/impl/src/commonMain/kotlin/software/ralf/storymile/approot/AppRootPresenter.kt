package software.ralf.storymile.approot

import androidx.compose.runtime.Composable
import dev.zacsweers.metro.Inject
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.compose.ComposePresenter
import software.ralf.storymile.user.UserPresenter

@Inject
class AppRootPresenter(private val userPresenter: UserPresenter<*>) :
  ComposePresenter<Unit, BaseModel> {
  @Composable
  override fun present(input: Unit): BaseModel {
    return userPresenter.present(Unit)
  }
}
