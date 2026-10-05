package software.ralf.storymile

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import software.ralf.storymile.library.LibraryPresenterImpl
import software.ralf.storymile.playback.PlaybackPresenterImpl
import software.ralf.storymile.tabs.TabsPresenterImpl
import software.ralf.storymile.templates.AppTemplate

class StorymileDesktopHeadlessTest {
  private val headlessTestRule = DesktopHeadlessTestRule()

  @Test
  fun `app starts with content tabs and playback`() = headlessTestRule.runPhoneTest {
    assertThat(awaitItem())
      .isEqualTo(
        AppTemplate.AdaptiveTemplate(
          content = LibraryPresenterImpl.Model,
          tabs = TabsPresenterImpl.Model,
          playback = PlaybackPresenterImpl.Model,
        )
      )
  }
}
