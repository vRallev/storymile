package software.ralf.storymile.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.storymile.player.Player
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.RuntimeModeController
import software.ralf.storymile.user.UserScope

@Inject
@ContributesBinding(UserScope::class)
class PlaybackPresenterImpl(
  private val player: Player,
  private val runtimeModeController: RuntimeModeController,
) : PlaybackPresenter {
  @Composable
  override fun present(input: Unit): Model {
    val audio by player.state.collectAsState()
    val current = audio.episode ?: return Model(collapsed = null, expanded = null)
    val isPreview = remember { runtimeModeController.mode == RuntimeMode.Fake }
    val duration = (audio.durationMillis / 1000).toInt()
    val position = (audio.positionMillis / 1000).toInt()
    val chapters =
      remember(isPreview, duration) {
        if (isPreview) chapters(duration) else emptyList()
      }
    var isBookmarked by remember(current.feedUrl, current.episode.id) { mutableStateOf(false) }
    var isDownloaded by remember(current.feedUrl, current.episode.id) { mutableStateOf(false) }
    var sleepMinutes by remember { mutableStateOf<Int?>(null) }

    val upcoming =
      remember(audio.upcomingEpisodes) {
        audio.upcomingEpisodes.map {
          Episode(
            title = it.episode.title,
            showTitle = it.podcastTitle,
            durationSeconds = it.episode.duration?.inWholeSeconds?.toInt() ?: 0,
          )
        }
      }
    val model =
      PlayerModel(
        title = current.episode.title,
        showTitle = current.podcastTitle,
        positionSeconds = position,
        durationSeconds = duration,
        isPlaying = audio.playWhenReady || audio.isPlaying,
        isBookmarked = isBookmarked,
        isDownloaded = isDownloaded,
        speed = audio.speed,
        sleepMinutes = sleepMinutes,
        chapters = chapters,
        activeChapterIndex = chapters.indexOfLast { it.startSeconds <= position },
        queue = upcoming,
        onPlayPause = { player.togglePlayback() },
        onSeek = { player.seekTo(it.toLong() * 1000) },
        onSkipBack = { player.seekTo(player.state.value.positionMillis - 15_000) },
        onSkipForward = { player.seekTo(player.state.value.positionMillis + 30_000) },
        onBookmark = { isBookmarked = !isBookmarked },
        onDownload = { isDownloaded = !isDownloaded },
        onSpeed = { player.setSpeed(it) },
        onSleep = { sleepMinutes = it?.takeIf { minutes -> minutes > 0 } },
        onChapter = { index ->
          chapters.getOrNull(index)?.let { player.seekTo(it.startSeconds.toLong() * 1000) }
        },
        onEpisode = { index -> audio.upcomingEpisodes.getOrNull(index)?.let { player.play(it) } },
        error = audio.error,
        isPreview = isPreview,
        artworkUrl = current.artworkUrl,
      )
    return Model(collapsed = BarModel(model), expanded = ScreenModel(model))
  }

  private fun chapters(durationSeconds: Int): List<Chapter> =
    listOf(
        Chapter("Welcome to the quiet", 0),
        Chapter("Letting go of the rush", 132),
        Chapter("Finding a slower rhythm", 600),
        Chapter("Space for stillness", 1080),
        Chapter("Returning to yourself", 1425),
        Chapter("Everyday rituals", 1790),
        Chapter("Carrying calm with you", 2120),
        Chapter("Closing thoughts", 2418),
      )
      .map { it.copy(startSeconds = it.startSeconds * durationSeconds / 2538) }

  data class Chapter(val title: String, val startSeconds: Int)

  data class Episode(val title: String, val showTitle: String, val durationSeconds: Int)

  data class PlayerModel(
    val title: String,
    val showTitle: String,
    val positionSeconds: Int,
    val durationSeconds: Int,
    val isPlaying: Boolean,
    val isBookmarked: Boolean,
    val isDownloaded: Boolean,
    val speed: Float,
    val sleepMinutes: Int?,
    val chapters: List<Chapter>,
    val activeChapterIndex: Int,
    val queue: List<Episode>,
    val onPlayPause: () -> Unit,
    val onSeek: (Int) -> Unit,
    val onSkipBack: () -> Unit,
    val onSkipForward: () -> Unit,
    val onBookmark: () -> Unit,
    val onDownload: () -> Unit,
    val onSpeed: (Float) -> Unit,
    val onSleep: (Int?) -> Unit,
    val onChapter: (Int) -> Unit,
    val onEpisode: (Int) -> Unit,
    val error: String? = null,
    val isPreview: Boolean = true,
    val artworkUrl: String? = null,
  )

  data class BarModel(val player: PlayerModel) : BaseModel

  data class ScreenModel(val player: PlayerModel) : BaseModel

  /** Renderer-only state and actions stay shared while the playback layer expands or collapses. */
  data class Model(override val collapsed: BarModel?, override val expanded: ScreenModel?) :
    PlaybackPresenter.Model
}
