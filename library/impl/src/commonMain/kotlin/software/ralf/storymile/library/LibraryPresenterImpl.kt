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
import software.ralf.app.platform.presenter.backstack.nav3.backstack
import software.ralf.app.platform.presenter.backstack.nav3.presenterBackstack
import software.ralf.app.platform.presenter.backstack.nav3.requireNotNull
import software.ralf.app.platform.presenter.compose.ComposePresenter
import software.ralf.storymile.discovery.DiscoveryPresenter
import software.ralf.storymile.libraryqueue.LibraryQueuePresenter
import software.ralf.storymile.librarysaved.LibrarySavedPresenter
import software.ralf.storymile.libraryshows.LibraryShowsPresenter
import software.ralf.storymile.presenternavigation.DefaultBackstackModel
import software.ralf.storymile.user.LocalOpenSettings
import software.ralf.storymile.user.UserScope

@Inject
@ContributesBinding(UserScope::class, binding = binding<LibraryPresenter<*>>())
class LibraryPresenterImpl(
  private val discoveryPresenterFactory: DiscoveryPresenter.Factory,
  private val queuePresenter: LibraryQueuePresenter,
  private val showsPresenter: LibraryShowsPresenter,
  private val savedPresenter: LibrarySavedPresenter,
) : LibraryPresenter<LibraryPresenterImpl.BackstackModel> {
  @Composable
  override fun present(input: Unit): BackstackModel {
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
          queue = queuePresenter.present(Unit),
          shows = showsPresenter.present(Unit),
          saved = savedPresenter.present(Unit),
          onSelectTab = { selectedTab = it },
          onOpenSettings = LocalOpenSettings.current,
          onDiscoverPodcasts = { backstack.push(discoveryPresenterFactory.create()) },
        )
      }
    }
    return presenterBackstack(contentPresenter) { models ->
      val root = models.first() as Model
      BackstackModel(
        content = DefaultBackstackModel(backstack = models, onBack = { pop() }),
        onLibrarySelected = {
          while (backstack.size > 1) {
            pop()
          }
          root.queue.onCloseEpisode()
          root.shows.onCloseShow()
          root.saved.onCloseEpisode()
        },
      )
    }
  }

  enum class Tab {
    QUEUE,
    SHOWS,
    SAVED,
    HISTORY,
  }

  /** [content] supplies the delegated renderer's navigation state. */
  data class BackstackModel(
    val content: DefaultBackstackModel,
    override val onLibrarySelected: () -> Unit,
  ) : LibraryPresenter.Model

  /**
   * Renderer-only state starts with Queue and retains the selected tab when leaving Library.
   * Discovery opens in the Library backstack.
   */
  data class Model(
    val selectedTab: Tab,
    val queue: LibraryQueuePresenter.Model,
    val shows: LibraryShowsPresenter.Model,
    val saved: LibrarySavedPresenter.Model,
    val onSelectTab: (Tab) -> Unit,
    val onDiscoverPodcasts: () -> Unit,
    val onOpenSettings: (() -> Unit)? = null,
  ) : BaseModel
}
