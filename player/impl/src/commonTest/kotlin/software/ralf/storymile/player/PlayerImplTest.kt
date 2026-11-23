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

class PlayerImplTest {
  private val episodes = (1..3).map { episode(it) }

  @Test
  fun `the first queue entry stays paused until play loads its resolved source`() =
    runTestWithScope { scope ->
      val audio = FakeAudioPlayer()
      val first = episodes.first()
      val localSource = "file:///bundled/episode.mp3"
      val player: Player =
        player(
          scope = scope,
          audio = audio,
          audioSourceResolver =
            AudioSourceResolver {
              if (it == first.episode.enclosure.url) localSource else it
            },
        )
      runCurrent()
      assertThat(player.state.value.title).isEqualTo(first.episode.title)
      assertThat(player.state.value.positionMillis).isEqualTo(0L)
      assertThat(player.state.value.isPlaying).isFalse()
      assertThat(player.state.value.speed).isEqualTo(1f)
      assertThat(player.state.value.episode).isEqualTo(first)
      assertThat(audio.sources).isEqualTo(emptyList())
      player.togglePlayback()
      runCurrent()
      assertThat(audio.sources.single()).isEqualTo(localSource)
    }

  @Test
  fun `an empty queue does not create an audio transport`() = runTestWithScope { scope ->
    val transport = lazy<AudioPlayer> { error("An empty queue must not create a player") }
    val player: Player =
      PlayerImpl(
          audioPlayer = transport,
          queueRepository = TestQueue(),
          audioSourceResolver =
            AudioSourceResolver { error("An empty queue must not resolve audio") },
          mainDispatcher = StandardTestDispatcher(testScheduler),
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
  fun `playback follows the queue when upcoming episodes move`() = runTestWithScope { scope ->
    val audio = FakeAudioPlayer()
    val queue = TestQueue()
    val player: Player = player(scope = scope, audio = audio, queue = queue)
    val (first, second, third) = (1..3).map { episode(it) }

    queue.publish(listOf(first, second, third))
    runCurrent()
    assertThat(player.state.value.episode).isEqualTo(first)
    assertThat(player.state.value.isPlaying).isFalse()
    assertThat(audio.sources.isEmpty()).isTrue()

    player.togglePlayback()
    runCurrent()
    queue.publish(listOf(third, first, second))
    runCurrent()
    assertThat(player.state.value.episode).isEqualTo(first)
    assertThat(player.state.value.upcomingEpisodes).isEqualTo(listOf(third, second))
    audio.finish()
    runCurrent()
    assertThat(player.state.value.episode).isEqualTo(third)
    assertThat(player.state.value.isPlaying).isTrue()
    audio.finish()
    runCurrent()
    assertThat(player.state.value.episode).isEqualTo(second)
    audio.finish()
    runCurrent()
    assertThat(player.state.value.isComplete).isTrue()
    assertThat(audio.sources)
      .isEqualTo(listOf(first, third, second).map { it.episode.enclosure.url })

    player.togglePlayback()
    runCurrent()
    assertThat(player.state.value.episode).isEqualTo(third)
    queue.publish(emptyList())
    runCurrent()
    assertThat(player.state.value.episode).isNull()
    assertThat(audio.state.value.source).isNull()
  }

  @Test
  fun `audio progress and errors drive observable state without inventing elapsed time`() =
    runTestWithScope { scope ->
      val audio = FakeAudioPlayer()
      val player: Player = player(scope, audio)
      runCurrent()
      player.togglePlayback()
      runCurrent()
      audio.publish { it.copy(isReady = true, durationMillis = 60_000, positionMillis = 12_345) }
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
  fun `completion advances through the queue and stops at the end`() = runTestWithScope { scope ->
    val audio = FakeAudioPlayer()
    val player: Player = player(scope = scope, audio = audio)
    runCurrent()
    player.setSpeed(1.5f)
    player.togglePlayback()
    runCurrent()
    val firstSource = audio.state.value.source
    for (episode in episodes.drop(1)) {
      audio.finish()
      runCurrent()
      assertThat(player.state.value.episode).isEqualTo(episode)
      assertThat(audio.sources.last()).isEqualTo(episode.episode.enclosure.url)
      assertThat(player.state.value.speed).isEqualTo(1.5f)
      assertThat(audio.playbackSpeed).isEqualTo(1.5f)
      assertThat(player.state.value.isPlaying).isTrue()
    }
    val current = audio.state.value
    audio.publish { it.copy(source = firstSource, hasEnded = true) }
    runCurrent()
    assertThat(player.state.value.episode).isEqualTo(episodes.last())
    audio.publish { current }
    audio.finish()
    runCurrent()
    assertThat(player.state.value.isComplete).isTrue()
    assertThat(player.state.value.isPlaying).isFalse()
    player.togglePlayback()
    runCurrent()
    assertThat(audio.sources.size).isEqualTo(episodes.size + 1)
    assertThat(player.state.value.episode).isEqualTo(episodes.first())
    assertThat(player.state.value.isPlaying).isTrue()
  }

  @Test
  fun `seeks are clamped and nonfinite speeds are ignored`() = runTestWithScope { scope ->
    val audio = FakeAudioPlayer()
    val player: Player = player(scope, audio)
    runCurrent()
    player.seekTo(100)
    assertThat(audio.seekCount).isEqualTo(0)
    player.togglePlayback()
    runCurrent()
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
  fun `removing all queue entries stops playback`() = runTestWithScope { scope ->
    val audio = FakeAudioPlayer()
    val queue = TestQueue(episodes.take(1))
    val player: Player = player(scope = scope, audio = audio, queue = queue)
    runCurrent()
    player.togglePlayback()
    runCurrent()
    assertThat(player.state.value.isPlaying).isTrue()
    queue.publish(emptyList())
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
    queue: QueueRepository = TestQueue(episodes),
    audioSourceResolver: AudioSourceResolver = AudioSourceResolver { it },
  ): Player =
    PlayerImpl(
        audioPlayer = lazy { audio },
        queueRepository = queue,
        audioSourceResolver = audioSourceResolver,
        mainDispatcher = StandardTestDispatcher(testScheduler),
      )
      .also { scope.register(it) }

  private fun episode(number: Int): QueuedEpisode =
    QueuedEpisode(
      feedUrl = "https://example.org/feed.xml",
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
          enclosure =
            PodcastEnclosure(
              url = "https://example.org/episode-$number.mp3",
              type = "audio/mpeg",
              length = null,
            ),
        ),
    )

  private class TestQueue(initial: List<QueuedEpisode> = emptyList()) : QueueRepository {
    override val episodes: StateFlow<List<QueuedEpisode>?>
      field = MutableStateFlow(initial)

    fun publish(episodes: List<QueuedEpisode>) {
      this.episodes.value = episodes
    }

    override fun save(episode: QueuedEpisode) = error("Playback must not change the queue")

    override fun remove(episode: QueuedEpisode) = error("Playback must not change the queue")

    override fun removeWithUndo(episode: QueuedEpisode, onRemoved: (undo: () -> Unit) -> Unit) =
      error("Playback must not change the queue")

    override fun move(episode: QueuedEpisode, target: QueuedEpisode) =
      error("Playback must not change the queue")
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
