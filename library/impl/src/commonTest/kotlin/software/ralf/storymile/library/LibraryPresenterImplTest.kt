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
  fun `tab survives leaving composition and creating a new presenter`() = runTest {
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
        it.onSelectTab(LibraryPresenterImpl.Tab.SAVED)
      }
      assertThat(awaitItem().library).isNotNull().given {
        assertThat(it.selectedTab).isEqualTo(LibraryPresenterImpl.Tab.SAVED)
      }

      visible.value = false
      assertThat(awaitItem().library).isNull()

      visible.value = true
      assertThat(awaitItem().library).isNotNull().given {
        assertThat(it.selectedTab).isEqualTo(LibraryPresenterImpl.Tab.SAVED)
      }
    }
  }

  @Test
  fun `tabs select queue shows or saved without leaving the library`() = runTest {
    val presenter = LibraryPresenterImpl()

    presenter.testLibrary(this) {
      var model = awaitItem()
      assertThat(model.selectedTab).isEqualTo(LibraryPresenterImpl.Tab.QUEUE)

      model.onSelectTab(LibraryPresenterImpl.Tab.SHOWS)
      model = awaitItem()
      assertThat(model.selectedTab).isEqualTo(LibraryPresenterImpl.Tab.SHOWS)

      model.onSelectTab(LibraryPresenterImpl.Tab.SAVED)
      model = awaitItem()
      assertThat(model.selectedTab).isEqualTo(LibraryPresenterImpl.Tab.SAVED)

      model.onSelectTab(LibraryPresenterImpl.Tab.QUEUE)
      model = awaitItem()
      assertThat(model.selectedTab).isEqualTo(LibraryPresenterImpl.Tab.QUEUE)
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
