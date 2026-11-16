package software.ralf.storymile.player

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
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
import software.ralf.storymile.podcast.QueueRepository
import software.ralf.storymile.podcast.QueuedEpisode
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.RuntimeModeController
import software.ralf.storymile.user.UserScope

@SingleIn(UserScope::class)
@ContributesScoped(UserScope::class)
class PlayerImpl
internal constructor(
  private val audioPlayer: Lazy<AudioPlayer>,
  private val runtimeModeController: RuntimeModeController,
  private val queueRepository: QueueRepository,
  @MainCoroutineDispatcher private val mainDispatcher: CoroutineDispatcher,
  private val resourceUri: (String) -> String,
) : Player, Scoped {
  override val state: StateFlow<Player.State>
    field = MutableStateFlow(Player.State())

  private val audioReady = CompletableDeferred<AudioPlayer>()
  private val played = mutableSetOf<EpisodeKey>()
  private var queue = emptyList<QueuedEpisode>()
  private var source: String? = null
  private var closed = false
  private var playRequested = false
  private var observedPlayIntent = false

  @Inject
  @OptIn(ExperimentalResourceApi::class)
  constructor(
    audioPlayer: Lazy<AudioPlayer>,
    runtimeModeController: RuntimeModeController,
    queueRepository: QueueRepository,
    @MainCoroutineDispatcher mainDispatcher: CoroutineDispatcher,
  ) : this(
    audioPlayer = audioPlayer,
    runtimeModeController = runtimeModeController,
    queueRepository = queueRepository,
    mainDispatcher = mainDispatcher,
    resourceUri = { Res.getUri(it) },
  )

  override fun onEnterScope(scope: Scope) {
    scope.launch(mainDispatcher) {
      queueRepository.episodes.collect { episodes ->
        if (episodes != null && !closed) {
          updateQueue(episodes)
        }
      }
    }
    scope.launch(mainDispatcher) {
      audioReady.await().state.collect { updateAudioState(it) }
    }
  }

  override fun onExitScope() {
    closed = true
    if (audioPlayer.isInitialized()) {
      audioPlayer.value.close()
    }
    state.update { it.copy(isPlaying = false, playWhenReady = false) }
  }

  override fun togglePlayback() {
    if (closed) {
      return
    }
    val snapshot = state.value
    val current = snapshot.episode ?: return
    when {
      snapshot.isComplete -> {
        played.clear()
        queue.firstOrNull()?.let { start(it) }
      }
      source == null || snapshot.error != null -> start(current)
      else -> {
        playRequested = !playRequested
        state.update { it.copy(playWhenReady = playRequested) }
        if (playRequested) {
          handleAudioFailure { audioPlayer.value.play() }
        } else {
          audioPlayer.value.pause()
        }
      }
    }
  }

  override fun play(episode: QueuedEpisode) {
    if (closed) {
      return
    }
    val selected = queue.find { it.key == episode.key } ?: return
    val snapshot = state.value
    if (snapshot.isComplete) {
      played.clear()
    }
    played.remove(selected.key)
    if (
      snapshot.episode?.key == selected.key &&
        source != null &&
        !snapshot.isComplete &&
        snapshot.error == null
    ) {
      playRequested = true
      state.update { it.copy(playWhenReady = true) }
      handleAudioFailure { audioPlayer.value.play() }
    } else {
      start(selected)
    }
  }

  override fun seekTo(positionMillis: Long) {
    val snapshot = state.value
    if (!closed && snapshot.isReady && !snapshot.isComplete) {
      audioPlayer.value.seekTo(positionMillis.coerceIn(0, snapshot.durationMillis))
    }
  }

  override fun setSpeed(speed: Float) {
    if (closed || !speed.isFinite()) {
      return
    }
    val supportedSpeed = speed.coerceIn(0.75f, 2f)
    if (audioPlayer.isInitialized()) {
      audioPlayer.value.setSpeed(supportedSpeed)
    }
    state.update { it.copy(speed = supportedSpeed) }
  }

  private fun updateQueue(episodes: List<QueuedEpisode>) {
    queue = episodes
    played.retainAll(queue.map { it.key }.toSet())
    val snapshot = state.value
    val selected = queue.find { it.key == snapshot.episode?.key }
    val next = queue.firstOrNull { it.key !in played }
    when {
      queue.isEmpty() -> selectPaused(null)
      source == null -> selectPaused(next ?: queue.first())
      selected == null -> {
        if (playRequested && next != null) {
          start(next)
        } else {
          if (next == null) {
            played.clear()
          }
          selectPaused(next ?: queue.first())
        }
      }
      snapshot.isComplete && next != null -> selectPaused(next)
      else -> state.update { it.copy(episode = selected, upcomingEpisodes = upcoming(selected)) }
    }
  }

  private fun updateAudioState(audio: AudioPlayer.State) {
    val snapshot = state.value
    val current = snapshot.episode ?: return
    if (closed || source == null || audio.source != source || snapshot.isComplete) {
      return
    }
    if (observedPlayIntent && !audio.isPlaying && !audio.playWhenReady && !audio.hasEnded) {
      playRequested = false
    }
    observedPlayIntent = audio.playWhenReady || audio.isPlaying
    if (audio.hasEnded && audio.error == null) {
      // The repository can publish a reorder before its collector has run.
      val latest = queueRepository.episodes.value ?: return
      if (latest.none { it.key == current.key }) {
        return
      }
      queue = latest
      played += current.key
      val next = upcoming(current).firstOrNull()
      if (next != null) {
        if (playRequested) {
          start(next)
        } else {
          selectPaused(next)
        }
        return
      }
      playRequested = false
    }
    if (audio.error != null) {
      playRequested = false
    }
    state.update {
      it.copy(
        positionMillis = audio.positionMillis,
        durationMillis =
          audio.durationMillis.takeIf { duration -> duration > 0 }
            ?: (current.episode.duration?.inWholeMilliseconds ?: 0),
        isPlaying = audio.isPlaying,
        playWhenReady = playRequested,
        isReady = audio.isReady,
        error = audio.error,
        isComplete = audio.hasEnded && audio.error == null,
      )
    }
  }

  private fun selectPaused(episode: QueuedEpisode?) {
    val hadSource = source != null
    source = null
    playRequested = false
    observedPlayIntent = false
    state.value = selection(episode)
    if (hadSource && audioPlayer.isInitialized()) {
      audioPlayer.value.clear()
    }
  }

  private fun start(episode: QueuedEpisode) {
    state.value = selection(episode).copy(playWhenReady = true)
    observedPlayIntent = false
    playRequested = true
    handleAudioFailure {
      val uri = enclosureUri(episode.episode.enclosure.url)
      source = uri
      val audio = audioPlayer.value
      audio.load(uri)
      audio.setSpeed(state.value.speed)
      audioReady.complete(audio)
      audio.play()
    }
  }

  private fun selection(episode: QueuedEpisode?): Player.State =
    Player.State(
      episode = episode,
      upcomingEpisodes = upcoming(episode),
      durationMillis = episode?.episode?.duration?.inWholeMilliseconds ?: 0,
      speed = state.value.speed,
    )

  private fun upcoming(episode: QueuedEpisode?): List<QueuedEpisode> = queue.filter {
    it.key != episode?.key && it.key !in played
  }

  private fun enclosureUri(url: String): String {
    if (runtimeModeController.mode == RuntimeMode.Fake) {
      for (number in 1..3) {
        val file = "test-episode-$number.mp3"
        if (url == "https://storymile.invalid/test-episodes/$file") {
          return resourceUri("files/test-episodes/$file")
        }
      }
    }
    return url
  }

  private inline fun handleAudioFailure(action: () -> Unit) {
    try {
      action()
    } catch (exception: Exception) {
      if (exception is CancellationException) {
        throw exception
      }
      playRequested = false
      state.update {
        it.copy(
          isPlaying = false,
          playWhenReady = false,
          isReady = false,
          error = exception.message ?: "Playback failed",
        )
      }
    }
  }

  private val QueuedEpisode.key: EpisodeKey
    get() = EpisodeKey(feedUrl = feedUrl, episodeId = episode.id)

  private data class EpisodeKey(val feedUrl: String, val episodeId: String)
}
