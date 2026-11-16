package software.ralf.storymile.player

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ForScope
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
import platform.AVFoundation.AVAudioTimePitchAlgorithmTimeDomain
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemFailedToPlayToEndTimeErrorKey
import platform.AVFoundation.AVPlayerItemFailedToPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVPlayerItemStatusReadyToPlay
import platform.AVFoundation.AVPlayerStatusFailed
import platform.AVFoundation.AVPlayerTimeControlStatusPlaying
import platform.AVFoundation.audioTimePitchAlgorithm
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.defaultRate
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.AVFoundation.timeControlStatus
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.CoreMedia.kCMTimeZero
import platform.Foundation.NSError
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSThread
import platform.Foundation.NSURL
import platform.darwin.NSObjectProtocol
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_sync
import software.ralf.app.platform.scope.coroutine.MainCoroutineDispatcher
import software.ralf.storymile.user.UserScope

@OptIn(ExperimentalForeignApi::class)
@ContributesBinding(UserScope::class)
class IosAudioPlayer(
  @ForScope(UserScope::class) private val coroutineScope: CoroutineScope,
  @MainCoroutineDispatcher private val mainDispatcher: CoroutineDispatcher,
) : AudioPlayer() {
  override val state: StateFlow<State>
    field = MutableStateFlow(State())

  private var player: AVPlayer? = null
  private var polling: Job? = null
  private var speed = 1f
  private var closed = false
  private var sessionActive = false
  private var playWhenReady = false
  private val observers = mutableListOf<NSObjectProtocol>()

  override fun load(source: String) {
    if (closed) {
      return
    }
    releasePlayer()
    state.value = State(source = source)
    try {
      val url = requireNotNull(NSURL.URLWithString(source)) { "Invalid audio URL." }
      val item = AVPlayerItem(url)
      item.audioTimePitchAlgorithm = AVAudioTimePitchAlgorithmTimeDomain
      val engine = AVPlayer(item)
      engine.defaultRate = speed
      player = engine
      observe(AVPlayerItemDidPlayToEndTimeNotification, item) {
        if (player === engine) {
          playWhenReady = false
          deactivateSession()
          state.value = state.value.copy(hasEnded = true)
          publish(engine)
        }
      }
      observe(AVPlayerItemFailedToPlayToEndTimeNotification, item) { notification ->
        if (player === engine) {
          val error =
            notification?.userInfo?.get(AVPlayerItemFailedToPlayToEndTimeErrorKey) as? NSError
          fail(error?.localizedDescription ?: "Could not finish audio playback.")
        }
      }
      observePause(
        engine = engine,
        notificationName = AVAudioSessionInterruptionNotification,
        reasonKey = requireNotNull(AVAudioSessionInterruptionTypeKey),
        reason = AVAudioSessionInterruptionTypeBegan,
      )
      observePause(
        engine = engine,
        notificationName = AVAudioSessionRouteChangeNotification,
        reasonKey = requireNotNull(AVAudioSessionRouteChangeReasonKey),
        reason = AVAudioSessionRouteChangeReasonOldDeviceUnavailable,
      )
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

  override fun clear() {
    if (!closed) {
      releasePlayer()
      state.value = State()
    }
  }

  override fun play() {
    command { engine ->
      if (state.value.hasEnded) {
        engine.seekToTime(kCMTimeZero.readValue())
        state.value = state.value.copy(hasEnded = false)
      }
      val session = AVAudioSession.sharedInstance()
      check(session.setCategory(AVAudioSessionCategoryPlayback, error = null)) {
        "Could not configure audio playback."
      }
      check(session.setActive(true, error = null)) { "Could not activate audio playback." }
      sessionActive = true
      playWhenReady = true
      engine.play()
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
      val duration = state.value.durationMillis.takeIf { it > 0 } ?: Long.MAX_VALUE
      val position = positionMillis.coerceIn(0, duration)
      engine.seekToTime(
        time = CMTimeMake(value = position, timescale = 1_000),
        toleranceBefore = kCMTimeZero.readValue(),
        toleranceAfter = kCMTimeZero.readValue(),
      )
      state.value = state.value.copy(hasEnded = false)
    }
  }

  override fun setSpeed(speed: Float) {
    if (!speed.isFinite() || speed !in 0.5f..2f || closed) {
      return
    }
    this.speed = speed
    command { engine ->
      engine.defaultRate = speed
      if (playWhenReady) {
        engine.rate = speed
      }
    }
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

  private fun command(block: (AVPlayer) -> Unit) {
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

  private fun publish(engine: AVPlayer) {
    val item = engine.currentItem ?: return
    if (engine.status == AVPlayerStatusFailed || item.status == AVPlayerItemStatusFailed) {
      fail(
        item.error?.localizedDescription
          ?: engine.error?.localizedDescription
          ?: "Could not play audio.",
      )
      return
    }
    val ready = item.status == AVPlayerItemStatusReadyToPlay
    val duration = if (ready) CMTimeGetSeconds(item.duration).milliseconds() else 0
    val ended = state.value.hasEnded
    val position = CMTimeGetSeconds(engine.currentTime()).milliseconds()
    state.value =
      state.value.copy(
        positionMillis = if (ended && duration > 0) duration else position,
        durationMillis = duration,
        isPlaying = !ended && engine.timeControlStatus == AVPlayerTimeControlStatusPlaying,
        playWhenReady = playWhenReady,
        isReady = ready,
      )
  }

  private fun Double.milliseconds(): Long =
    if (isFinite() && this > 0) (this * 1_000).toLong() else 0

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
    observers.forEach { NSNotificationCenter.defaultCenter.removeObserver(it) }
    observers.clear()
    previous?.pause()
    previous?.replaceCurrentItemWithPlayerItem(null)
    deactivateSession()
  }

  private fun observePause(
    engine: AVPlayer,
    notificationName: String?,
    reasonKey: String,
    reason: ULong,
  ) {
    observe(notificationName, null) { notification ->
      val observedReason = notification?.userInfo?.get(reasonKey) as? NSNumber
      if (player === engine && observedReason?.unsignedIntegerValue == reason) {
        pause()
      }
    }
  }

  private fun observe(name: String?, source: Any?, callback: (NSNotification?) -> Unit) {
    observers +=
      NSNotificationCenter.defaultCenter.addObserverForName(
        name = requireNotNull(name),
        `object` = source,
        queue = NSOperationQueue.mainQueue,
        usingBlock = callback,
      )
  }

  private fun deactivateSession() {
    if (sessionActive) {
      sessionActive = false
      AVAudioSession.sharedInstance()
        .setActive(false, AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, error = null)
    }
  }
}
