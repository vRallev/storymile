package software.ralf.storymile.home

import androidx.compose.runtime.Composable
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.storymile.user.UserScope

@Inject
@ContributesBinding(UserScope::class, binding<HomePresenter<*>>())
class HomePresenterImpl : HomePresenter<HomePresenterImpl.Model> {
  @Composable override fun present(input: Unit): Model = Model

  data object Model : BaseModel
}
