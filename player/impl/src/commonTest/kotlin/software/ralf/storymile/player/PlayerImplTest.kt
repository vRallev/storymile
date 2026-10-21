package software.ralf.storymile.player

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.runTestWithScope
import software.ralf.storymile.playlist.EpisodeId
import software.ralf.storymile.playlist.Playlist
import software.ralf.storymile.playlist.PlaylistId
import software.ralf.storymile.playlist.PlaylistName
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.testing.FakeRuntimeModeController

class PlayerImplTest {
  @Test
  fun `fake mode prepares the first episode paused at normal speed`() = runTestWithScope { scope ->
    val audio = FakeAudioPlayer()
    val player: Player = player(scope, audio)
    runCurrent()
    assertThat(player.state.value.title).isEqualTo("Test episode 1")
    assertThat(player.state.value.positionMillis).isEqualTo(0L)
    assertThat(player.state.value.isPlaying).isFalse()
    assertThat(player.state.value.speed).isEqualTo(1f)
    assertThat(audio.sources.single().endsWith("test-episode-1.mp3")).isTrue()
  }

  @Test
  fun `real mode does not create an audio transport`() = runTestWithScope { scope ->
    val transport = lazy<AudioPlayer> { error("Real mode must not create a player") }
    val player: Player =
      PlayerImpl(
          transport,
          FakeRuntimeModeController(RuntimeMode.Real),
          playlists(),
          StandardTestDispatcher(testScheduler),
          { it },
        )
        .also { scope.register(it) }
    runCurrent()
    player.togglePlayback()
    player.seekTo(123)
    player.setSpeed(2f)
    assertThat(player.state.value).isEqualTo(Player.State())
    scope.destroy()
    assertThat(transport.isInitialized()).isFalse()
  }

  @Test
  fun `audio progress and errors drive observable state without inventing elapsed time`() =
    runTestWithScope { scope ->
      val audio = FakeAudioPlayer()
      val player: Player = player(scope, audio)
      runCurrent()
      audio.publish { it.copy(isReady = true, durationMillis = 60_000) }
      runCurrent()
      player.togglePlayback()
      audio.publish { it.copy(positionMillis = 12_345, isPlaying = true) }
      runCurrent()
      assertThat(player.state.value.positionMillis).isEqualTo(12_345L)
      assertThat(player.state.value.durationMillis).isEqualTo(60_000L)
      assertThat(player.state.value.isPlaying).isTrue()

      player.togglePlayback()
      runCurrent()
      assertThat(player.state.value.isPlaying).isFalse()
      assertThat(player.state.value.positionMillis).isEqualTo(12_345L)

      audio.publish { it.copy(error = "Cannot decode", isPlaying = false) }
      runCurrent()
      assertThat(player.state.value.error).isEqualTo("Cannot decode")
      assertThat(audio.sources.size).isEqualTo(1)
    }

  @Test
  fun `completion advances once per source and stops after episode three`() =
    runTestWithScope { scope ->
      val audio = FakeAudioPlayer()
      val player: Player = player(scope, audio)
      runCurrent()
      player.setSpeed(1.5f)
      player.togglePlayback()
      runCurrent()
      val firstSource = audio.state.value.source
      for (number in 2..3) {
        audio.finish()
        runCurrent()
        assertThat(player.state.value.title).isEqualTo("Test episode $number")
        assertThat(player.state.value.speed).isEqualTo(1.5f)
        assertThat(audio.playbackSpeed).isEqualTo(1.5f)
        assertThat(player.state.value.isPlaying).isTrue()
      }
      val current = audio.state.value
      audio.publish { it.copy(source = firstSource, hasEnded = true) }
      runCurrent()
      assertThat(player.state.value.title).isEqualTo("Test episode 3")
      audio.publish { current }
      audio.finish()
      runCurrent()
      assertThat(player.state.value.isComplete).isTrue()
      assertThat(player.state.value.isPlaying).isFalse()
      player.togglePlayback()
      runCurrent()
      assertThat(audio.sources.size).isEqualTo(3)
      assertThat(player.state.value.isPlaying).isFalse()
    }

  @Test
  fun `seeks are clamped and nonfinite speeds are ignored`() = runTestWithScope { scope ->
    val audio = FakeAudioPlayer()
    val player: Player = player(scope, audio)
    runCurrent()
    player.seekTo(100)
    assertThat(audio.seekCount).isEqualTo(0)
    audio.publish { it.copy(isReady = true, durationMillis = 40_000) }
    runCurrent()
    player.seekTo(-1)
    assertThat(audio.state.value.positionMillis).isEqualTo(0L)
    player.seekTo(Long.MAX_VALUE)
    assertThat(audio.state.value.positionMillis).isEqualTo(40_000L)
    player.setSpeed(Float.NaN)
    player.setSpeed(Float.POSITIVE_INFINITY)
    assertThat(player.state.value.speed).isEqualTo(1f)
    player.setSpeed(3f)
    assertThat(audio.playbackSpeed).isEqualTo(2f)
    player.setSpeed(0f)
    assertThat(audio.playbackSpeed).isEqualTo(0.75f)
  }

  @Test
  fun `a second play click cancels playback while the source is preparing`() =
    runTestWithScope { scope ->
      val audio = FakeAudioPlayer().apply { completePlay = false }
      val player: Player = player(scope, audio)
      runCurrent()
      player.togglePlayback()
      player.togglePlayback()
      assertThat(audio.wantsPlayback).isFalse()
      audio.finish()
      runCurrent()
      assertThat(player.state.value.isPlaying).isFalse()
      assertThat(audio.wantsPlayback).isFalse()
    }

  @Test
  fun `native pause resets play intent while buffering preserves it`() = runTestWithScope { scope ->
    val audio = FakeAudioPlayer()
    val player: Player = player(scope, audio)
    runCurrent()
    player.togglePlayback()
    runCurrent()
    audio.publish { it.copy(isPlaying = false, playWhenReady = true) }
    runCurrent()
    player.togglePlayback()
    assertThat(audio.wantsPlayback).isFalse()

    player.togglePlayback()
    runCurrent()
    audio.publish { it.copy(isPlaying = false, playWhenReady = true) }
    runCurrent()
    audio.publish { it.copy(isPlaying = false, playWhenReady = false) }
    runCurrent()
    player.togglePlayback()
    assertThat(audio.wantsPlayback).isTrue()
  }

  @Test
  fun `removing all playlist episodes pauses audio when its controls disappear`() =
    runTestWithScope { scope ->
      val audio = FakeAudioPlayer()
      val playlists = playlists()
      val player: Player =
        PlayerImpl(
            lazy { audio },
            FakeRuntimeModeController(RuntimeMode.Fake),
            playlists,
            StandardTestDispatcher(testScheduler),
            { it },
          )
          .also { scope.register(it) }
      runCurrent()
      player.togglePlayback()
      runCurrent()
      assertThat(player.state.value.isPlaying).isTrue()
      playlists.update(emptyList())
      runCurrent()
      assertThat(player.state.value.isPlaying).isFalse()
      player.togglePlayback()
      assertThat(audio.wantsPlayback).isFalse()
      audio.finish()
      runCurrent()
      assertThat(player.state.value.isPlaying).isFalse()
      assertThat(audio.wantsPlayback).isFalse()
    }

  @Test
  fun `scope exit releases playback and ignores later commands and events`() =
    runTestWithScope { scope ->
      val audio = FakeAudioPlayer()
      val player: Player = player(scope, audio)
      runCurrent()
      player.togglePlayback()
      runCurrent()
      scope.destroy()
      assertThat(audio.closed).isTrue()
      assertThat(player.state.value.isPlaying).isFalse()
      val stopped = player.state.value
      player.togglePlayback()
      player.setSpeed(2f)
      player.seekTo(10_000)
      audio.finish()
      runCurrent()
      assertThat(player.state.value).isEqualTo(stopped)
      assertThat(audio.sources.size).isEqualTo(1)
    }

  private fun TestScope.player(
    scope: Scope,
    audio: AudioPlayer,
    mode: RuntimeMode = RuntimeMode.Fake,
  ): Player =
    PlayerImpl(
        lazy { audio },
        FakeRuntimeModeController(mode),
        playlists(),
        StandardTestDispatcher(testScheduler),
        { it },
      )
      .also { scope.register(it) }

  private fun playlists(): TestPlaylistRepository =
    TestPlaylistRepository(
      listOf(
        Playlist(PlaylistId("test"), PlaylistName("Test"), listOf(EpisodeId("test-episode-1"))),
      ),
    )

  private class FakeAudioPlayer : AudioPlayer() {
    override val state: StateFlow<State>
      field = MutableStateFlow(State())

    val sources = mutableListOf<String>()
    var playbackSpeed = 1f
    var seekCount = 0
    var wantsPlayback = false
    var completePlay = true
    var closed = false

    override fun load(source: String) {
      sources += source
      state.value = State(source = source)
    }

    override fun play() {
      wantsPlayback = true
      publish { it.copy(isPlaying = completePlay, playWhenReady = true) }
    }

    override fun pause() {
      wantsPlayback = false
      publish { it.copy(isPlaying = false, playWhenReady = false) }
    }

    override fun seekTo(positionMillis: Long) {
      seekCount++
      publish { it.copy(positionMillis = positionMillis) }
    }

    override fun setSpeed(speed: Float) {
      this.playbackSpeed = speed
    }

    override fun close() {
      closed = true
    }

    fun publish(update: (State) -> State) {
      state.update(update)
    }

    fun finish() {
      publish {
        it.copy(
          hasEnded = true,
          isPlaying = false,
          playWhenReady = false,
          positionMillis = it.durationMillis,
        )
      }
    }
  }
}
