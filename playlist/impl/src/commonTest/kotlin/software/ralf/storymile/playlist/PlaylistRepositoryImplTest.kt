package software.ralf.storymile.playlist

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.runTestWithScope
import software.ralf.storymile.storage.FileStorage
import software.ralf.storymile.storage.Storage

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistRepositoryImplTest {
  @Test
  fun `playlists retain their identities and episode order when reloaded`() =
    runTestWithScope { scope ->
      val storage = FakeStorage()
      val repository: PlaylistRepository = scope.repository(storage)
      repository.create("  Favorites  ")
      repository.create("Favorites")
      runCurrent()
      val first = repository.playlists.value.first()
      val second = repository.playlists.value.last()
      assertThat(first.id).isNotEqualTo(second.id)
      assertThat(repository.playlists.value.map { it.name })
        .isEqualTo(listOf("Favorites", "Favorites"))

      repository.addEpisode(first.id, EpisodeId("one"))
      repository.addEpisode(first.id, EpisodeId("two"))
      repository.addEpisode(second.id, EpisodeId("one"))
      repository.rename(first.id, "  Listen later  ")
      runCurrent()
      val saved = repository.playlists.value
      val reopened: PlaylistRepository = scope.repository(storage)
      runCurrent()
      assertThat(reopened.playlists.value).isEqualTo(saved)
      assertThat(reopened.playlists.value.first().name).isEqualTo("Listen later")
      assertThat(reopened.playlists.value.first().episodeIds)
        .isEqualTo(listOf(EpisodeId("one"), EpisodeId("two")))
    }

  @Test
  fun `membership commands preserve order and prevent repeats within each playlist`() =
    runTestWithScope { scope ->
      val repository: PlaylistRepository = scope.repository(FakeStorage())
      repository.create("Favorites")
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
      val storage = FakeStorage()
      val repository: PlaylistRepository = scope.repository(storage)
      repository.create("First")
      repository.create("Second")
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
        .isEqualTo(listOf(Playlist(second, "Second", listOf(episode))))
      val reopened: PlaylistRepository = scope.repository(storage)
      runCurrent()
      assertThat(reopened.playlists.value).isEqualTo(repository.playlists.value)
    }

  @Test
  fun `commands wait for loading and apply to the saved playlists in submission order`() =
    runTestWithScope { scope ->
      val storage = FakeStorage(SAVED_PLAYLISTS)
      val readBarrier = CompletableDeferred<Unit>()
      storage.readBarrier = readBarrier
      val repository: PlaylistRepository = scope.repository(storage)
      runCurrent()
      repository.rename(PlaylistId("saved"), "Renamed")
      repository.addEpisode(PlaylistId("saved"), EpisodeId("two"))
      repository.create("New")
      runCurrent()

      assertThat(repository.playlists.value).isEmpty()
      assertThat(storage.readCount).isEqualTo(1)
      assertThat(storage.writeCount).isEqualTo(0)
      readBarrier.complete(Unit)
      runCurrent()

      assertThat(repository.playlists.value.map { it.name }).isEqualTo(listOf("Renamed", "New"))
      assertThat(repository.playlists.value.first())
        .isEqualTo(
          Playlist(PlaylistId("saved"), "Renamed", listOf(EpisodeId("one"), EpisodeId("two"))),
        )
    }

  @Test
  fun `a playlist becomes observable only after its write completes`() = runTestWithScope { scope ->
    val storage = FakeStorage()
    val writeBarrier = CompletableDeferred<Unit>()
    storage.writeBarrier = writeBarrier
    val repository: PlaylistRepository = scope.repository(storage)

    repository.playlists.test {
      assertThat(awaitItem()).isEmpty()
      repository.create("Favorites")
      runCurrent()
      assertThat(storage.writeStarted.isCompleted).isTrue()
      assertThat(storage.writeCount).isEqualTo(0)
      expectNoEvents()

      writeBarrier.complete(Unit)
      runCurrent()
      assertThat(awaitItem().single().name).isEqualTo("Favorites")
      assertThat(storage.writeCount).isEqualTo(1)
    }
  }

  @Test
  fun `a failed write retains saved state and later commands can succeed`() =
    runTestWithScope { scope ->
      val storage = FakeStorage(SAVED_PLAYLISTS)
      val repository: PlaylistRepository = scope.repository(storage)
      runCurrent()
      val saved = repository.playlists.value
      storage.writeFailure = IllegalStateException("Storage unavailable")

      repository.rename(PlaylistId("saved"), "Unsaved")
      repository.addEpisode(PlaylistId("saved"), EpisodeId("unsaved"))
      runCurrent()

      assertThat(repository.playlists.value).isEqualTo(saved)
      assertThat(storage.savedDocument).isEqualTo(SAVED_PLAYLISTS)
      storage.writeFailure = null
      repository.addEpisode(PlaylistId("saved"), EpisodeId("two"))
      runCurrent()

      assertThat(repository.playlists.value.single())
        .isEqualTo(
          Playlist(PlaylistId("saved"), "Saved", listOf(EpisodeId("one"), EpisodeId("two"))),
        )
      val reopened: PlaylistRepository = scope.repository(storage)
      runCurrent()
      assertThat(reopened.playlists.value).isEqualTo(repository.playlists.value)
    }

  @Test
  fun `a failed read is retried before a later command can update saved playlists`() =
    runTestWithScope { scope ->
      val storage = FakeStorage(SAVED_PLAYLISTS)
      storage.readFailure = IllegalStateException("Storage unavailable")
      val repository: PlaylistRepository = scope.repository(storage)
      runCurrent()
      repository.create("Unsaved")
      runCurrent()

      assertThat(repository.playlists.value).isEmpty()
      assertThat(storage.savedDocument).isEqualTo(SAVED_PLAYLISTS)
      assertThat(storage.writeCount).isEqualTo(0)
      storage.readFailure = null
      repository.create("Recovered")
      runCurrent()

      assertThat(repository.playlists.value.map { it.name }).isEqualTo(listOf("Saved", "Recovered"))
      assertThat(repository.playlists.value.first().episodeIds).isEqualTo(listOf(EpisodeId("one")))
    }

  @Test
  fun `corrupt and unsupported saved documents are never overwritten by commands`() =
    runTestWithScope { scope ->
      for (document in listOf("{broken", """{"version":2,"playlists":[]}""")) {
        val storage = FakeStorage(document)
        val repository: PlaylistRepository = scope.repository(storage)
        runCurrent()
        repository.create("New")
        repository.delete(PlaylistId("saved"))
        runCurrent()

        assertThat(repository.playlists.value).isEmpty()
        assertThat(storage.savedDocument).isEqualTo(document)
        assertThat(storage.writeCount).isEqualTo(0)
      }
    }

  @Test
  fun `invalid moves preserve membership and do not prevent later commands`() =
    runTestWithScope { scope ->
      val repository: PlaylistRepository = scope.repository(FakeStorage(SAVED_PLAYLISTS))
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
      val repository: PlaylistRepository = scope.repository(FakeStorage())
      val missing = PlaylistId("missing")
      repository.rename(missing, "Renamed")
      repository.addEpisode(missing, EpisodeId("one"))
      repository.delete(missing)
      repository.removeEpisode(missing, EpisodeId("one"))
      repository.create("Created")
      runCurrent()

      assertThat(repository.playlists.value.map { it.name }).isEqualTo(listOf("Created"))
      assertThat(repository.playlists.value.single().episodeIds).isEmpty()
    }

  @Test
  fun `blank names fail immediately without changing saved playlists`() =
    runTestWithScope { scope ->
      val storage = FakeStorage(SAVED_PLAYLISTS)
      val repository: PlaylistRepository = scope.repository(storage)
      runCurrent()

      for (name in listOf("", " \t\n ")) {
        assertFailure { repository.create(name) }.isInstanceOf<IllegalArgumentException>()
        assertFailure { repository.rename(PlaylistId("saved"), name) }
          .isInstanceOf<IllegalArgumentException>()
      }
      runCurrent()

      assertThat(repository.playlists.value.single().name).isEqualTo("Saved")
      assertThat(storage.savedDocument).isEqualTo(SAVED_PLAYLISTS)
      assertThat(storage.writeCount).isEqualTo(0)
    }

  @Test
  fun `scope exit cancels pending writes and queued commands and rejects later mutations`() =
    runTestWithScope { scope ->
      val storage = FakeStorage(SAVED_PLAYLISTS)
      val repository: PlaylistRepository = scope.repository(storage)
      runCurrent()
      val saved = repository.playlists.value
      val writeBarrier = CompletableDeferred<Unit>()
      storage.writeBarrier = writeBarrier
      repository.rename(PlaylistId("saved"), "Pending")
      repository.create("Queued")
      runCurrent()
      assertThat(storage.writeStarted.isCompleted).isTrue()

      scope.destroy()
      runCurrent()
      writeBarrier.complete(Unit)
      runCurrent()

      assertThat(storage.writeCanceled.isCompleted).isTrue()
      assertThat(storage.savedDocument).isEqualTo(SAVED_PLAYLISTS)
      assertThat(repository.playlists.value).isEqualTo(saved)
      val id = PlaylistId("saved")
      val episode = EpisodeId("one")
      assertFailure { repository.create("Later") }.isInstanceOf<CancellationException>()
      assertFailure { repository.rename(id, "Later") }.isInstanceOf<CancellationException>()
      assertFailure { repository.delete(id) }.isInstanceOf<CancellationException>()
      assertFailure { repository.addEpisode(id, episode) }.isInstanceOf<CancellationException>()
      assertFailure { repository.removeEpisode(id, episode) }.isInstanceOf<CancellationException>()
      assertFailure { repository.moveEpisode(id, episode, null) }
        .isInstanceOf<CancellationException>()
    }

  private fun Scope.repository(storage: Storage): PlaylistRepository =
    PlaylistRepositoryImpl(storage).also { register(it) }

  private companion object {
    const val SAVED_PLAYLISTS =
      """{"version":1,"playlists":[{"id":"saved","name":"Saved","episodeIds":["one"]}]}"""
  }

  private class FakeStorage(savedDocument: String? = null) : Storage {
    private val content = mutableMapOf<String, ByteArray>()

    var readBarrier: CompletableDeferred<Unit>? = null
    var writeBarrier: CompletableDeferred<Unit>? = null
    var readFailure: Exception? = null
    var writeFailure: Exception? = null
    var readCount = 0
      private set

    var writeCount = 0
      private set

    val writeStarted = CompletableDeferred<Unit>()
    val writeCanceled = CompletableDeferred<Unit>()

    val savedDocument: String?
      get() = content["playlists/playlists.json"]?.decodeToString()

    override val files: FileStorage = Files("")

    override val cache: FileStorage
      get() = error("Cache is not used")

    init {
      if (savedDocument != null) {
        content["playlists/playlists.json"] = savedDocument.encodeToByteArray()
      }
    }

    override suspend fun preferences(name: String): DataStore<Preferences> =
      error("Preferences are not used")

    private inner class Files(private val path: String) : FileStorage {
      override fun directory(name: String): FileStorage = Files("$path$name/")

      override suspend fun read(name: String): ByteArray? {
        readCount++
        readBarrier?.await()
        readFailure?.let { throw it }
        return content["$path$name"]?.copyOf()
      }

      override suspend fun write(name: String, content: ByteArray) {
        writeStarted.complete(Unit)
        try {
          writeBarrier?.await()
          writeFailure?.let { throw it }
          this@FakeStorage.content["$path$name"] = content.copyOf()
          writeCount++
        } catch (exception: CancellationException) {
          writeCanceled.complete(Unit)
          throw exception
        }
      }

      override suspend fun delete(name: String) {
        content.remove("$path$name")
      }

      override suspend fun deleteAll() {
        content.keys.removeAll { it.startsWith(path) }
      }
    }
  }
}
