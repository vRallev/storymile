package software.ralf.storymile.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.storymile.user.UserScope

@Inject
@ContributesBinding(UserScope::class, binding = binding<LibraryPresenter<*>>())
class LibraryPresenterImpl : LibraryPresenter<LibraryPresenterImpl.Model> {
  @Composable
  override fun present(input: Unit): Model {
    var selectedTab by
      rememberSaveable(
        stateSaver = Saver(save = { tab: Tab -> tab.name }, restore = { Tab.valueOf(it) }),
      ) {
        mutableStateOf(Tab.QUEUE)
      }
    return Model(
      selectedTab = selectedTab,
      onSelectTab = { selectedTab = it },
      onDiscoverPodcasts = {},
    )
  }

  enum class Tab {
    QUEUE,
    SHOWS,
    SAVED,
  }

  /**
   * Renderer-only state starts with Queue and retains the selected tab when leaving Library.
   * Discovery is a placeholder action.
   */
  data class Model(
    val selectedTab: Tab,
    val onSelectTab: (Tab) -> Unit,
    val onDiscoverPodcasts: () -> Unit,
  ) : BaseModel
}
