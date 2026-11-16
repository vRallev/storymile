package software.ralf.storymile.player

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
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
import software.ralf.storymile.podcast.PodcastEnclosure
import software.ralf.storymile.podcast.PodcastEpisode
import software.ralf.storymile.podcast.QueueRepository
import software.ralf.storymile.podcast.QueuedEpisode
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.testing.FakeRuntimeModeController

class PlayerImplTest {
  @Test
  fun `adding the first real episode selects it without requesting audio until play`() =
    runTestWithScope { scope ->
      val queue = TestQueue(emptyList())
      val audio = FakeAudioPlayer()
      val player: Player = player(scope = scope, audio = audio, queue = queue)
      runCurrent()
      assertThat(player.state.value.episode).isNull()
      val first = episode(1)
      queue.save(first)
      runCurrent()
      assertThat(player.state.value.episode).isEqualTo(first)
      assertThat(player.state.value.isPlaying).isFalse()
      assertThat(audio.sources).isEqualTo(emptyList())
      player.togglePlayback()
      runCurrent()
      assertThat(audio.sources).isEqualTo(listOf(first.episode.enclosure.url))
      assertThat(player.state.value.isPlaying).isTrue()
    }

  @Test
  fun `fake mode resolves the sample enclosure to a bundled resource`() =
    runTestWithScope { scope ->
      val audio = FakeAudioPlayer()
      val sample =
        episode(number = 1, url = "https://storymile.invalid/test-episodes/test-episode-1.mp3")
      val player: Player =
        player(
          scope = scope,
          audio = audio,
          queue = TestQueue(listOf(sample)),
          mode = RuntimeMode.Fake,
        )
      runCurrent()
      player.togglePlayback()
      assertThat(audio.sources).isEqualTo(listOf("files/test-episodes/test-episode-1.mp3"))
    }

  @Test
  fun `audio progress and errors drive observable state without inventing elapsed time`() =
    runTestWithScope { scope ->
      val audio = FakeAudioPlayer()
      val player: Player = player(scope = scope, audio = audio)
      runCurrent()
      player.togglePlayback()
      audio.publish { it.copy(isReady = true, durationMillis = 60_000) }
      runCurrent()
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

      player.togglePlayback()
      runCurrent()
      assertThat(player.state.value.error).isNull()
      assertThat(audio.sources).isEqualTo(List(2) { episode(1).episode.enclosure.url })
    }

  @Test
  fun `completion advances once per source and stops at the end of the queue`() =
    runTestWithScope { scope ->
      val audio = FakeAudioPlayer()
      val player: Player = player(scope = scope, audio = audio)
      runCurrent()
      player.setSpeed(1.5f)
      player.togglePlayback()
      runCurrent()
      val firstSource = audio.state.value.source
      for (number in 2..3) {
        audio.finish()
        runCurrent()
        assertThat(player.state.value.episode).isEqualTo(episode(number))
        assertThat(player.state.value.speed).isEqualTo(1.5f)
        assertThat(audio.playbackSpeed).isEqualTo(1.5f)
        assertThat(player.state.value.isPlaying).isTrue()
      }
      val current = audio.state.value
      audio.publish { it.copy(source = firstSource, hasEnded = true) }
      runCurrent()
      assertThat(player.state.value.episode).isEqualTo(episode(3))
      audio.publish { current }
      audio.finish()
      runCurrent()
      assertThat(player.state.value.isComplete).isTrue()
      assertThat(player.state.value.isPlaying).isFalse()
      assertThat(audio.sources.size).isEqualTo(3)

      player.togglePlayback()
      runCurrent()
      assertThat(player.state.value.episode).isEqualTo(episode(1))
      assertThat(player.state.value.isPlaying).isTrue()
    }

  @Test
  fun `seeks are clamped and nonfinite speeds are ignored`() = runTestWithScope { scope ->
    val audio = FakeAudioPlayer()
    val player: Player = player(scope = scope, audio = audio)
    runCurrent()
    player.seekTo(100)
    assertThat(audio.seekCount).isEqualTo(0)
    player.togglePlayback()
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
      val player: Player = player(scope = scope, audio = audio)
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
    val player: Player = player(scope = scope, audio = audio)
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
  fun `reordering keeps the current episode and changes which episode plays next`() =
    runTestWithScope { scope ->
      val audio = FakeAudioPlayer()
      val queue = TestQueue()
      val player: Player = player(scope = scope, audio = audio, queue = queue)
      runCurrent()
      queue.move(episode = episode(2), target = episode(1))
      runCurrent()
      assertThat(player.state.value.episode).isEqualTo(episode(2))
      player.togglePlayback()
      runCurrent()

      queue.move(episode = episode(3), target = episode(2))
      runCurrent()
      assertThat(player.state.value.episode).isEqualTo(episode(2))
      assertThat(player.state.value.upcomingEpisodes).isEqualTo(listOf(episode(3), episode(1)))
      audio.finish()
      runCurrent()
      assertThat(player.state.value.episode).isEqualTo(episode(3))
      audio.finish()
      runCurrent()
      assertThat(player.state.value.episode).isEqualTo(episode(1))
      assertThat(queue.episodes.value).isEqualTo(listOf(episode(3), episode(2), episode(1)))
    }

  @Test
  fun `removing the playing episode advances and clearing the queue stops audio`() =
    runTestWithScope { scope ->
      val audio = FakeAudioPlayer()
      val queue = TestQueue(listOf(episode(1), episode(2)))
      val player: Player = player(scope = scope, audio = audio, queue = queue)
      runCurrent()
      player.togglePlayback()
      runCurrent()
      queue.remove(episode(1))
      runCurrent()
      assertThat(player.state.value.episode).isEqualTo(episode(2))
      assertThat(player.state.value.isPlaying).isTrue()
      queue.remove(episode(2))
      runCurrent()
      assertThat(player.state.value.episode).isNull()
      assertThat(player.state.value.isPlaying).isFalse()
      player.togglePlayback()
      assertThat(audio.wantsPlayback).isFalse()
      assertThat(audio.state.value.source).isNull()
    }

  @Test
  fun `a queued episode can be selected by feed and episode id`() = runTestWithScope { scope ->
    val audio = FakeAudioPlayer()
    val first = episode(1)
    val otherShow = first.copy(feedUrl = "https://second.example.org/feed")
    val queue = TestQueue(listOf(first, otherShow, episode(2)))
    val player: Player = player(scope = scope, audio = audio, queue = queue)
    runCurrent()
    player.play(otherShow)
    runCurrent()
    assertThat(player.state.value.episode).isEqualTo(otherShow)
    assertThat(player.state.value.upcomingEpisodes).isEqualTo(listOf(first, episode(2)))
    audio.finish()
    runCurrent()
    assertThat(player.state.value.episode).isEqualTo(first)
    assertThat(player.state.value.isPlaying).isTrue()
  }

  @Test
  fun `scope exit releases playback and ignores later commands and events`() =
    runTestWithScope { scope ->
      val audio = FakeAudioPlayer()
      val player: Player = player(scope = scope, audio = audio)
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
    queue: QueueRepository = TestQueue(),
    mode: RuntimeMode = RuntimeMode.Real,
  ): Player =
    PlayerImpl(
        audioPlayer = lazy { audio },
        runtimeModeController = FakeRuntimeModeController(mode),
        queueRepository = queue,
        mainDispatcher = StandardTestDispatcher(testScheduler),
        resourceUri = { it },
      )
      .also { scope.register(it) }

  private companion object {
    fun episode(number: Int, url: String = "https://cdn.example.org/episode-$number.mp3") =
      QueuedEpisode(
        feedUrl = "https://example.org/feed",
        podcastTitle = "Test show",
        artworkUrl = null,
        episode =
          PodcastEpisode(
            id = "$number",
            title = "Episode $number",
            description = null,
            author = null,
            artworkUrl = null,
            publishedAt = null,
            duration = null,
            enclosure = PodcastEnclosure(url = url, type = "audio/mpeg", length = null),
          ),
      )
  }

  private class TestQueue(initial: List<QueuedEpisode> = (1..3).map { episode(it) }) :
    QueueRepository {
    override val episodes: StateFlow<List<QueuedEpisode>?>
      field = MutableStateFlow(initial)

    override fun save(episode: QueuedEpisode) {
      episodes.update { it + episode }
    }

    override fun remove(episode: QueuedEpisode) {
      episodes.update { it - episode }
    }

    override fun move(episode: QueuedEpisode, target: QueuedEpisode) {
      episodes.update {
        it.toMutableList().apply {
          val index = indexOf(target)
          remove(episode)
          add(index, episode)
        }
      }
    }
  }

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

    override fun clear() {
      wantsPlayback = false
      state.value = State()
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
