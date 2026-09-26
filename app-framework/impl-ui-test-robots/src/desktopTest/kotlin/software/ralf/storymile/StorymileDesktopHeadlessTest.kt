package software.ralf.storymile

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import kotlin.test.Test
import software.ralf.storymile.library.LibraryPresenterImpl
import software.ralf.storymile.templates.AppTemplate

class StorymileDesktopHeadlessTest {
  private val headlessTestRule = DesktopHeadlessTestRule()

  @Test
  fun `phone starts with library template`() = headlessTestRule.runPhoneTest {
    assertThat(awaitItem())
      .isInstanceOf<AppTemplate.FullScreenTemplate>()
      .transform { it.model }
      .isEqualTo(LibraryPresenterImpl.Model)
  }

  @Test
  fun `tablet starts with library template`() = headlessTestRule.runTabletTest {
    assertThat(awaitItem())
      .isInstanceOf<AppTemplate.FullScreenTemplate>()
      .transform { it.model }
      .isEqualTo(LibraryPresenterImpl.Model)
  }
}
