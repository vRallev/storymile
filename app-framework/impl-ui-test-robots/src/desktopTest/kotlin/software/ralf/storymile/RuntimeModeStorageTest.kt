package software.ralf.storymile

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.ReceiveTurbine
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import java.net.URI
import kotlin.test.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.compose.test
import software.ralf.app.platform.scope.RootScopeProvider
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.buildTestScope
import software.ralf.app.platform.scope.coroutine.coroutineScope
import software.ralf.app.platform.scope.di.metro.metroDependencyGraph
import software.ralf.storymile.approot.AppRootPresenter
import software.ralf.storymile.player.Player
import software.ralf.storymile.podcast.FakePodcasts
import software.ralf.storymile.podcast.PodcastEnclosure
import software.ralf.storymile.podcast.PodcastEpisode
import software.ralf.storymile.podcast.QueueRepository
import software.ralf.storymile.podcast.QueuedEpisode
import software.ralf.storymile.restart.ApplicationRestarter
import software.ralf.storymile.restart.DefaultApplicationRestarter
import software.ralf.storymile.restart.testing.FakeApplicationRestarter
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.RuntimeModeController
import software.ralf.storymile.runtimemode.TestRuntimeModeController
import software.ralf.storymile.storage.DefaultStorage
import software.ralf.storymile.storage.DesktopStoragePlatform
import software.ralf.storymile.storage.InMemoryStoragePlatform
import software.ralf.storymile.storage.Storage
import software.ralf.storymile.storage.StoragePlatform
import software.ralf.storymile.templates.AppTemplate
import software.ralf.storymile.user.UserScope

class RuntimeModeStorageTest {
  @Test
  fun `real playback bar follows the library queue`() = runTest {
    withApplication(InMemoryStoragePlatform()) { graph, _ ->
      assertThat(graph.runtimeModeController.mode).isEqualTo(RuntimeMode.Real)
      val queue =
        checkNotNull(graph.userManager.userScope.value)
          .metroDependencyGraph<UserStorageBindings>()
          .queue
      val player = graph.player()
      val episode =
        QueuedEpisode(
          feedUrl = "https://feeds.test/show.xml",
          podcastTitle = "Queued show",
          artworkUrl = null,
          episode =
            PodcastEpisode(
              id = "episode-1",
              title = "Queued episode",
              description = null,
              author = null,
              artworkUrl = null,
              publishedAt = null,
              duration = null,
              enclosure =
                PodcastEnclosure(
                  url = "https://audio.test/episode-1.mp3",
                  type = "audio/mpeg",
                  length = null,
                ),
            ),
        )

      graph.appRootPresenter.test(this) {
        val initial = awaitItem() as AppTemplate.AdaptiveTemplate
        assertThat(initial.playback).isNull()
        assertThat(initial.expandedPlayback).isNull()

        queue.save(episode)
        val queued = awaitPlayback(visible = true)
        assertThat(queued.expandedPlayback).isNotNull()
        val selected = player.state.value
        assertThat(selected.episode).isEqualTo(episode)
        assertThat(selected.isPlaying).isFalse()

        queue.remove(episode)
        val empty = awaitPlayback(visible = false)
        assertThat(empty.expandedPlayback).isNull()
        cancelAndIgnoreRemainingEvents()
      }
    }
  }

  @Test
  fun `fake demo queue is ordered editable and resets after restart`() = runTest {
    val platform = InMemoryStoragePlatform()
    seedStorage(platform)
    val demoIds = (1..11).map { "test-episode-$it" }

    withApplication(platform) { graph, _ ->
      val queue =
        checkNotNull(graph.userManager.userScope.value)
          .metroDependencyGraph<UserStorageBindings>()
          .queue
      val entries = queue.episodes.filterNotNull()
      val initial = entries.first { it.size >= demoIds.size }
      assertThat(initial.map { it.episode.id }).isEqualTo(demoIds)
      assertThat(initial.map { it.podcastTitle to it.episode.author }.distinct())
        .isEqualTo(
          listOf(
            "Small Science" to "Coral",
            "History on Foot" to "Daniel",
            "Design Notes" to "Karen",
          ),
        )
      for (entry in initial) {
        val uri = checkNotNull(FakePodcasts.audioUri(entry.episode.enclosure.url))
        val recording = URI(uri).toURL().openStream().use { it.readBytes() }
        assertThat(recording.size > 1024).isTrue()
      }

      val player = graph.player()
      val playback = player.state.first { it.upcomingEpisodes.size == initial.lastIndex }
      assertThat(playback.episode).isEqualTo(initial.first())
      assertThat(playback.upcomingEpisodes).isEqualTo(initial.drop(1))
      assertThat(playback.isPlaying).isFalse()
      assertThat(playback.positionMillis).isEqualTo(0L)
      assertThat(playback.durationMillis).isGreaterThan(0L)
      assertThat(playback.speed).isEqualTo(1f)

      val first = initial.first()
      val last = initial.last()
      queue.move(episode = last, target = first)
      val reordered = entries.first { it.firstOrNull() == last }
      assertThat(reordered).isEqualTo(listOf(last) + initial.dropLast(1))
      player.state.first { it.episode == last }

      val undo = CompletableDeferred<() -> Unit>()
      queue.removeWithUndo(last) { undo.complete(it) }
      assertThat(entries.first { it.size == initial.lastIndex }).isEqualTo(initial.dropLast(1))
      player.state.first { it.episode == first }
      undo.await()()
      assertThat(entries.first { it.size == initial.size }).isEqualTo(reordered)

      queue.remove(last)
      entries.first { it.size == initial.lastIndex }
    }

    withApplication(platform) { graph, _ ->
      val queue =
        checkNotNull(graph.userManager.userScope.value)
          .metroDependencyGraph<UserStorageBindings>()
          .queue
      val seeded = queue.episodes.filterNotNull().first { it.size >= demoIds.size }
      assertThat(seeded.map { it.episode.id }).isEqualTo(demoIds)
      graph.runtimeModeController.switchMode(RuntimeMode.Real)
    }

    withApplication(platform) { graph, _ ->
      val queue =
        checkNotNull(graph.userManager.userScope.value)
          .metroDependencyGraph<UserStorageBindings>()
          .queue
      assertThat(queue.episodes.filterNotNull().first()).isEqualTo(emptyList())
    }
  }

  @Test
  fun `fake app and user storage start empty and reset after application recreation`() = runTest {
    val platform = InMemoryStoragePlatform()
    seedStorage(platform)

    withApplication(platform) { graph, restarter ->
      assertThat(graph.runtimeModeController.mode).isEqualTo(RuntimeMode.Fake)
      graph.storage.assertData(null)
      graph.userStorage().assertData(null)

      graph.storage.writeData("fake app")
      graph.userStorage().writeData("fake guest")

      graph.storage.assertData("fake app")
      graph.userStorage().assertData("fake guest")
      assertThat(restarter.restartCount).isEqualTo(0)
    }

    withRawStorage(platform, "app") { it.assertData("saved app") }
    withRawStorage(platform, "user-guest") { it.assertData("saved guest") }

    withApplication(platform) { graph, restarter ->
      assertThat(graph.runtimeModeController.mode).isEqualTo(RuntimeMode.Fake)
      graph.storage.assertData(null)
      graph.userStorage().assertData(null)
      assertThat(restarter.restartCount).isEqualTo(0)
    }
  }

  @Test
  fun `switching to real preserves saved data and keeps fake storage until restart`() = runTest {
    val platform = InMemoryStoragePlatform()
    seedStorage(platform)

    withApplication(platform) { graph, restarter ->
      graph.runtimeModeController.switchMode(RuntimeMode.Real)

      assertThat(graph.runtimeModeController.mode).isEqualTo(RuntimeMode.Fake)
      assertThat(restarter.restartCount).isEqualTo(1)
      graph.storage.writeData("outgoing fake app")
      graph.userStorage().writeData("outgoing fake guest")
    }

    withApplication(platform) { graph, restarter ->
      assertThat(graph.runtimeModeController.mode).isEqualTo(RuntimeMode.Real)
      graph.storage.assertData("saved app")
      graph.userStorage().assertData("saved guest")
      graph.storage.writeData("updated app")
      graph.userStorage().writeData("updated guest")
      assertThat(restarter.restartCount).isEqualTo(0)
    }

    withApplication(platform) { graph, _ ->
      assertThat(graph.runtimeModeController.mode).isEqualTo(RuntimeMode.Real)
      graph.storage.assertData("updated app")
      graph.userStorage().assertData("updated guest")
    }
  }

  private suspend fun TestScope.seedStorage(platform: StoragePlatform) {
    withRawStorage(platform, "app") { it.writeData("saved app") }
    withRawStorage(platform, "runtime-mode") { storage ->
      storage.preferences("runtime_mode").edit {
        it[stringPreferencesKey("runtime_mode")] = RuntimeMode.Fake.name
      }
    }
    withRawStorage(platform, "user-guest") { it.writeData("saved guest") }
  }

  private suspend fun ReceiveTurbine<BaseModel>.awaitPlayback(
    visible: Boolean,
  ): AppTemplate.AdaptiveTemplate {
    while (true) {
      val model = awaitItem() as AppTemplate.AdaptiveTemplate
      if ((model.playback != null) == visible) {
        return model
      }
    }
  }

  private suspend fun TestScope.withRawStorage(
    platform: StoragePlatform,
    namespace: String,
    block: suspend (Storage) -> Unit,
  ) {
    val scope = Scope.buildTestScope(this)
    val storage: Storage = DefaultStorage(namespace, scope.coroutineScope(), platform)
    try {
      block(storage)
    } finally {
      scope.destroy()
    }
  }

  private suspend fun withApplication(
    platform: StoragePlatform,
    block: suspend (Graph, FakeApplicationRestarter) -> Unit,
  ) {
    val restarter = FakeApplicationRestarter()
    val application = KmpApplication {
      createGraphFactory<Graph.Factory>().create(it, platform, restarter)
    }
    try {
      application.awaitReady()
      block(application.rootScope.metroDependencyGraph<Graph>(), restarter)
    } finally {
      application.destroy()
    }
  }

  private fun Graph.userStorage(): Storage =
    checkNotNull(userManager.userScope.value).metroDependencyGraph<UserStorageBindings>().storage

  private fun Graph.player(): Player =
    checkNotNull(userManager.userScope.value).metroDependencyGraph<UserStorageBindings>().player

  private suspend fun Storage.writeData(value: String) {
    preferences("settings").edit { it[stringPreferencesKey("value")] = value }
    files.directory("nested").write("value", "$value-file".encodeToByteArray())
    cache.directory("nested").write("value", "$value-cache".encodeToByteArray())
  }

  private suspend fun Storage.assertData(value: String?) {
    assertThat(preferences("settings").data.first()[stringPreferencesKey("value")]).isEqualTo(value)
    assertThat(files.directory("nested").read("value")?.decodeToString())
      .isEqualTo(value?.let { "$it-file" })
    assertThat(cache.directory("nested").read("value")?.decodeToString())
      .isEqualTo(value?.let { "$it-cache" })
  }

  @DependencyGraph(
    AppScope::class,
    excludes =
      [
        DesktopStoragePlatform::class,
        TestStorageBindings::class,
        TestRuntimeModeController::class,
        TestPlayer::class,
        DefaultApplicationRestarter::class,
      ],
  )
  interface Graph : AppGraph {
    val runtimeModeController: RuntimeModeController

    val appRootPresenter: AppRootPresenter

    @DependencyGraph.Factory
    fun interface Factory {
      fun create(
        @Provides rootScopeProvider: RootScopeProvider,
        @Provides storagePlatform: StoragePlatform,
        @Provides restarter: ApplicationRestarter,
      ): Graph
    }
  }

  @ContributesTo(UserScope::class)
  interface UserStorageBindings {
    @ForScope(UserScope::class) val storage: Storage

    val player: Player

    val queue: QueueRepository
  }
}
