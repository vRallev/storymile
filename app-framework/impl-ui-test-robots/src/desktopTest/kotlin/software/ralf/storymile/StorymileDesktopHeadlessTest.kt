package software.ralf.storymile

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import software.ralf.storymile.tabs.TabsPresenter
import software.ralf.storymile.templates.AppTemplate

class StorymileDesktopHeadlessTest {
  private val headlessTestRule = DesktopHeadlessTestRule()

  @Test
  fun `tab selection updates content without changing playback`() = headlessTestRule.runPhoneTest {
    var template = assertIs<AppTemplate.AdaptiveTemplate>(awaitItem())
    var tabs = assertIs<TabsPresenter.Model>(template.tabs)
    val playback = assertNotNull(template.playback)
    val expandedPlayback = assertNotNull(template.expandedPlayback)
    assertThat(template.content).isEqualTo(tabs.content)
    assertThat(tabs.selectedTab).isEqualTo(TabsPresenter.Tab.HOME)

    listOf(TabsPresenter.Tab.DOWNLOADS, TabsPresenter.Tab.LIBRARY, TabsPresenter.Tab.HOME)
      .forEach { tab ->
        tabs.onSelectTab(tab)
        template = assertIs<AppTemplate.AdaptiveTemplate>(awaitItem())
        tabs = assertIs<TabsPresenter.Model>(template.tabs)
        assertThat(template.content).isEqualTo(tabs.content)
        assertThat(tabs.selectedTab).isEqualTo(tab)
        assertThat(template.playback).isEqualTo(playback)
        assertThat(template.expandedPlayback).isEqualTo(expandedPlayback)
      }
  }
}
