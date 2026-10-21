package software.ralf.storymile.playlist

import app.cash.turbine.test
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEqualTo
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.runTestWithScope
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.testing.FakeRuntimeModeController
import software.ralf.storymile.storage.Storage
import software.ralf.storymile.storage.testing.FakeStorage

class PlaylistRepositoryImplTest {
  @Test
  fun `fake mode seeds three ordered episodes before queued edits`() = runTestWithScope { scope ->
    val storage = storage()
    val mode = RuntimeMode.Fake
    val readBarrier = CompletableDeferred<Unit>()
    storage.readBarrier = readBarrier
    val repository: PlaylistRepository = scope.repository(storage, mode)
    repository.create(PlaylistName("My playlist"))
    runCurrent()
    assertThat(repository.playlists.value).isEmpty()
    assertThat(storage.writeCount).isEqualTo(0)

    readBarrier.complete(Unit)
    runCurrent()
    val playlists = repository.playlists.value
    assertThat(playlists.map { it.name.value })
      .isEqualTo(listOf("Storymile test episodes", "My playlist"))
    assertThat(playlists.first().episodeIds)
      .isEqualTo(
        listOf(
          EpisodeId("test-episode-1"),
          EpisodeId("test-episode-2"),
          EpisodeId("test-episode-3"),
        ),
      )

    val reopened: PlaylistRepository = scope.repository(storage, mode)
    runCurrent()
    assertThat(reopened.playlists.value).isEqualTo(playlists)
    assertThat(storage.writeCount).isEqualTo(2)
  }

  @Test
  fun `deleting the default playlist does not reseed it in the same fake session`() =
    runTestWithScope { scope ->
      val storage = storage()
      val mode = RuntimeMode.Fake
      val repository: PlaylistRepository = scope.repository(storage, mode)
      runCurrent()
      repository.delete(repository.playlists.value.single().id)
      runCurrent()

      val reopened: PlaylistRepository = scope.repository(storage, mode)
      runCurrent()
      assertThat(reopened.playlists.value).isEmpty()
      assertThat(storage.writeCount).isEqualTo(2)
    }

  @Test
  fun `failed seeding is retried before applying a later command`() = runTestWithScope { scope ->
    val storage = storage().apply { writeFailure = IllegalStateException("Unavailable") }
    val repository: PlaylistRepository = scope.repository(storage, RuntimeMode.Fake)
    runCurrent()
    assertThat(repository.playlists.value).isEmpty()

    storage.writeFailure = null
    repository.create(PlaylistName("After retry"))
    runCurrent()
    assertThat(repository.playlists.value.map { it.name.value })
      .isEqualTo(listOf("Storymile test episodes", "After retry"))
  }

  @Test
  fun `real mode starts empty without writing a default playlist`() = runTestWithScope { scope ->
    val storage = storage()
    val repository: PlaylistRepository = scope.repository(storage)
    runCurrent()
    assertThat(repository.playlists.value).isEmpty()
    assertThat(storage.writeCount).isEqualTo(0)
  }

  @Test
  fun `playlists retain their identities and episode order when reloaded`() =
    runTestWithScope { scope ->
      val storage = storage()
      val repository: PlaylistRepository = scope.repository(storage)
      repository.create(PlaylistName("  Favorites  "))
      repository.create(PlaylistName("Favorites"))
      runCurrent()
      val first = repository.playlists.value.first()
      val second = repository.playlists.value.last()
      assertThat(first.id).isNotEqualTo(second.id)
      assertThat(repository.playlists.value.map { it.name.value })
        .isEqualTo(listOf("Favorites", "Favorites"))

      repository.addEpisode(first.id, EpisodeId("one"))
      repository.addEpisode(first.id, EpisodeId("two"))
      repository.addEpisode(second.id, EpisodeId("one"))
      repository.rename(first.id, PlaylistName("  Listen later  "))
      runCurrent()
      val saved = repository.playlists.value
      val reopened: PlaylistRepository = scope.repository(storage)
      runCurrent()
      assertThat(reopened.playlists.value).isEqualTo(saved)
      assertThat(reopened.playlists.value.first().name.value).isEqualTo("Listen later")
      assertThat(reopened.playlists.value.first().episodeIds)
        .isEqualTo(listOf(EpisodeId("one"), EpisodeId("two")))
    }

  @Test
  fun `membership commands preserve order and prevent repeats within each playlist`() =
    runTestWithScope { scope ->
      val repository: PlaylistRepository = scope.repository(storage())
      repository.create(PlaylistName("Favorites"))
      runCurrent()
      val id = repository.playlists.value.single().id
      val one = EpisodeId("one")
      val two = EpisodeId("two")
      val three = EpisodeId("three")

      repository.addEpisode(id, one)
      repository.addEpisode(id, two)
      repository.addEpisode(id, three)
      repository.addEpisode(id, two)
      runCurrent()
      assertThat(repository.playlists.value.single().episodeIds).isEqualTo(listOf(one, two, three))

      repository.moveEpisode(id, three, one)
      repository.moveEpisode(id, one, null)
      repository.moveEpisode(id, two, two)
      runCurrent()
      assertThat(repository.playlists.value.single().episodeIds).isEqualTo(listOf(three, two, one))

      repository.removeEpisode(id, two)
      repository.removeEpisode(id, two)
      runCurrent()
      assertThat(repository.playlists.value.single().episodeIds).isEqualTo(listOf(three, one))
    }

  @Test
  fun `removing membership and deleting a playlist preserve the other playlist`() =
    runTestWithScope { scope ->
      val storage = storage()
      val repository: PlaylistRepository = scope.repository(storage)
      repository.create(PlaylistName("First"))
      repository.create(PlaylistName("Second"))
      runCurrent()
      val first = repository.playlists.value.first().id
      val second = repository.playlists.value.last().id
      val episode = EpisodeId("shared")
      repository.addEpisode(first, episode)
      repository.addEpisode(second, episode)
      repository.removeEpisode(first, episode)
      repository.delete(first)
      repository.delete(first)
      repository.removeEpisode(first, episode)
      runCurrent()

      assertThat(repository.playlists.value)
        .isEqualTo(listOf(Playlist(second, PlaylistName("Second"), listOf(episode))))
      val reopened: PlaylistRepository = scope.repository(storage)
      runCurrent()
      assertThat(reopened.playlists.value).isEqualTo(repository.playlists.value)
    }

  @Test
  fun `commands wait for loading and apply to the saved playlists in submission order`() =
    runTestWithScope { scope ->
      val storage = storage(SAVED_PLAYLISTS)
      val readBarrier = CompletableDeferred<Unit>()
      storage.readBarrier = readBarrier
      val repository: PlaylistRepository = scope.repository(storage)
      runCurrent()
      repository.rename(PlaylistId("saved"), PlaylistName("Renamed"))
      repository.addEpisode(PlaylistId("saved"), EpisodeId("two"))
      repository.create(PlaylistName("New"))
      runCurrent()

      assertThat(repository.playlists.value).isEmpty()
      assertThat(storage.readCount).isEqualTo(1)
      assertThat(storage.writeCount).isEqualTo(0)
      readBarrier.complete(Unit)
      runCurrent()

      assertThat(repository.playlists.value.map { it.name.value })
        .isEqualTo(listOf("Renamed", "New"))
      assertThat(repository.playlists.value.first())
        .isEqualTo(
          Playlist(
            PlaylistId("saved"),
            PlaylistName("Renamed"),
            listOf(EpisodeId("one"), EpisodeId("two")),
          ),
        )
    }

  @Test
  fun `a playlist becomes observable only after its write completes`() = runTestWithScope { scope ->
    val storage = storage()
    val writeBarrier = CompletableDeferred<Unit>()
    storage.writeBarrier = writeBarrier
    val repository: PlaylistRepository = scope.repository(storage)

    repository.playlists.test {
      assertThat(awaitItem()).isEmpty()
      repository.create(PlaylistName("Favorites"))
      runCurrent()
      assertThat(storage.writeStarted.isCompleted).isTrue()
      assertThat(storage.writeCount).isEqualTo(0)
      expectNoEvents()

      writeBarrier.complete(Unit)
      runCurrent()
      assertThat(awaitItem().single().name.value).isEqualTo("Favorites")
      assertThat(storage.writeCount).isEqualTo(1)
    }
  }

  @Test
  fun `a failed write retains saved state and later commands can succeed`() =
    runTestWithScope { scope ->
      val storage = storage(SAVED_PLAYLISTS)
      val repository: PlaylistRepository = scope.repository(storage)
      runCurrent()
      val saved = repository.playlists.value
      storage.writeFailure = IllegalStateException("Storage unavailable")

      repository.rename(PlaylistId("saved"), PlaylistName("Unsaved"))
      repository.addEpisode(PlaylistId("saved"), EpisodeId("unsaved"))
      runCurrent()

      assertThat(repository.playlists.value).isEqualTo(saved)
      assertThat(storage.fileContent("playlists/playlists.json")?.decodeToString())
        .isEqualTo(SAVED_PLAYLISTS)
      storage.writeFailure = null
      repository.addEpisode(PlaylistId("saved"), EpisodeId("two"))
      runCurrent()

      assertThat(repository.playlists.value.single())
        .isEqualTo(
          Playlist(
            PlaylistId("saved"),
            PlaylistName("Saved"),
            listOf(EpisodeId("one"), EpisodeId("two")),
          ),
        )
      val reopened: PlaylistRepository = scope.repository(storage)
      runCurrent()
      assertThat(reopened.playlists.value).isEqualTo(repository.playlists.value)
    }

  @Test
  fun `a failed read is retried before a later command can update saved playlists`() =
    runTestWithScope { scope ->
      val storage = storage(SAVED_PLAYLISTS)
      storage.readFailure = IllegalStateException("Storage unavailable")
      val repository: PlaylistRepository = scope.repository(storage)
      runCurrent()
      repository.create(PlaylistName("Unsaved"))
      runCurrent()

      assertThat(repository.playlists.value).isEmpty()
      assertThat(storage.fileContent("playlists/playlists.json")?.decodeToString())
        .isEqualTo(SAVED_PLAYLISTS)
      assertThat(storage.writeCount).isEqualTo(0)
      storage.readFailure = null
      repository.create(PlaylistName("Recovered"))
      runCurrent()

      assertThat(repository.playlists.value.map { it.name.value })
        .isEqualTo(listOf("Saved", "Recovered"))
      assertThat(repository.playlists.value.first().episodeIds).isEqualTo(listOf(EpisodeId("one")))
    }

  @Test
  fun `corrupt and unsupported saved documents are never overwritten by commands`() =
    runTestWithScope { scope ->
      for (document in
        listOf(
          "{broken",
          """{"version":2,"playlists":[]}""",
          SAVED_PLAYLISTS.replace("\"Saved\"", "\"  \""),
        )) {
        val storage = storage(document)
        val repository: PlaylistRepository = scope.repository(storage)
        runCurrent()
        repository.create(PlaylistName("New"))
        repository.delete(PlaylistId("saved"))
        runCurrent()

        assertThat(repository.playlists.value).isEmpty()
        assertThat(storage.fileContent("playlists/playlists.json")?.decodeToString())
          .isEqualTo(document)
        assertThat(storage.writeCount).isEqualTo(0)
      }
    }

  @Test
  fun `invalid moves preserve membership and do not prevent later commands`() =
    runTestWithScope { scope ->
      val repository: PlaylistRepository = scope.repository(storage(SAVED_PLAYLISTS))
      runCurrent()
      val id = PlaylistId("saved")
      repository.moveEpisode(id, EpisodeId("one"), EpisodeId("missing"))
      repository.moveEpisode(id, EpisodeId("missing"), EpisodeId("one"))
      repository.moveEpisode(PlaylistId("missing"), EpisodeId("one"), null)
      runCurrent()

      assertThat(repository.playlists.value.single().episodeIds).isEqualTo(listOf(EpisodeId("one")))
      repository.addEpisode(id, EpisodeId("two"))
      runCurrent()
      assertThat(repository.playlists.value.single().episodeIds)
        .isEqualTo(listOf(EpisodeId("one"), EpisodeId("two")))
    }

  @Test
  fun `commands for a missing playlist do not create it or prevent later commands`() =
    runTestWithScope { scope ->
      val repository: PlaylistRepository = scope.repository(storage())
      val missing = PlaylistId("missing")
      repository.rename(missing, PlaylistName("Renamed"))
      repository.addEpisode(missing, EpisodeId("one"))
      repository.delete(missing)
      repository.removeEpisode(missing, EpisodeId("one"))
      repository.create(PlaylistName("Created"))
      runCurrent()

      assertThat(repository.playlists.value.map { it.name.value }).isEqualTo(listOf("Created"))
      assertThat(repository.playlists.value.single().episodeIds).isEmpty()
    }

  @Test
  fun `scope exit cancels pending writes and queued commands and rejects later mutations`() =
    runTestWithScope { scope ->
      val storage = storage(SAVED_PLAYLISTS)
      val repository: PlaylistRepository = scope.repository(storage)
      runCurrent()
      val saved = repository.playlists.value
      val writeBarrier = CompletableDeferred<Unit>()
      storage.writeBarrier = writeBarrier
      repository.rename(PlaylistId("saved"), PlaylistName("Pending"))
      repository.create(PlaylistName("Queued"))
      runCurrent()
      assertThat(storage.writeStarted.isCompleted).isTrue()

      scope.destroy()
      runCurrent()
      writeBarrier.complete(Unit)
      runCurrent()

      assertThat(storage.writeCanceled.isCompleted).isTrue()
      assertThat(storage.fileContent("playlists/playlists.json")?.decodeToString())
        .isEqualTo(SAVED_PLAYLISTS)
      assertThat(repository.playlists.value).isEqualTo(saved)
      val id = PlaylistId("saved")
      val episode = EpisodeId("one")
      assertFailure { repository.create(PlaylistName("Later")) }
        .isInstanceOf<CancellationException>()
      assertFailure { repository.rename(id, PlaylistName("Later")) }
        .isInstanceOf<CancellationException>()
      assertFailure { repository.delete(id) }.isInstanceOf<CancellationException>()
      assertFailure { repository.addEpisode(id, episode) }.isInstanceOf<CancellationException>()
      assertFailure { repository.removeEpisode(id, episode) }.isInstanceOf<CancellationException>()
      assertFailure { repository.moveEpisode(id, episode, null) }
        .isInstanceOf<CancellationException>()
    }

  private fun Scope.repository(
    storage: Storage,
    mode: RuntimeMode = RuntimeMode.Real,
  ): PlaylistRepository =
    PlaylistRepositoryImpl(storage, FakeRuntimeModeController(mode)).also { register(it) }

  private fun storage(savedDocument: String? = null): FakeStorage =
    FakeStorage(
      initialFiles =
        savedDocument?.let {
          mapOf("playlists/playlists.json" to it.encodeToByteArray())
        } ?: emptyMap(),
    )

  private companion object {
    const val SAVED_PLAYLISTS =
      """{"version":1,"playlists":[{"id":"saved","name":"Saved","episodeIds":["one"]}]}"""
  }
}
