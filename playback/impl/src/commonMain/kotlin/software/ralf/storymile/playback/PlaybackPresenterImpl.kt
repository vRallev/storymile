package software.ralf.storymile.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.scope.di.metro.metroDependencyGraph
import software.ralf.storymile.playlist.PlaylistRepository
import software.ralf.storymile.user.UserManager
import software.ralf.storymile.user.UserScope

@Inject
@ContributesBinding(AppScope::class)
class PlaybackPresenterImpl(private val userManager: UserManager) : PlaybackPresenter {
  @Composable
  override fun present(input: Unit): Model {
    val userScope by userManager.userScope.collectAsState()
    val hasEpisodes =
      key(userScope) {
        val playlists by
          userScope
            .metroDependencyGraph<UserBindings>()
            .playlistRepository
            .playlists
            .collectAsState()
        playlists.any { it.episodeIds.isNotEmpty() }
      }
    var episode by remember {
      mutableStateOf(Episode("The art of slowing down", "The Quiet Current", 2538))
    }
    var queue by remember {
      mutableStateOf(
        listOf(
          Episode("The sound of a quieter life", "The Quiet Current", 2220),
          Episode("Making room for what matters", "The Quiet Current", 2880),
          Episode("Small moments, big meaning", "The Quiet Current", 2340),
        ),
      )
    }
    var positionSeconds by remember { mutableIntStateOf(754) }
    var isPlaying by remember { mutableStateOf(true) }
    var isBookmarked by remember { mutableStateOf(false) }
    var isDownloaded by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1.2f) }
    var sleepMinutes by remember { mutableStateOf<Int?>(null) }
    val chapters = remember(episode) { chapters(episode.durationSeconds) }

    val player =
      PlayerModel(
        title = episode.title,
        showTitle = episode.showTitle,
        positionSeconds = positionSeconds,
        durationSeconds = episode.durationSeconds,
        isPlaying = isPlaying,
        isBookmarked = isBookmarked,
        isDownloaded = isDownloaded,
        speed = speed,
        sleepMinutes = sleepMinutes,
        chapters = chapters,
        activeChapterIndex = chapters.indexOfLast { it.startSeconds <= positionSeconds },
        queue = queue,
        onPlayPause = { isPlaying = !isPlaying },
        onSeek = { positionSeconds = it.coerceIn(0, episode.durationSeconds) },
        onSkipBack = { positionSeconds = (positionSeconds - 15).coerceAtLeast(0) },
        onSkipForward = {
          positionSeconds = (positionSeconds + 30).coerceAtMost(episode.durationSeconds)
        },
        onBookmark = { isBookmarked = !isBookmarked },
        onDownload = { isDownloaded = !isDownloaded },
        onSpeed = {
          if (it.isFinite()) {
            speed = it.coerceIn(0.5f, 3f)
          }
        },
        onSleep = { sleepMinutes = it?.takeIf { minutes -> minutes > 0 } },
        onChapter = { index ->
          chapters.getOrNull(index)?.let { positionSeconds = it.startSeconds }
        },
        onEpisode = { index ->
          queue.getOrNull(index)?.let { next ->
            episode = next
            queue = queue.filterIndexed { queueIndex, _ -> queueIndex != index }
            positionSeconds = 0
            isPlaying = true
            isBookmarked = false
            isDownloaded = false
          }
        },
      )

    return Model(
      collapsed = BarModel(player).takeIf { hasEpisodes },
      expanded = ScreenModel(player).takeIf { hasEpisodes },
    )
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

  @ContributesTo(UserScope::class)
  interface UserBindings {
    val playlistRepository: PlaylistRepository
  }

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
  )

  data class BarModel(val player: PlayerModel) : BaseModel

  data class ScreenModel(val player: PlayerModel) : BaseModel

  /** Renderer-only state and actions stay shared while the playback layer expands or collapses. */
  data class Model(override val collapsed: BarModel?, override val expanded: ScreenModel?) :
    PlaybackPresenter.Model
}
