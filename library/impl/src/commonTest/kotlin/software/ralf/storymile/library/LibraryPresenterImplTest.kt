package software.ralf.storymile.library

import app.cash.turbine.ReceiveTurbine
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import software.ralf.app.platform.presenter.compose.ComposePresenter
import software.ralf.app.platform.presenter.compose.test

class LibraryPresenterImplTest {
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
}
