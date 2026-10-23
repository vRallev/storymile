package software.ralf.storymile

import app.cash.turbine.ReceiveTurbine
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import java.nio.file.Files
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import software.ralf.app.platform.presenter.backstack.nav3.FakePresenterBackstackScope
import software.ralf.app.platform.presenter.backstack.nav3.withPresenterBackstackScope
import software.ralf.app.platform.presenter.compose.test
import software.ralf.app.platform.scope.RootScopeProvider
import software.ralf.app.platform.scope.coroutine.CoroutineScopeScoped
import software.ralf.app.platform.scope.di.metro.metroDependencyGraph
import software.ralf.storymile.database.PodcastDatabase
import software.ralf.storymile.discovery.PodcastDiscoveryRepository
import software.ralf.storymile.discovery.presentation.ShowContentPresenter
import software.ralf.storymile.discovery.presentation.ShowPresenter
import software.ralf.storymile.episodedetails.EpisodeDetailsPresenter
import software.ralf.storymile.podcast.PodcastFeedRepository
import software.ralf.storymile.storage.DefaultStorage
import software.ralf.storymile.storage.OkioStoragePlatform
import software.ralf.storymile.storage.Storage
import software.ralf.storymile.user.UserManager
import software.ralf.storymile.user.UserScope

class FollowShowPersistenceTest {
  @Test
  fun `follow and unfollow survive application recreation in the guest database`() = runTest {
    val root = Files.createTempDirectory("storymile-follow").toString().toPath()
    val platform =
      object : OkioStoragePlatform(FileSystem.SYSTEM, Dispatchers.IO) {
        override fun rootDirectory(): Path = root

        override fun cacheDirectory(): Path = root / "cache"
      }
    val storageFactory =
      object : Storage.Factory {
        override fun create(
          namespace: String,
          coroutineScope: CoroutineScope,
          mustBePersistent: Boolean,
        ): Storage = DefaultStorage(namespace, coroutineScope, platform)
      }
    try {
      withApplication(storageFactory) { graph, user ->
        graph.showPresenter(user).test(this) {
          awaitModel { it.followEnabled }.onToggleFollow()
          awaitModel { it.followed && it.followEnabled }
          val saved = user.database.podcasts.first().single()
          assertThat(saved.title).isEqualTo("The Quiet Current")
          assertThat(saved.episodes.size).isEqualTo(3)
        }
      }
      withApplication(storageFactory) { graph, user ->
        graph.showPresenter(user).test(this) {
          awaitModel { it.followed && it.followEnabled }.onToggleFollow()
          awaitModel { !it.followed && it.followEnabled }
          assertThat(user.database.podcasts.first()).isEmpty()
        }
      }
      withApplication(storageFactory) { graph, user ->
        graph.showPresenter(user).test(this) {
          assertThat(awaitModel { it.followEnabled }.followed).isFalse()
          assertThat(user.database.podcasts.first()).isEmpty()
        }
      }
    } finally {
      FileSystem.SYSTEM.deleteRecursively(root)
    }
  }

  private suspend fun Graph.showPresenter(user: UserBindings) =
    ShowContentPresenter(
        catalog = catalog,
        feeds = feeds,
        episodeDetailsFactory = user.episodeDetailsFactory,
        database = user.database,
        initialPodcast =
          (catalog.popular.first { it is PodcastDiscoveryRepository.State.Success }
              as PodcastDiscoveryRepository.State.Success)
            .podcasts
            .first(),
        podcasts = emptyList(),
        source = ShowPresenter.Source.Browse,
        onBack = {},
      )
      .withPresenterBackstackScope(FakePresenterBackstackScope())

  private suspend fun ReceiveTurbine<ShowContentPresenter.Model>.awaitModel(
    predicate: (ShowContentPresenter.Model) -> Boolean,
  ): ShowContentPresenter.Model {
    while (true) {
      val model = awaitItem()
      if (predicate(model)) {
        return model
      }
    }
  }

  private suspend fun withApplication(
    storageFactory: Storage.Factory,
    block: suspend (Graph, UserBindings) -> Unit,
  ) {
    val application = KmpApplication()
    val graph = createGraphFactory<Graph.Factory>().create(application, storageFactory)
    application.create(graph)
    val user = graph.userManager.userScope.value.metroDependencyGraph<UserBindings>()
    try {
      block(graph, user)
    } finally {
      application.destroy()
      user.coroutineOwner.coroutineContext.job.join()
      graph.appScopeCoroutineScopeScoped.coroutineContext.job.join()
    }
  }

  @DependencyGraph(
    AppScope::class,
    excludes = [DefaultStorage.Factory::class, TestStorageBindings::class],
  )
  interface Graph : AppGraph {
    val userManager: UserManager
    val catalog: PodcastDiscoveryRepository
    val feeds: PodcastFeedRepository

    @DependencyGraph.Factory
    fun interface Factory {
      fun create(
        @Provides rootScopeProvider: RootScopeProvider,
        @Provides storageFactory: Storage.Factory,
      ): Graph
    }
  }

  @ContributesTo(UserScope::class)
  interface UserBindings {
    val database: PodcastDatabase
    val episodeDetailsFactory: EpisodeDetailsPresenter.Factory
    @ForScope(UserScope::class) val coroutineOwner: CoroutineScopeScoped
  }
}
