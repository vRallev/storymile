package software.ralf.storymile.player

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ForScope
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionTypeBegan
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.AVAudioSessionRouteChangeNotification
import platform.AVFAudio.AVAudioSessionRouteChangeReasonKey
import platform.AVFAudio.AVAudioSessionRouteChangeReasonOldDeviceUnavailable
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.setActive
import platform.Foundation.NSError
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSThread
import platform.Foundation.NSURL
import platform.darwin.NSObject
import platform.darwin.NSObjectProtocol
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_sync
import software.ralf.app.platform.scope.coroutine.MainCoroutineDispatcher
import software.ralf.storymile.user.UserScope

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@ContributesBinding(UserScope::class)
class IosAudioPlayer(
  @ForScope(UserScope::class) private val coroutineScope: CoroutineScope,
  @MainCoroutineDispatcher private val mainDispatcher: CoroutineDispatcher,
) : AudioPlayer() {
  override val state: StateFlow<State>
    field = MutableStateFlow(State())

  private var player: AVAudioPlayer? = null
  private var delegate: Delegate? = null
  private var polling: Job? = null
  private var speed = 1f
  private var closed = false
  private var sessionActive = false
  // Native playback can stop before its completion delegate runs.
  private var playWhenReady = false
  private val pauseObservers = mutableListOf<NSObjectProtocol>()

  override fun load(source: String) {
    if (closed) {
      return
    }
    releasePlayer()
    state.value = State(source = source)
    try {
      val engine = memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        error.value = null
        val url = requireNotNull(NSURL.URLWithString(source)) { "Invalid audio URL." }
        val loaded = AVAudioPlayer(contentsOfURL = url, error = error.ptr)
        check(error.value == null) { error.value?.localizedDescription ?: "Could not load audio." }
        loaded
      }
      player = engine
      val events =
        Delegate(
          onEnd = { finished, success ->
            if (player === finished) {
              if (success) {
                playWhenReady = false
                deactivateSession()
                state.value =
                  state.value.copy(
                    positionMillis = state.value.durationMillis,
                    isPlaying = false,
                    playWhenReady = false,
                    hasEnded = true,
                  )
              } else {
                fail("Could not finish audio playback.")
              }
            }
          },
          onError = { failed, error ->
            if (player === failed) {
              fail(error?.localizedDescription ?: "Could not decode audio.")
            }
          },
        )
      delegate = events
      engine.delegate = events
      observePause(
        engine,
        requireNotNull(AVAudioSessionInterruptionNotification),
        requireNotNull(AVAudioSessionInterruptionTypeKey),
        AVAudioSessionInterruptionTypeBegan,
      )
      observePause(
        engine,
        requireNotNull(AVAudioSessionRouteChangeNotification),
        requireNotNull(AVAudioSessionRouteChangeReasonKey),
        AVAudioSessionRouteChangeReasonOldDeviceUnavailable,
      )
      engine.enableRate = true
      engine.rate = speed
      check(engine.prepareToPlay()) { "Could not prepare audio." }
      publish(engine)
      polling =
        coroutineScope.launch(mainDispatcher) {
          while (isActive && player === engine) {
            publish(engine)
            delay(100)
          }
        }
    } catch (exception: Throwable) {
      fail(exception.message ?: "Could not load audio.")
    }
  }

  override fun play() {
    command { engine ->
      if (state.value.hasEnded) {
        engine.currentTime = 0.0
        state.value = state.value.copy(hasEnded = false)
      }
      val session = AVAudioSession.sharedInstance()
      check(session.setCategory(AVAudioSessionCategoryPlayback, error = null)) {
        "Could not configure audio playback."
      }
      check(session.setActive(true, error = null)) { "Could not activate audio playback." }
      sessionActive = true
      playWhenReady = true
      check(engine.play()) { "Could not start audio." }
    }
  }

  override fun pause() {
    playWhenReady = false
    command {
      it.pause()
      deactivateSession()
    }
  }

  override fun seekTo(positionMillis: Long) {
    command { engine ->
      engine.currentTime = positionMillis.coerceIn(0, state.value.durationMillis) / 1000.0
      state.value = state.value.copy(hasEnded = false)
    }
  }

  override fun setSpeed(speed: Float) {
    if (!speed.isFinite() || speed !in 0.5f..2f || closed) {
      return
    }
    this.speed = speed
    command { it.rate = speed }
  }

  override fun close() {
    if (player == null || NSThread.isMainThread) {
      closeOnMain()
    } else {
      dispatch_sync(dispatch_get_main_queue()) { closeOnMain() }
    }
  }

  private fun closeOnMain() {
    closed = true
    releasePlayer()
    state.value = state.value.copy(isPlaying = false, playWhenReady = false, isReady = false)
  }

  private fun command(block: (AVAudioPlayer) -> Unit) {
    val engine = player ?: return
    if (closed || state.value.error != null) {
      return
    }
    try {
      block(engine)
      publish(engine)
    } catch (exception: Throwable) {
      fail(exception.message ?: "Could not play audio.")
    }
  }

  private fun publish(engine: AVAudioPlayer) {
    val duration = engine.duration.milliseconds()
    state.value =
      state.value.copy(
        positionMillis =
          if (state.value.hasEnded) {
            duration
          } else {
            engine.currentTime.milliseconds().coerceAtMost(duration)
          },
        durationMillis = duration,
        isPlaying = engine.playing,
        playWhenReady = playWhenReady,
        isReady = true,
      )
  }

  private fun Double.milliseconds(): Long =
    if (isFinite() && this > 0) (this * 1000).toLong() else 0

  private fun fail(message: String) {
    releasePlayer()
    state.value =
      state.value.copy(
        isPlaying = false,
        playWhenReady = false,
        isReady = false,
        hasEnded = false,
        error = message,
      )
  }

  private fun releasePlayer() {
    playWhenReady = false
    polling?.cancel()
    polling = null
    val previous = player
    player = null
    previous?.delegate = null
    delegate = null
    pauseObservers.forEach { NSNotificationCenter.defaultCenter.removeObserver(it) }
    pauseObservers.clear()
    previous?.stop()
    deactivateSession()
  }

  private fun observePause(
    engine: AVAudioPlayer,
    notificationName: String,
    reasonKey: String,
    reason: ULong,
  ) {
    pauseObservers +=
      NSNotificationCenter.defaultCenter.addObserverForName(
        notificationName,
        null,
        NSOperationQueue.mainQueue,
      ) { notification ->
        val observedReason = notification?.userInfo?.get(reasonKey) as? NSNumber
        if (player === engine && observedReason?.unsignedIntegerValue == reason) {
          pause()
        }
      }
  }

  private fun deactivateSession() {
    if (sessionActive) {
      sessionActive = false
      AVAudioSession.sharedInstance()
        .setActive(false, AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, error = null)
    }
  }

  private class Delegate(
    private val onEnd: (AVAudioPlayer, Boolean) -> Unit,
    private val onError: (AVAudioPlayer, NSError?) -> Unit,
  ) : NSObject(), AVAudioPlayerDelegateProtocol {
    override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) {
      onEnd(player, successfully)
    }

    override fun audioPlayerDecodeErrorDidOccur(player: AVAudioPlayer, error: NSError?) {
      onError(player, error)
    }
  }
}
