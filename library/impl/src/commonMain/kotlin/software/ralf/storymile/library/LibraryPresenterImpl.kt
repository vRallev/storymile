package software.ralf.storymile.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.backstack.nav3.LocalBackstackScope
import software.ralf.app.platform.presenter.backstack.nav3.presenterBackstack
import software.ralf.app.platform.presenter.backstack.nav3.requireNotNull
import software.ralf.app.platform.presenter.compose.ComposePresenter
import software.ralf.storymile.discovery.DiscoveryPresenter
import software.ralf.storymile.presenternavigation.DefaultBackstackModel
import software.ralf.storymile.user.UserScope

@Inject
@ContributesBinding(UserScope::class, binding = binding<LibraryPresenter<*>>())
class LibraryPresenterImpl(
  private val discoveryPresenterFactory: DiscoveryPresenter.Factory,
  private val showsPresenter: LibraryShowsPresenter,
) : LibraryPresenter<DefaultBackstackModel> {
  @Composable
  override fun present(input: Unit): DefaultBackstackModel {
    var selectedTab by
      rememberSaveable(
        stateSaver = Saver(save = { tab: Tab -> tab.name }, restore = { Tab.valueOf(it) }),
      ) {
        mutableStateOf(Tab.QUEUE)
      }
    val contentPresenter = remember {
      ComposePresenter<Unit, Model> {
        val backstack = LocalBackstackScope.requireNotNull()
        Model(
          selectedTab = selectedTab,
          onSelectTab = { selectedTab = it },
          onDiscoverPodcasts = { backstack.push(discoveryPresenterFactory.create()) },
        )
      }
    }
    return presenterBackstack(contentPresenter) { backstack ->
      // Preserve show selection when the Library backstack is recreated.
      val shows = showsPresenter.present(Unit)
      DefaultBackstackModel(
        backstack = backstack.map { if (it is Model) it.copy(shows = shows) else it },
        onBack = { pop() },
      )
    }
  }

  enum class Tab {
    QUEUE,
    SHOWS,
    SAVED,
    HISTORY,
  }

  /**
   * Renderer-only state starts with Queue. The selected tab and show survive leaving Library.
   * Discovery and episode details open in the Library backstack.
   */
  data class Model(
    val selectedTab: Tab,
    val onSelectTab: (Tab) -> Unit,
    val onDiscoverPodcasts: () -> Unit,
    val shows: LibraryShowsPresenter.Model? = null,
  ) : BaseModel
}
