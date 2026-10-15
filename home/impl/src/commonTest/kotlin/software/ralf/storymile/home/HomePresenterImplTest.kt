package software.ralf.storymile.home

import androidx.compose.runtime.Composable
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.backstack.nav3.LocalBackstackScope
import software.ralf.app.platform.presenter.backstack.nav3.requireNotNull
import software.ralf.app.platform.presenter.compose.ComposePresenter
import software.ralf.app.platform.presenter.compose.test
import software.ralf.storymile.discovery.DiscoveryPresenter

class HomePresenterImplTest {
  @Test
  fun `back returns from a show through discovery to welcome`() = runTest {
    val presenter = HomePresenterImpl(HomeContentPresenter(FakeDiscoveryFactory()))

    presenter.test(this) {
      val welcome = awaitItem().backstack.backstack.single() as HomeContentPresenter.Model
      welcome.onDiscover()

      val discoveryStack = awaitItem()
      val discovery = discoveryStack.backstack.backstack.last() as DiscoveryModel
      assertThat(discovery.focusSearch).isEqualTo(false)
      discovery.onOpenShow()

      val showStack = awaitItem()
      assertThat(showStack.backstack.backstack.size).isEqualTo(3)
      assertThat(showStack.backstack.backstack.last()).isEqualTo(ShowModel)
      showStack.backstack.onBack()

      val returnedDiscoveryStack = awaitItem()
      assertThat(returnedDiscoveryStack.backstack.backstack.size).isEqualTo(2)
      assertThat(returnedDiscoveryStack.backstack.backstack.last()).isInstanceOf<DiscoveryModel>()
      returnedDiscoveryStack.backstack.onBack()

      val returnedHome = awaitItem()
      assertThat(returnedHome.backstack.backstack.single())
        .isInstanceOf<HomeContentPresenter.Model>()
      returnedHome.backstack.onBack()
      expectNoEvents()
    }
  }

  @Test
  fun `home search opens discovery with search focus`() = runTest {
    val presenter = HomePresenterImpl(HomeContentPresenter(FakeDiscoveryFactory()))

    presenter.test(this) {
      val welcome = awaitItem().backstack.backstack.single() as HomeContentPresenter.Model
      welcome.onSearch()

      val discovery = awaitItem().backstack.backstack.last() as DiscoveryModel
      assertThat(discovery.focusSearch).isEqualTo(true)
    }
  }

  @Test
  fun `home selection clears every pushed entry and can repeat at root or after reopening`() =
    runTest {
      val presenter = HomePresenterImpl(HomeContentPresenter(FakeDiscoveryFactory()))

      presenter.test(this) {
        val initial = awaitItem()
        initial.onHomeSelected()
        initial.onHomeSelected()
        expectNoEvents()

        (initial.backstack.backstack.single() as HomeContentPresenter.Model).onDiscover()
        val discovery = awaitItem().backstack.backstack.last() as DiscoveryModel
        discovery.onOpenShow()
        val show = awaitItem()
        assertThat(show.backstack.backstack.size).isEqualTo(3)

        show.onHomeSelected()
        val returnedHome = awaitItem()
        val welcome = returnedHome.backstack.backstack.single() as HomeContentPresenter.Model
        returnedHome.onHomeSelected()
        returnedHome.onHomeSelected()
        expectNoEvents()

        welcome.onSearch()
        val reopened = awaitItem()
        assertThat(reopened.backstack.backstack.size).isEqualTo(2)
        assertThat((reopened.backstack.backstack.last() as DiscoveryModel).focusSearch)
          .isEqualTo(true)
        reopened.onHomeSelected()
        assertThat(awaitItem().backstack.backstack.single())
          .isInstanceOf<HomeContentPresenter.Model>()
      }
    }

  private class FakeDiscoveryFactory : DiscoveryPresenter.Factory {
    override fun create(focusSearch: Boolean): DiscoveryPresenter<*> =
      FakeDiscoveryPresenter(focusSearch)
  }

  private class FakeDiscoveryPresenter(private val focusSearch: Boolean) :
    DiscoveryPresenter<DiscoveryModel> {
    @Composable
    override fun present(input: Unit): DiscoveryModel {
      val backstack = LocalBackstackScope.requireNotNull()
      return DiscoveryModel(
        focusSearch = focusSearch,
        onOpenShow = { backstack.push(ShowPresenter) },
      )
    }
  }

  private data class DiscoveryModel(val focusSearch: Boolean, val onOpenShow: () -> Unit) :
    BaseModel

  private object ShowPresenter : ComposePresenter<Unit, ShowModel> {
    @Composable override fun present(input: Unit): ShowModel = ShowModel
  }

  private data object ShowModel : BaseModel
}
