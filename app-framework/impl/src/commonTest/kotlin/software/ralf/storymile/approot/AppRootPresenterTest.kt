@file:OptIn(ExperimentalAppPlatform::class)

package software.ralf.storymile.approot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import software.ralf.app.platform.ExperimentalAppPlatform
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.compose.test
import software.ralf.storymile.playback.PlaybackPresenterImpl
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.RuntimeModeController
import software.ralf.storymile.runtimemode.testing.FakeRuntimeModeController
import software.ralf.storymile.tabs.TabsPresenter
import software.ralf.storymile.templates.AppTemplate

class AppRootPresenterTest {
  @Test
  fun `mode changes reset navigation and feature state and dispose their effects`() = runTest {
    val controller: RuntimeModeController = FakeRuntimeModeController(RuntimeMode.Real)
    var createdTabs = 0
    var createdPlayback = 0
    val disposedTabs = mutableListOf<Int>()
    val presenter =
      AppRootPresenter(
        tabsPresenter = {
          StatefulTabsPresenter(++createdTabs) { disposedTabs += it }
        },
        playbackPresenter = {
          createdPlayback++
          PlaybackPresenterImpl()
        },
        runtimeModeController = controller,
      )

    presenter.test(this) {
      val initial = awaitItem().adaptive()
      assertThat(initial.tabs().selectedTab).isEqualTo(TabsPresenter.Tab.HOME)
      assertThat(initial.feature().instance).isEqualTo(1)
      initial.tabs().onSelectTab(TabsPresenter.Tab.LIBRARY)
      val library = awaitItem().adaptive()
      assertThat(library.tabs().selectedTab).isEqualTo(TabsPresenter.Tab.LIBRARY)
      library.feature().onIncrement()
      assertThat(awaitItem().adaptive().feature().count).isEqualTo(1)
      assertThat(createdTabs).isEqualTo(1)
      assertThat(createdPlayback).isEqualTo(1)

      controller.switchMode(RuntimeMode.Real)
      expectNoEvents()
      assertThat(createdTabs).isEqualTo(1)
      assertThat(createdPlayback).isEqualTo(1)
      assertThat(disposedTabs).isEqualTo(emptyList())

      controller.switchMode(RuntimeMode.Fake)
      val fake = awaitItem().adaptive()
      assertThat(fake.tabs().selectedTab).isEqualTo(TabsPresenter.Tab.HOME)
      assertThat(fake.feature().instance).isEqualTo(2)
      assertThat(fake.feature().count).isEqualTo(0)
      assertThat(createdPlayback).isEqualTo(2)
      assertThat(disposedTabs).isEqualTo(listOf(1))

      controller.switchMode(RuntimeMode.Real)
      val real = awaitItem().adaptive()
      assertThat(real.tabs().selectedTab).isEqualTo(TabsPresenter.Tab.HOME)
      assertThat(real.feature().instance).isEqualTo(3)
      assertThat(real.feature().count).isEqualTo(0)
      assertThat(createdPlayback).isEqualTo(3)
      assertThat(disposedTabs).isEqualTo(listOf(1, 2))
    }
  }

  private fun AppTemplate.adaptive(): AppTemplate.AdaptiveTemplate =
    this as AppTemplate.AdaptiveTemplate

  private fun AppTemplate.AdaptiveTemplate.feature(): FeatureModel = content as FeatureModel

  private fun AppTemplate.AdaptiveTemplate.tabs(): TabsPresenter.Model = tabs as TabsPresenter.Model

  private class StatefulTabsPresenter(
    private val instance: Int,
    private val onDispose: (Int) -> Unit,
  ) : TabsPresenter {
    @Composable
    override fun present(input: Unit): TabsPresenter.Model {
      var selectedTab by remember { mutableStateOf(TabsPresenter.Tab.HOME) }
      var count by remember { mutableIntStateOf(0) }
      DisposableEffect(Unit) { onDispose { onDispose(instance) } }
      return TabsPresenter.Model(
        selectedTab = selectedTab,
        content = FeatureModel(instance, count) { count++ },
        onSelectTab = { selectedTab = it },
      )
    }
  }

  private data class FeatureModel(
    val instance: Int,
    val count: Int,
    val onIncrement: () -> Unit,
  ) : BaseModel
}
