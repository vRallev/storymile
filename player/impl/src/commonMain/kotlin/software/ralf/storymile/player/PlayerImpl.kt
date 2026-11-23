package software.ralf.storymile.player

import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import software.ralf.app.platform.inject.metro.ContributesScoped
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.Scoped
import software.ralf.app.platform.scope.coroutine.MainCoroutineDispatcher
import software.ralf.app.platform.scope.coroutine.launch
import software.ralf.storymile.podcast.QueueRepository
import software.ralf.storymile.podcast.QueuedEpisode
import software.ralf.storymile.user.UserScope

@SingleIn(UserScope::class)
@ContributesScoped(UserScope::class)
class PlayerImpl(
  private val audioPlayer: Lazy<AudioPlayer>,
  private val queueRepository: QueueRepository,
  private val audioSourceResolver: AudioSourceResolver,
  @MainCoroutineDispatcher private val mainDispatcher: CoroutineDispatcher,
) : Player, Scoped {
  override val state: StateFlow<Player.State>
    field = MutableStateFlow(Player.State())

  private val audioStarted = CompletableDeferred<AudioPlayer>()
  private val played = mutableSetOf<Pair<String, String>>()
  private val queue: List<QueuedEpisode>
    get() = queueRepository.episodes.value.orEmpty()

  private var source: String? = null
  private var closed = false
  private var playRequested = false
  private var observedPlayIntent = false

  override fun onEnterScope(scope: Scope) {
    scope.launch(mainDispatcher) {
      try {
        if (closed) {
          return@launch
        }
        scope.launch(mainDispatcher) {
          queueRepository.episodes.filterNotNull().collect { updateQueue(it) }
        }
        audioStarted.await().state.collect { updateAudioState(it) }
      } catch (exception: Throwable) {
        if (exception is CancellationException) {
          throw exception
        }
        state.update {
          it.copy(
            isPlaying = false,
            playWhenReady = false,
            error = exception.message ?: "Playback failed",
          )
        }
      }
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
    val current = state.value
    val episode = current.episode ?: return
    if (source == null || current.isComplete || current.error != null) {
      val next = if (current.isComplete) queue.firstOrNull() else episode
      next?.let { play(it) }
      return
    }
    playRequested = !playRequested
    state.update { it.copy(playWhenReady = playRequested) }
    if (!playRequested) {
      audioPlayer.value.pause()
    } else {
      audioPlayer.value.play()
    }
  }

  override fun play(episode: QueuedEpisode) {
    if (closed) {
      return
    }
    val selected = queue.find { it.matches(episode) } ?: return
    if (state.value.isComplete) {
      played.clear()
    }
    played.remove(selected.key)
    startQueuedEpisode(selected)
  }

  override fun seekTo(positionMillis: Long) {
    if (!closed && source != null && state.value.isReady && state.value.episode != null) {
      audioPlayer.value.seekTo(positionMillis.coerceIn(0, state.value.durationMillis))
    }
  }

  override fun setSpeed(speed: Float) {
    if (closed || !speed.isFinite() || (source == null && state.value.episode == null)) {
      return
    }
    val supportedSpeed = speed.coerceIn(0.75f, 2f)
    if (source != null) {
      audioPlayer.value.setSpeed(supportedSpeed)
    }
    state.update { it.copy(speed = supportedSpeed) }
  }

  private fun updateQueue(episodes: List<QueuedEpisode>) {
    if (closed) {
      return
    }
    if (played.isNotEmpty()) {
      played.retainAll(episodes.mapTo(mutableSetOf()) { it.key })
    }
    val snapshot = state.value
    val current = snapshot.episode?.let { previous -> episodes.find { it.matches(previous) } }
    val next = episodes.firstOrNull { !it.wasPlayed() }
    when {
      current != null && source != null && (!snapshot.isComplete || next == null) ->
        state.update { it.copy(episode = current, upcomingEpisodes = upcoming(current, episodes)) }
      current == null && playRequested && next != null -> startQueuedEpisode(next)
      next != null -> selectPaused(next)
      else -> {
        played.clear()
        selectPaused(episodes.firstOrNull())
      }
    }
  }

  private fun updateAudioState(audio: AudioPlayer.State) {
    if (closed || source == null || audio.source != source || state.value.isComplete) {
      return
    }
    updatePlayIntent(audio)
    val ended = audio.hasEnded && audio.error == null
    if (ended && advanceEpisode()) {
      return
    }
    if (ended || audio.error != null) {
      playRequested = false
    }
    state.update {
      it.copy(
        positionMillis = audio.positionMillis,
        durationMillis =
          audio.durationMillis.takeIf { duration -> duration > 0 }
            ?: (it.episode?.episode?.duration?.inWholeMilliseconds ?: 0),
        isPlaying = audio.isPlaying,
        playWhenReady = playRequested,
        isReady = audio.isReady,
        error = audio.error,
        isComplete = ended,
      )
    }
  }

  private fun advanceEpisode(): Boolean {
    val current = state.value.episode ?: return true
    val entries = queue
    if (entries.none { it.matches(current) }) {
      return true // The queue collector handles removal of the current episode.
    }
    played += current.key
    val next = entries.firstOrNull { !it.wasPlayed() } ?: return false
    if (playRequested) {
      startQueuedEpisode(next)
    } else {
      selectPaused(next)
    }
    return true
  }

  private fun updatePlayIntent(audio: AudioPlayer.State) {
    if (observedPlayIntent && !audio.isPlaying && !audio.playWhenReady && !audio.hasEnded) {
      playRequested = false
    }
    observedPlayIntent = audio.playWhenReady || audio.isPlaying
  }

  private fun startQueuedEpisode(episode: QueuedEpisode) {
    state.value = selection(episode).copy(playWhenReady = true)
    try {
      load(source = audioSourceResolver.resolve(episode.episode.enclosure.url), autoPlay = true)
    } catch (exception: Exception) {
      if (exception is CancellationException) {
        throw exception
      }
      playRequested = false
      state.update {
        it.copy(playWhenReady = false, error = exception.message ?: "Playback failed")
      }
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

  private fun selection(episode: QueuedEpisode?): Player.State =
    Player.State(
      title = episode?.episode?.title,
      durationMillis = episode?.episode?.duration?.inWholeMilliseconds ?: 0,
      speed = state.value.speed,
      episode = episode,
      upcomingEpisodes = episode?.let { upcoming(it, queue) }.orEmpty(),
    )

  private fun upcoming(episode: QueuedEpisode, entries: List<QueuedEpisode>): List<QueuedEpisode> =
    entries.filter {
      !it.matches(episode) && !it.wasPlayed()
    }

  private fun load(source: String, autoPlay: Boolean) {
    this.source = source
    observedPlayIntent = false
    playRequested = autoPlay
    val audio = audioPlayer.value
    audio.load(source)
    audio.setSpeed(state.value.speed)
    audioStarted.complete(audio)
    if (autoPlay) {
      audio.play()
    }
  }

  private fun QueuedEpisode.matches(other: QueuedEpisode): Boolean =
    feedUrl == other.feedUrl && episode.id == other.episode.id

  private fun QueuedEpisode.wasPlayed(): Boolean = played.isNotEmpty() && key in played

  private val QueuedEpisode.key: Pair<String, String>
    get() = feedUrl to episode.id
}
