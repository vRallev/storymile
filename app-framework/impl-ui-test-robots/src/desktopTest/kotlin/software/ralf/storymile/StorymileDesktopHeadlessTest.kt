package software.ralf.storymile

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlin.test.assertIs
import software.ralf.storymile.playback.PlaybackPresenterImpl
import software.ralf.storymile.tabs.TabsPresenter
import software.ralf.storymile.tabs.TabsPresenterImpl
import software.ralf.storymile.templates.AppTemplate

class StorymileDesktopHeadlessTest {
  private val headlessTestRule = DesktopHeadlessTestRule()

  @Test
  fun `tab selection updates content without changing playback`() = headlessTestRule.runPhoneTest {
    var template = assertIs<AppTemplate.AdaptiveTemplate>(awaitItem())
    assertThat(template.content).isEqualTo(TabsPresenterImpl.ContentModel(TabsPresenter.Tab.HOME))
    assertThat(assertIs<TabsPresenter.Model>(template.tabs).selectedTab)
      .isEqualTo(TabsPresenter.Tab.HOME)

    listOf(TabsPresenter.Tab.DOWNLOADS, TabsPresenter.Tab.LIBRARY, TabsPresenter.Tab.HOME)
      .forEach { tab ->
        assertIs<TabsPresenter.Model>(template.tabs).onSelectTab(tab)
        template = assertIs<AppTemplate.AdaptiveTemplate>(awaitItem())
        assertThat(template.content).isEqualTo(TabsPresenterImpl.ContentModel(tab))
        assertThat(assertIs<TabsPresenter.Model>(template.tabs).selectedTab).isEqualTo(tab)
        assertThat(template.playback).isEqualTo(PlaybackPresenterImpl.BarModel)
        assertThat(template.expandedPlayback).isEqualTo(PlaybackPresenterImpl.ScreenModel)
      }
  }
}
