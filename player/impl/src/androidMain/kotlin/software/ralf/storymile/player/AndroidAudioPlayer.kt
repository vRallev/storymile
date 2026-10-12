package software.ralf.storymile.player

import android.app.Application
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player as Media3Player
import androidx.media3.exoplayer.ExoPlayer
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ForScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import software.ralf.app.platform.scope.coroutine.MainCoroutineDispatcher
import software.ralf.storymile.user.UserScope

@ContributesBinding(UserScope::class)
class AndroidAudioPlayer(
  private val application: Application,
  @ForScope(UserScope::class) private val coroutineScope: CoroutineScope,
  @MainCoroutineDispatcher private val mainDispatcher: CoroutineDispatcher,
) : AudioPlayer() {
  override val state: StateFlow<State>
    field = MutableStateFlow(State())

  private var player: ExoPlayer? = null
  private var listener: Media3Player.Listener? = null
  private var polling: Job? = null
  private var speed = 1f
  private var closed = false

  @Suppress("TooGenericExceptionCaught") // Report all platform playback failures.
  override fun load(source: String) {
    if (closed) {
      return
    }
    releasePlayer()
    state.value = State(source = source)
    try {
      val engine =
        ExoPlayer.Builder(application)
          .setAudioAttributes(
            AudioAttributes.Builder()
              .setUsage(C.USAGE_MEDIA)
              .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
              .build(),
            true,
          )
          .setHandleAudioBecomingNoisy(true)
          .build()
      player = engine
      val events =
        object : Media3Player.Listener {
          override fun onEvents(player: Media3Player, events: Media3Player.Events) {
            if (this@AndroidAudioPlayer.player === engine) {
              publish(engine)
            }
          }

          override fun onPlayerError(error: PlaybackException) {
            if (this@AndroidAudioPlayer.player === engine) {
              fail(error)
            }
          }
        }
      listener = events
      engine.addListener(events)
      engine.playbackParameters = PlaybackParameters(speed, 1f)
      engine.setMediaItem(MediaItem.fromUri(source))
      engine.prepare()
      polling =
        coroutineScope.launch(mainDispatcher) {
          while (isActive && player === engine) {
            publish(engine)
            delay(100)
          }
        }
    } catch (exception: Throwable) {
      fail(exception)
    }
  }

  override fun play() {
    command { engine ->
      if (engine.playbackState == Media3Player.STATE_ENDED) {
        engine.seekTo(0)
      }
      engine.play()
    }
  }

  override fun pause() {
    command { it.pause() }
  }

  override fun seekTo(positionMillis: Long) {
    command { engine ->
      val duration = engine.duration.takeIf { it >= 0 }
      val position = positionMillis.coerceAtLeast(0)
      engine.seekTo(duration?.let { position.coerceAtMost(it) } ?: position)
    }
  }

  override fun setSpeed(speed: Float) {
    if (!speed.isFinite() || speed <= 0 || closed) {
      return
    }
    this.speed = speed
    command { it.playbackParameters = PlaybackParameters(speed, 1f) }
  }

  override fun close() {
    if (player == null || Looper.myLooper() == Looper.getMainLooper()) {
      closeOnMain()
    } else {
      runBlocking(mainDispatcher) { closeOnMain() }
    }
  }

  private fun closeOnMain() {
    closed = true
    releasePlayer()
    state.value = state.value.copy(isPlaying = false, playWhenReady = false, isReady = false)
  }

  @Suppress("TooGenericExceptionCaught") // Report all platform playback failures.
  private fun command(block: (ExoPlayer) -> Unit) {
    val engine = player ?: return
    if (closed || state.value.error != null) {
      return
    }
    try {
      block(engine)
      publish(engine)
    } catch (exception: Throwable) {
      fail(exception)
    }
  }

  private fun publish(engine: ExoPlayer) {
    val duration = engine.duration.coerceAtLeast(0)
    state.value =
      state.value.copy(
        positionMillis = engine.currentPosition.coerceAtLeast(0).coerceAtMost(duration),
        durationMillis = duration,
        isPlaying = engine.isPlaying,
        playWhenReady = engine.playWhenReady && engine.playbackState != Media3Player.STATE_ENDED,
        isReady =
          engine.playbackState == Media3Player.STATE_READY ||
            engine.playbackState == Media3Player.STATE_ENDED,
        hasEnded = engine.playbackState == Media3Player.STATE_ENDED,
      )
  }

  private fun fail(error: Throwable) {
    releasePlayer()
    state.value =
      state.value.copy(
        isPlaying = false,
        playWhenReady = false,
        isReady = false,
        hasEnded = false,
        error = error.message ?: "Could not play audio.",
      )
  }

  private fun releasePlayer() {
    polling?.cancel()
    polling = null
    val previous = player
    player = null
    listener?.let { previous?.removeListener(it) }
    listener = null
    previous?.release()
  }
}
