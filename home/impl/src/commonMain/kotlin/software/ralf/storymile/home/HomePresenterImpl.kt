package software.ralf.storymile.home

import androidx.compose.runtime.Composable
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import software.ralf.app.platform.presenter.backstack.nav3.backstack
import software.ralf.app.platform.presenter.backstack.nav3.presenterBackstack
import software.ralf.storymile.presenternavigation.DefaultBackstackModel
import software.ralf.storymile.user.UserScope

@Inject
@ContributesBinding(UserScope::class, binding<HomePresenter<*>>())
class HomePresenterImpl(private val contentPresenter: HomeContentPresenter) :
  HomePresenter<HomePresenterImpl.Model> {
  @Composable
  override fun present(input: Unit): Model {
    return presenterBackstack(contentPresenter) { models ->
      Model(
        backstack = DefaultBackstackModel(backstack = models, onBack = { pop() }),
        onHomeSelected = {
          while (backstack.size > 1) {
            pop()
          }
        },
      )
    }
  }

  /** [backstack] supplies the delegated renderer's navigation state. */
  data class Model(
    val backstack: DefaultBackstackModel,
    override val onHomeSelected: () -> Unit,
  ) : HomePresenter.Model
}
