package software.ralf.storymile.home

import androidx.compose.runtime.Composable
import dev.zacsweers.metro.Inject
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.backstack.nav3.LocalBackstackScope
import software.ralf.app.platform.presenter.backstack.nav3.requireNotNull
import software.ralf.app.platform.presenter.compose.ComposePresenter
import software.ralf.storymile.discovery.DiscoveryPresenter

@Inject
class HomeContentPresenter(private val discoveryPresenterFactory: DiscoveryPresenter.Factory) :
  ComposePresenter<Unit, HomeContentPresenter.Model> {
  @Composable
  override fun present(input: Unit): Model {
    val backstack = LocalBackstackScope.requireNotNull()
    return Model(
      onDiscover = { backstack.push(discoveryPresenterFactory.create()) },
      onSearch = { backstack.push(discoveryPresenterFactory.create(focusSearch = true)) },
    )
  }

  /** Actions open catalog browsing or focus its search field. */
  data class Model(val onDiscover: () -> Unit, val onSearch: () -> Unit) : BaseModel
}
