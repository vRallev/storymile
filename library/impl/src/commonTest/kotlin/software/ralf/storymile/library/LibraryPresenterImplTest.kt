package software.ralf.storymile.library

import androidx.compose.runtime.remember
import app.cash.turbine.ReceiveTurbine
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import kotlin.test.Test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.compose.ComposePresenter
import software.ralf.app.platform.presenter.compose.saveable.rememberReturningSaveableStateHolder
import software.ralf.app.platform.presenter.compose.test

class LibraryPresenterImplTest {
  @Test
  fun `filter survives leaving composition and creating a new presenter`() = runTest {
    val visible = MutableStateFlow(true)
    val presenter =
      ComposePresenter<Boolean, LibraryVisibilityModel> { showLibrary ->
        val stateHolder = rememberReturningSaveableStateHolder()
        stateHolder.SaveableStateProvider(if (showLibrary) "library" else "hidden") {
          LibraryVisibilityModel(
            if (showLibrary) {
              val library = remember { LibraryPresenterImpl() }
              library.present(Unit)
            } else {
              null
            },
          )
        }
      }

    presenter.test(this, visible) {
      assertThat(awaitItem().library).isNotNull().given {
        it.onSelectFilter(LibraryPresenterImpl.Filter.SHOWS)
      }
      assertThat(awaitItem().library).isNotNull().given {
        assertThat(it.selectedFilter).isEqualTo(LibraryPresenterImpl.Filter.SHOWS)
      }

      visible.value = false
      assertThat(awaitItem().library).isNull()

      visible.value = true
      assertThat(awaitItem().library).isNotNull().given {
        assertThat(it.selectedFilter).isEqualTo(LibraryPresenterImpl.Filter.SHOWS)
      }
    }
  }

  @Test
  fun `filters select episodes or shows without leaving the library`() = runTest {
    val presenter = LibraryPresenterImpl()

    presenter.testLibrary(this) {
      var model = awaitItem()
      assertThat(model.selectedFilter).isEqualTo(LibraryPresenterImpl.Filter.EPISODES)

      model.onSelectFilter(LibraryPresenterImpl.Filter.SHOWS)
      model = awaitItem()
      assertThat(model.selectedFilter).isEqualTo(LibraryPresenterImpl.Filter.SHOWS)

      model.onSelectFilter(LibraryPresenterImpl.Filter.EPISODES)
      model = awaitItem()
      assertThat(model.selectedFilter).isEqualTo(LibraryPresenterImpl.Filter.EPISODES)
    }
  }

  private suspend fun LibraryPresenterImpl.testLibrary(
    scope: TestScope,
    validate: suspend ReceiveTurbine<LibraryPresenterImpl.Model>.() -> Unit,
  ) {
    ComposePresenter<Unit, LibraryPresenterImpl.Model> { present(it) }
      .test(scope, validate = validate)
  }

  private data class LibraryVisibilityModel(val library: LibraryPresenterImpl.Model?) : BaseModel
}
