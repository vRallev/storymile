package software.ralf.storymile

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import kotlin.test.Test
import software.ralf.storymile.tabs.TabsPresenter
import software.ralf.storymile.templates.AppTemplate

class StorymileDesktopHeadlessTest {
  private val headlessTestRule = DesktopHeadlessTestRule()

  @Test
  fun `tab selection updates content without changing playback`() = headlessTestRule.runPhoneTest {
    assertThat(awaitItem()).isInstanceOf<AppTemplate.AdaptiveTemplate>().given { initial ->
      val playback = initial.playback
      val expandedPlayback = initial.expandedPlayback
      assertThat(playback).isNull()
      assertThat(expandedPlayback).isNull()
      assertThat(initial.tabs).isNotNull().isInstanceOf<TabsPresenter.Model>().given { initialTabs
        ->
        var tabs = initialTabs
        assertThat(initial.content).isEqualTo(tabs.content)
        assertThat(tabs.selectedTab).isEqualTo(TabsPresenter.Tab.HOME)

        listOf(TabsPresenter.Tab.DOWNLOADS, TabsPresenter.Tab.LIBRARY, TabsPresenter.Tab.HOME)
          .forEach { tab ->
            tabs.onSelectTab(tab)
            assertThat(awaitItem()).isInstanceOf<AppTemplate.AdaptiveTemplate>().given { template ->
              assertThat(template.tabs).isNotNull().isInstanceOf<TabsPresenter.Model>().given {
                updatedTabs ->
                tabs = updatedTabs
                assertThat(template.content).isEqualTo(tabs.content)
                assertThat(tabs.selectedTab).isEqualTo(tab)
              }
              assertThat(template.playback).isEqualTo(playback)
              assertThat(template.expandedPlayback).isEqualTo(expandedPlayback)
            }
          }
      }
    }
  }
}
