@file:OptIn(ExperimentalCoroutinesApi::class)

package software.ralf.storymile

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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import software.ralf.app.platform.scope.RootScopeProvider
import software.ralf.app.platform.scope.di.metro.metroDependencyGraph
import software.ralf.storymile.scope.runtime.ApplicationRestarter
import software.ralf.storymile.screen.ScreenSize
import software.ralf.storymile.tabs.TabsPresenter
import software.ralf.storymile.templates.AppTemplate

class ApplicationRestartTest {
  @Test
  fun `restart stops presenters and storage before requesting a process restart`() = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    val application = KmpApplication()
    try {
      val testGraph = createGraphFactory<TestDesktopAppGraph.Factory>().create(application)
      application.create(testGraph)
      val restarter: ApplicationRestarter = testGraph.applicationRestarter
      val templateProvider = application.createTemplateProvider()
      templateProvider.templates.test {
        val initialTabs = awaitItem().tabs()
        assertThat(initialTabs.selectedTab).isEqualTo(TabsPresenter.Tab.HOME)
        initialTabs.onSelectTab(TabsPresenter.Tab.DOWNLOADS)
        val selectedTemplate = awaitItem()
        assertThat(selectedTemplate.tabs().selectedTab).isEqualTo(TabsPresenter.Tab.DOWNLOADS)

        val appScope = application.rootScope
        val graph = appScope.metroDependencyGraph<AppGraph>()
        val storage = graph.storage
        storage.files.write("restart-test", byteArrayOf(1))
        val preferences = storage.preferences("restart-test")
        val recorder = testGraph.restartRecorder
        recorder.onRestart = {
          assertTrue(appScope.isDestroyed())
          assertTrue(graph.appScopeCoroutineScopeScoped.coroutineContext.job.isCompleted)
          assertFailsWith<CancellationException> { storage.files.directory("closed") }
        }

        restarter.restart()
        recorder.restarted.await()

        assertFailsWith<CancellationException> { storage.files.read("restart-test") }
        assertFailsWith<CancellationException> { preferences.data.first() }
        selectedTemplate.tabs().onSelectTab(TabsPresenter.Tab.HOME)
        runCurrent()
        expectNoEvents()
        assertSame(selectedTemplate, templateProvider.templates.value)
      }
    } finally {
      application.destroy()
      runCurrent()
      Dispatchers.resetMain()
    }
  }

  private fun RootScopeProvider.createTemplateProvider(): TemplateProvider {
    rootScope
      .metroDependencyGraph<DesktopHeadlessTestRule.Graph>()
      .screenSizeProvider
      .update(
        ScreenSize.from(DesktopWindowSizes.phone.width, DesktopWindowSizes.phone.height),
      )
    return rootScope
      .metroDependencyGraph<DesktopApp.Graph>()
      .templateProviderFactory
      .createTemplateProvider()
  }

  private fun AppTemplate.tabs(): TabsPresenter.Model {
    return assertIs<TabsPresenter.Model>(assertIs<AppTemplate.AdaptiveTemplate>(this).tabs)
  }
}
