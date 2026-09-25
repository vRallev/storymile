@file:OptIn(ExperimentalAppPlatform::class)

package software.ralf.storymile.library

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import software.ralf.app.platform.ExperimentalAppPlatform
import software.ralf.app.platform.presenter.compose.test

class LibraryPresenterImplTest {
  @Test
  fun `library starts empty`() = runTest {
    LibraryPresenterImpl().test(this) {
      assertThat(awaitItem()).isEqualTo(LibraryPresenterImpl.Model)
    }
  }
}
