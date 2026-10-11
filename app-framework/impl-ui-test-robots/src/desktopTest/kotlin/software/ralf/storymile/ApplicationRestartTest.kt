@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)

package software.ralf.storymile

import androidx.compose.ui.test.ExperimentalTestApi
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.zacsweers.metro.createGraphFactory
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import software.ralf.app.platform.robot.composeRobot
import software.ralf.app.platform.robot.waitUntilCatching
import software.ralf.app.platform.scope.di.metro.metroDependencyGraph
import software.ralf.storymile.application.Application
import software.ralf.storymile.application.KmpApplication
import software.ralf.storymile.screen.ScreenSize
import software.ralf.storymile.tabs.TabsPresenter
import software.ralf.storymile.templates.AppTemplate

class ApplicationRestartTest {
  @Test
  fun `restart stops old presenters and storage before starting fresh navigation`() = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    val owner =
      KmpApplication(
        graphFactory = { createGraphFactory<TestDesktopAppGraph.Factory>().create(it) },
      )
    val application: Application = owner
    try {
      owner.create()
      var templateProvider = application.createTemplateProvider()
      listOf(TabsPresenter.Tab.DOWNLOADS, TabsPresenter.Tab.LIBRARY).forEach { selectedTab ->
        templateProvider.templates.test {
          val initialTabs = awaitItem().tabs()
          assertThat(initialTabs.selectedTab).isEqualTo(TabsPresenter.Tab.HOME)
          initialTabs.onSelectTab(selectedTab)
          val selectedTemplate = awaitItem()
          assertThat(selectedTemplate.tabs().selectedTab).isEqualTo(selectedTab)

          val oldScope = application.rootScope
          val storage = oldScope.metroDependencyGraph<AppGraph>().storage
          storage.files.write("restart-test", byteArrayOf(1))
          val preferences = storage.preferences("restart-test")

          application.restart()
          application.rootScopes.filterNotNull().first { it !== oldScope }

          assertTrue(oldScope.isDestroyed())
          assertFailsWith<CancellationException> { storage.files.read("restart-test") }
          assertFailsWith<CancellationException> { preferences.data.first() }

          selectedTemplate.tabs().onSelectTab(TabsPresenter.Tab.HOME)
          runCurrent()
          expectNoEvents()
          assertSame(selectedTemplate, templateProvider.templates.value)
        }
        templateProvider = application.createTemplateProvider()
      }
      templateProvider.templates.test {
        assertThat(awaitItem().tabs().selectedTab).isEqualTo(TabsPresenter.Tab.HOME)
      }
    } finally {
      owner.destroy()
      runCurrent()
      Dispatchers.resetMain()
    }
  }

  @Test
  fun `restart renders fresh home and keeps navigation usable`() {
    DesktopUiTestRule().runPhoneRobotTest {
      listOf(TabsPresenter.Tab.DOWNLOADS, TabsPresenter.Tab.LIBRARY).forEach { selectedTab ->
        composeRobot<AppShellRobot> {
          selectTab(selectedTab)
          seeSelectedTab(selectedTab)
          restart()
        }
        waitUntilCatching("restarted app shows Home") {
          composeRobot<AppShellRobot> { seeAppLayers() }
        }
      }
    }
  }

  private fun Application.createTemplateProvider(): TemplateProvider {
    rootScope
      .metroDependencyGraph<DesktopHeadlessTestRule.Graph>()
      .screenSizeProvider
      .update(
        ScreenSize.from(DesktopWindowSizes.phone.width, DesktopWindowSizes.phone.height),
      )
    return rootScope
      .metroDependencyGraph<AppGraph>()
      .templateProviderFactory
      .createTemplateProvider()
  }

  private fun AppTemplate.tabs(): TabsPresenter.Model {
    return assertIs<TabsPresenter.Model>(assertIs<AppTemplate.AdaptiveTemplate>(this).tabs)
  }
}
