package software.ralf.storymile.player

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.compose.resources.ExperimentalResourceApi
import software.ralf.app.platform.inject.metro.ContributesScoped
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.Scoped
import software.ralf.app.platform.scope.coroutine.MainCoroutineDispatcher
import software.ralf.app.platform.scope.coroutine.launch
import software.ralf.storymile.player.impl.generated.resources.Res
import software.ralf.storymile.playlist.PlaylistRepository
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.RuntimeModeController
import software.ralf.storymile.user.UserScope

@SingleIn(UserScope::class)
@ContributesScoped(UserScope::class)
class PlayerImpl
internal constructor(
  private val audioPlayer: Lazy<AudioPlayer>,
  private val runtimeModeController: RuntimeModeController,
  private val playlistRepository: PlaylistRepository,
  @MainCoroutineDispatcher private val mainDispatcher: CoroutineDispatcher,
  private val sourceUri: (String) -> String,
) : Player, Scoped {
  override val state: StateFlow<Player.State>
    field = MutableStateFlow(Player.State())

  private var episodeIndex = 0
  private var source: String? = null
  private var closed = false
  private var playRequested = false
  private var observedPlayIntent = false

  @Inject
  @OptIn(ExperimentalResourceApi::class)
  constructor(
    audioPlayer: Lazy<AudioPlayer>,
    runtimeModeController: RuntimeModeController,
    playlistRepository: PlaylistRepository,
    @MainCoroutineDispatcher mainDispatcher: CoroutineDispatcher,
  ) : this(
    audioPlayer,
    runtimeModeController,
    playlistRepository,
    mainDispatcher,
    { Res.getUri(it) },
  )

  override fun onEnterScope(scope: Scope) {
    scope.launch(mainDispatcher) {
      runCatching {
        if (runtimeModeController.mode() != RuntimeMode.Fake || closed) {
          return@launch
        }
        loadEpisode(autoPlay = false)
        scope.launch(mainDispatcher) {
          playlistRepository.playlists.collect { playlists ->
            if (playlists.none { it.episodeIds.isNotEmpty() } && !closed) {
              playRequested = false
              audioPlayer.value.pause()
            }
          }
        }
        audioPlayer.value.state.collect { updateAudioState(it) }
      }
        .onFailure { exception ->
          if (exception is CancellationException) {
            throw exception
          }
          state.update {
            it.copy(isPlaying = false, error = exception.message ?: "Playback failed")
          }
        }
    }
  }

  override fun onExitScope() {
    closed = true
    if (audioPlayer.isInitialized()) {
      audioPlayer.value.close()
    }
    state.update { it.copy(isPlaying = false) }
  }

  override fun togglePlayback() {
    if (
      closed ||
        source == null ||
        state.value.isComplete ||
        state.value.error != null ||
        !hasEpisodes()
    ) {
      return
    }
    playRequested = !playRequested
    if (!playRequested) {
      audioPlayer.value.pause()
    } else {
      audioPlayer.value.play()
    }
  }

  override fun seekTo(positionMillis: Long) {
    if (!closed && source != null && state.value.isReady && hasEpisodes()) {
      audioPlayer.value.seekTo(positionMillis.coerceIn(0, state.value.durationMillis))
    }
  }

  override fun setSpeed(speed: Float) {
    if (closed || source == null || !speed.isFinite()) {
      return
    }
    val supportedSpeed = speed.coerceIn(0.75f, 2f)
    audioPlayer.value.setSpeed(supportedSpeed)
    state.update { it.copy(speed = supportedSpeed) }
  }

  private fun updateAudioState(audio: AudioPlayer.State) {
    if (audio.source != source || closed) {
      return
    }
    updatePlayIntent(audio)
    state.update {
      it.copy(
        positionMillis = audio.positionMillis,
        durationMillis = audio.durationMillis,
        isPlaying = audio.isPlaying,
        isReady = audio.isReady,
        error = audio.error,
        isComplete = audio.hasEnded && episodeIndex == 2,
      )
    }
    if (audio.error != null || state.value.isComplete) {
      playRequested = false
    }
    if (audio.hasEnded && audio.error == null && episodeIndex < 2) {
      episodeIndex++
      loadEpisode(autoPlay = playRequested && hasEpisodes())
    }
  }

  private fun updatePlayIntent(audio: AudioPlayer.State) {
    if (observedPlayIntent && !audio.isPlaying && !audio.playWhenReady && !audio.hasEnded) {
      playRequested = false
    }
    observedPlayIntent = audio.playWhenReady || audio.isPlaying
  }

  private fun loadEpisode(autoPlay: Boolean) {
    val number = episodeIndex + 1
    source = sourceUri("files/test-episodes/test-episode-$number.mp3")
    state.value = Player.State(title = "Test episode $number", speed = state.value.speed)
    observedPlayIntent = false
    audioPlayer.value.load(checkNotNull(source))
    audioPlayer.value.setSpeed(state.value.speed)
    playRequested = autoPlay
    if (autoPlay) {
      audioPlayer.value.play()
    }
  }

  private fun hasEpisodes(): Boolean =
    playlistRepository.playlists.value.any { it.episodeIds.isNotEmpty() }
}
