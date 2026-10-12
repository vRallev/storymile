package software.ralf.storymile.playlist

import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.SingleIn
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import software.ralf.app.platform.inject.metro.ContributesScoped
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.Scoped
import software.ralf.app.platform.scope.coroutine.launch
import software.ralf.storymile.logging.Logger.Companion.logger
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.RuntimeModeController
import software.ralf.storymile.storage.Storage
import software.ralf.storymile.user.UserScope

/**
 * One repository per user scope. A single scope-owned coroutine loads the versioned document from
 * `playlists/playlists.json` in user storage, then consumes queued mutations in submission order.
 * Each command transforms the last saved snapshot and writes the document before publishing it.
 *
 * Load and command failures are logged. Failed loads are retried before later commands, so unread
 * data is never overwritten. Failed commands preserve the published snapshot and leave the queue
 * usable. Cancellation propagates; scope exit cancels the processor and discards queued commands
 * without deleting saved data.
 */
@SingleIn(UserScope::class)
@ContributesScoped(UserScope::class)
class PlaylistRepositoryImpl(
  @ForScope(UserScope::class) storage: Storage,
  private val runtimeModeController: RuntimeModeController,
) : PlaylistRepository, Scoped {
  override val playlists: StateFlow<List<Playlist>>
    field = MutableStateFlow(emptyList())

  private val files = storage.files.directory("playlists")
  private val document = PlaylistDocument()
  private val commands = Channel<(List<Playlist>) -> List<Playlist>>(Channel.UNLIMITED)
  private var loaded = false

  override fun onEnterScope(scope: Scope) {
    scope.launch {
      try {
        reportFailure { load() }
        for (command in commands) {
          reportFailure {
            load()
            val current = playlists.value
            val updated = command(current)
            if (updated != current) {
              files.write(fileName, document.encode(updated))
              currentCoroutineContext().ensureActive()
              playlists.value = updated
            }
          }
        }
      } finally {
        commands.cancel()
      }
    }
  }

  override fun onExitScope() {
    commands.cancel()
  }

  override fun create(name: PlaylistName) {
    enqueue { it + Playlist(PlaylistId(Uuid.random().toString()), name, emptyList()) }
  }

  override fun rename(id: PlaylistId, name: PlaylistName) {
    updatePlaylist(id) { it.copy(name = name) }
  }

  override fun delete(id: PlaylistId) {
    enqueue { playlists -> playlists.filterNot { it.id == id } }
  }

  override fun addEpisode(playlistId: PlaylistId, episodeId: EpisodeId) {
    updatePlaylist(playlistId) {
      if (episodeId in it.episodeIds) {
        it
      } else {
        it.copy(episodeIds = it.episodeIds + episodeId)
      }
    }
  }

  override fun removeEpisode(playlistId: PlaylistId, episodeId: EpisodeId) {
    enqueue { playlists ->
      playlists.map {
        if (it.id == playlistId) {
          it.copy(episodeIds = it.episodeIds - episodeId)
        } else {
          it
        }
      }
    }
  }

  override fun moveEpisode(
    playlistId: PlaylistId,
    episodeId: EpisodeId,
    beforeEpisodeId: EpisodeId?,
  ) {
    updatePlaylist(playlistId) { playlist ->
      require(episodeId in playlist.episodeIds) { "Episode is not in the playlist." }
      require(beforeEpisodeId == null || beforeEpisodeId in playlist.episodeIds) {
        "The destination episode is not in the playlist."
      }
      if (episodeId == beforeEpisodeId) {
        playlist
      } else {
        val episodes = playlist.episodeIds.toMutableList()
        episodes.remove(episodeId)
        val index = beforeEpisodeId?.let { episodes.indexOf(it) } ?: episodes.size
        episodes.add(index, episodeId)
        playlist.copy(episodeIds = episodes.toList())
      }
    }
  }

  private fun enqueue(command: (List<Playlist>) -> List<Playlist>) {
    commands.trySend(command).getOrThrow()
  }

  private fun updatePlaylist(id: PlaylistId, update: (Playlist) -> Playlist) {
    enqueue { playlists ->
      require(playlists.any { it.id == id }) { "Playlist does not exist." }
      playlists.map { if (it.id == id) update(it) else it }
    }
  }

  private suspend fun load() {
    if (!loaded) {
      val saved = files.read(fileName)?.let { document.decode(it) } ?: defaultPlaylists()
      currentCoroutineContext().ensureActive()
      playlists.value = saved
      loaded = true
    }
  }

  private suspend fun defaultPlaylists(): List<Playlist> {
    if (runtimeModeController.mode() != RuntimeMode.Fake) {
      return emptyList()
    }
    val defaults =
      listOf(
        Playlist(
          PlaylistId("storymile-test-episodes"),
          PlaylistName("Storymile test episodes"),
          (1..3).map { EpisodeId("test-episode-$it") },
        ),
      )
    files.write(fileName, document.encode(defaults))
    return defaults
  }

  @Suppress("TooGenericExceptionCaught") // Report command failures while preserving cancellation.
  private suspend fun reportFailure(block: suspend () -> Unit) {
    try {
      block()
    } catch (exception: Throwable) {
      if (exception is CancellationException) {
        throw exception
      }
      logger.e(exception) { "Could not load or update playlists." }
    }
  }

  private companion object {
    const val fileName = "playlists.json"
  }
}
