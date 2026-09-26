@file:OptIn(ExperimentalAppPlatform::class)

package software.ralf.storymile.approot

import assertk.assertThat
import assertk.assertions.isSameInstanceAs
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import software.ralf.app.platform.ExperimentalAppPlatform
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.compose.test
import software.ralf.storymile.library.FakeLibraryPresenter

class AppRootPresenterTest {
  @Test
  fun `root presents the library`() = runTest {
    val model = object : BaseModel {}
    AppRootPresenter(FakeLibraryPresenter(model)).test(this) {
      assertThat(awaitItem()).isSameInstanceAs(model)
    }
  }
}
