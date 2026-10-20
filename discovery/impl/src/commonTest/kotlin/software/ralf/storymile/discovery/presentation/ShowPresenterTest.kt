package software.ralf.storymile.discovery.presentation

import androidx.compose.runtime.Composable
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.backstack.nav3.FakePresenterBackstackScope
import software.ralf.app.platform.presenter.backstack.nav3.LocalBackstackScope
import software.ralf.app.platform.presenter.backstack.nav3.backstack
import software.ralf.app.platform.presenter.backstack.nav3.requireNotNull
import software.ralf.app.platform.presenter.backstack.nav3.withPresenterBackstackScope
import software.ralf.app.platform.presenter.compose.test
import software.ralf.storymile.discovery.DiscoveredPodcast
import software.ralf.storymile.discovery.PodcastDiscoveryRepository.State as CatalogState
import software.ralf.storymile.episodedetails.EpisodeDetails
import software.ralf.storymile.episodedetails.EpisodeDetailsPresenter
import software.ralf.storymile.podcast.PodcastEnclosure
import software.ralf.storymile.podcast.PodcastEpisode
import software.ralf.storymile.podcast.PodcastFeed
import software.ralf.storymile.podcast.PodcastFeedRepository.State as FeedState

class ShowPresenterTest {
  @Test
  fun `episode back preserves the selected show and updates its metadata while open`() = runTest {
    val catalog = TestPodcastCatalog()
    val feeds = TestPodcastFeeds()
    feeds.update("two", FeedState.Success(feed("two")))
    val presenter = presenter(catalog = catalog, feeds = feeds)
    val outerBackstack = FakePresenterBackstackScope()
    val outerRoot = outerBackstack.backstack.single()
    outerBackstack.push(presenter)

    presenter.withPresenterBackstackScope(outerBackstack).test(this) {
      val initial = awaitItem()
      assertThat(initial.backstack.size).isEqualTo(1)
      initial.content.onPodcastSelected(podcast("two"))
      val selected = awaitItem()
      selected.content.onToggleDescription()
      val expanded = awaitItem()
      expanded.content.episodes.episodes.first().onClick()

      val opened = awaitItem()
      assertThat(opened.backstack.size).isEqualTo(2)
      assertThat(opened.content.podcast.id).isEqualTo("two")
      assertThat(opened.content.descriptionExpanded).isTrue()
      assertThat((opened.backstack.last() as EpisodeModel).details.episode.title)
        .isEqualTo("Episode one")
      assertThat(outerBackstack.backstack).containsExactly(outerRoot, presenter)

      val enriched = podcast("two").copy(description = "The selected show's description.")
      catalog.updateLookup(id = "two", state = CatalogState.Success(listOf(enriched)))
      val updated = awaitItem()
      assertThat(updated.content.podcast).isEqualTo(enriched)
      assertThat(updated.content.descriptionExpanded).isTrue()
      (updated.backstack.last() as EpisodeModel).onBack()

      val returned = awaitItem()
      assertThat(returned.backstack.size).isEqualTo(1)
      assertThat(returned.content.podcast).isEqualTo(enriched)
      assertThat(returned.content.descriptionExpanded).isTrue()
      assertThat(returned.content.episodes.episodes.map { it.details.episode.title })
        .containsExactly("Episode one", "Episode two")
      returned.onBack()
      assertThat(outerBackstack.backstack).containsExactly(outerRoot)
    }
  }

  @Test
  fun `selecting another episode replaces the detail entry and back returns to the show`() =
    runTest {
      val feeds = TestPodcastFeeds()
      feeds.update("one", FeedState.Success(feed("one")))
      val presenter = presenter(catalog = TestPodcastCatalog(), feeds = feeds)
      val outerBackstack = FakePresenterBackstackScope()

      presenter.withPresenterBackstackScope(outerBackstack).test(this) {
        awaitItem().content.episodes.episodes.first().onClick()
        val first = awaitItem()
        assertThat(first.backstack.size).isEqualTo(2)
        first.content.episodes.episodes.last().onClick()

        val second = awaitItem()
        assertThat(second.backstack.size).isEqualTo(2)
        assertThat((second.backstack.last() as EpisodeModel).details.episode.title)
          .isEqualTo("Episode two")
        second.onBack()

        val returned = awaitItem()
        assertThat(returned.backstack.size).isEqualTo(1)
        assertThat(returned.content.podcast.id).isEqualTo("one")
        assertThat(outerBackstack.backstack.size).isEqualTo(1)
      }
    }

  private fun presenter(catalog: TestPodcastCatalog, feeds: TestPodcastFeeds) =
    ShowPresenter(
      catalog = catalog,
      feeds = feeds,
      episodeDetailsFactory = DetailsFactory(),
      initialPodcast = podcast("one"),
      podcasts = listOf(podcast("one"), podcast("two")),
      source = ShowPresenter.Source.Browse,
    )

  private fun podcast(id: String) =
    DiscoveredPodcast(
      id = id,
      title = "Show $id",
      author = null,
      feedUrl = id,
      artworkUrl = null,
      catalogUrl = null,
    )

  private fun feed(id: String) =
    PodcastFeed(
      feedUrl = id,
      title = "Show $id",
      description = null,
      author = null,
      artworkUrl = null,
      link = null,
      episodes =
        listOf("one", "two").map { episode ->
          PodcastEpisode(
            id = episode,
            title = "Episode $episode",
            description = null,
            author = null,
            artworkUrl = null,
            publishedAt = null,
            duration = null,
            enclosure =
              PodcastEnclosure(
                url = "https://audio.test/$episode.mp3",
                type = "audio/mpeg",
                length = null,
              ),
          )
        },
    )

  private class DetailsFactory : EpisodeDetailsPresenter.Factory {
    override fun create(details: EpisodeDetails): EpisodeDetailsPresenter<*> =
      object : EpisodeDetailsPresenter<EpisodeModel> {
        @Composable
        override fun present(input: Unit): EpisodeModel {
          val backstack = LocalBackstackScope.requireNotNull()
          return EpisodeModel(details = details, onBack = { backstack.pop() })
        }
      }
  }

  private data class EpisodeModel(val details: EpisodeDetails, val onBack: () -> Unit) : BaseModel
}
