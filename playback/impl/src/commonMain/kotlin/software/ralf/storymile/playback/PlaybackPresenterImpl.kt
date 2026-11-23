package software.ralf.storymile.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.storymile.player.Player
import software.ralf.storymile.user.UserScope
import software.ralf.storymile.util.HtmlDescription

@Inject
@ContributesBinding(UserScope::class)
class PlaybackPresenterImpl(private val player: Player) : PlaybackPresenter {
  @Composable
  override fun present(input: Unit): Model {
    val audio by player.state.collectAsState()
    val current = audio.episode ?: return Model(collapsed = null, expanded = null)
    val duration = (audio.durationMillis / 1000).toInt()
    val position = (audio.positionMillis / 1000).toInt()
    val description =
      remember(current.episode.description) {
        HtmlDescription.plainText(current.episode.description)
      }

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
        speed = audio.speed,
        queue = upcoming,
        onPlayPause = { player.togglePlayback() },
        onSeek = { player.seekTo(it.toLong() * 1000) },
        onSkipBack = { player.seekTo(player.state.value.positionMillis - 15_000) },
        onSkipForward = { player.seekTo(player.state.value.positionMillis + 30_000) },
        onSpeed = { player.setSpeed(it) },
        onEpisode = { index -> audio.upcomingEpisodes.getOrNull(index)?.let { player.play(it) } },
        error = audio.error,
        description = description,
        artworkUrl = current.artworkUrl,
      )
    return Model(collapsed = BarModel(model), expanded = ScreenModel(model))
  }

  data class Chapter(val title: String, val startSeconds: Int)

  data class Episode(val title: String, val showTitle: String, val durationSeconds: Int)

  data class PlayerModel(
    val title: String,
    val showTitle: String,
    val positionSeconds: Int,
    val durationSeconds: Int,
    val isPlaying: Boolean,
    val speed: Float,
    val queue: List<Episode>,
    val onPlayPause: () -> Unit,
    val onSeek: (Int) -> Unit,
    val onSkipBack: () -> Unit,
    val onSkipForward: () -> Unit,
    val onSpeed: (Float) -> Unit,
    val onEpisode: (Int) -> Unit,
    val error: String? = null,
    val description: String? = null,
    val artworkUrl: String? = null,
    val isBookmarked: Boolean = false,
    val isDownloaded: Boolean = false,
    val sleepMinutes: Int? = null,
    val chapters: List<Chapter> = emptyList(),
    val activeChapterIndex: Int = -1,
    val onBookmark: (() -> Unit)? = null,
    val onDownload: (() -> Unit)? = null,
    val onSleep: ((Int?) -> Unit)? = null,
    val onChapter: ((Int) -> Unit)? = null,
  )

  data class BarModel(val player: PlayerModel) : BaseModel

  data class ScreenModel(val player: PlayerModel) : BaseModel

  /** Renderer-only state and actions stay shared while the playback layer expands or collapses. */
  data class Model(override val collapsed: BarModel?, override val expanded: ScreenModel?) :
    PlaybackPresenter.Model
}
