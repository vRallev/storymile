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
    var selectedFilter by
      rememberSaveable(
        stateSaver =
          Saver(save = { filter: Filter -> filter.name }, restore = { Filter.valueOf(it) }),
      ) {
        mutableStateOf(Filter.EPISODES)
      }
    return Model(
      selectedFilter = selectedFilter,
      onSelectFilter = { selectedFilter = it },
      onDiscoverPodcasts = {},
    )
  }

  enum class Filter {
    EPISODES,
    SHOWS,
  }

  /**
   * Renderer-only state starts with episodes and retains the selected filter across tab changes.
   * Discovery is a placeholder action.
   */
  data class Model(
    val selectedFilter: Filter,
    val onSelectFilter: (Filter) -> Unit,
    val onDiscoverPodcasts: () -> Unit,
  ) : BaseModel
}
