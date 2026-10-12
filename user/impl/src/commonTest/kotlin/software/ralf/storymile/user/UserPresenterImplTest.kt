package software.ralf.storymile.user

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.cash.turbine.ReceiveTurbine
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.compose.test
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.buildTestScope
import software.ralf.app.platform.scope.di.metro.addMetroDependencyGraph
import software.ralf.storymile.playback.PlaybackPresenter
import software.ralf.storymile.tabs.TabsPresenter
import software.ralf.storymile.templates.AppTemplate

class UserPresenterImplTest {
  @Test
  fun `changing users resets both children and disposes their previous effects`() = runTest {
    val first = FakeGraph("first")
    val second = FakeGraph("second")
    val firstScope = Scope.buildTestScope(this) { addMetroDependencyGraph(first) }
    val secondScope = Scope.buildTestScope(this) { addMetroDependencyGraph(second) }
    val userManager = FakeUserManager(firstScope)
    val presenter: UserPresenter<AppTemplate> = UserPresenterImpl(userManager)

    presenter.test(this) {
      var template = awaitTemplate()
      assertThat(template.content).isEqualTo(Content("first"))
      assertThat(first.tabsPresenter.isActive).isTrue()
      assertThat(first.playbackPresenter.isActive).isTrue()

      (template.tabs as TabsPresenter.Model).onSelectTab(TabsPresenter.Tab.DOWNLOADS)
      template = awaitTemplate()
      assertThat((template.tabs as TabsPresenter.Model).selectedTab)
        .isEqualTo(TabsPresenter.Tab.DOWNLOADS)

      (template.playback as PlaybackContent).onIncrement()
      template = awaitTemplate()
      assertThat((template.tabs as TabsPresenter.Model).selectedTab)
        .isEqualTo(TabsPresenter.Tab.DOWNLOADS)
      assertThat((template.expandedPlayback as PlaybackContent).count).isEqualTo(1)

      (template.tabs as TabsPresenter.Model).onSelectTab(TabsPresenter.Tab.LIBRARY)
      template = awaitTemplate()
      assertThat((template.playback as PlaybackContent).count).isEqualTo(1)

      userManager.switchUser(secondScope)
      template = awaitTemplate()
      assertThat(template.content).isEqualTo(Content("second"))
      assertThat((template.tabs as TabsPresenter.Model).selectedTab)
        .isEqualTo(TabsPresenter.Tab.HOME)
      assertThat((template.playback as PlaybackContent).count).isEqualTo(0)
      assertThat((template.expandedPlayback as PlaybackContent).count).isEqualTo(0)
      assertThat(first.tabsPresenter.isActive).isFalse()
      assertThat(first.playbackPresenter.isActive).isFalse()
      assertThat(second.tabsPresenter.isActive).isTrue()
      assertThat(second.playbackPresenter.isActive).isTrue()
    }
  }

  private suspend fun ReceiveTurbine<AppTemplate>.awaitTemplate(): AppTemplate.AdaptiveTemplate =
    awaitItem() as AppTemplate.AdaptiveTemplate

  private class FakeUserManager(initialScope: Scope) : UserManager {
    override val userScope: StateFlow<Scope>
      field = MutableStateFlow(initialScope)

    fun switchUser(scope: Scope) {
      userScope.value = scope
    }
  }

  private class FakeGraph(user: String) : UserPresenterImpl.Graph {
    override val tabsPresenter = FakeTabsPresenter(user)
    override val playbackPresenter = FakePlaybackPresenter()
  }

  private class FakeTabsPresenter(private val user: String) : TabsPresenter {
    var isActive = false
      private set

    @Composable
    override fun present(input: Unit): TabsPresenter.Model {
      var selectedTab by remember { mutableStateOf(TabsPresenter.Tab.HOME) }
      DisposableEffect(Unit) {
        isActive = true
        onDispose { isActive = false }
      }
      return TabsPresenter.Model(selectedTab, Content(user)) { selectedTab = it }
    }
  }

  private class FakePlaybackPresenter : PlaybackPresenter {
    var isActive = false
      private set

    @Composable
    override fun present(input: Unit): PlaybackPresenter.Model {
      var count by remember { mutableIntStateOf(0) }
      DisposableEffect(Unit) {
        isActive = true
        onDispose { isActive = false }
      }
      val content = PlaybackContent(count) { count++ }
      return Model(content, content)
    }

    private data class Model(
      override val collapsed: BaseModel,
      override val expanded: BaseModel,
    ) : PlaybackPresenter.Model
  }

  private data class Content(val user: String) : BaseModel

  private data class PlaybackContent(val count: Int, val onIncrement: () -> Unit) : BaseModel
}
