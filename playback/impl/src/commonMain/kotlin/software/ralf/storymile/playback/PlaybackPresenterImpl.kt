package software.ralf.storymile.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.storymile.player.Player
import software.ralf.storymile.podcast.QueuedEpisode
import software.ralf.storymile.user.UserScope
import software.ralf.storymile.util.HtmlDescription

@Inject
@ContributesBinding(UserScope::class)
class PlaybackPresenterImpl(private val player: Player) : PlaybackPresenter {
  @Composable
  override fun present(input: Unit): Model {
    val audio by player.state.collectAsState()
    val episode = audio.episode ?: return Model(collapsed = null, expanded = null)
    val description =
      remember(episode.episode.description) {
        HtmlDescription.plainText(episode.episode.description)
      }
    val playerModel =
      PlayerModel(
        title = episode.episode.title,
        showTitle = episode.podcastTitle,
        artworkUrl = episode.artworkUrl,
        description = description,
        positionSeconds = (audio.positionMillis / 1000).toInt(),
        durationSeconds = (audio.durationMillis / 1000).toInt(),
        isPlaying = audio.playWhenReady || audio.isPlaying,
        canSeek = audio.isReady && !audio.isComplete && audio.error == null,
        speed = audio.speed,
        queue = audio.upcomingEpisodes,
        onPlayPause = { player.togglePlayback() },
        onSeek = { player.seekTo(it.toLong() * 1000) },
        onSkipBack = { player.seekTo(player.state.value.positionMillis - 15_000) },
        onSkipForward = { player.seekTo(player.state.value.positionMillis + 30_000) },
        onSpeed = { player.setSpeed(it) },
        onEpisode = { player.play(it) },
        error = audio.error,
      )
    return Model(collapsed = BarModel(playerModel), expanded = ScreenModel(playerModel))
  }

  data class PlayerModel(
    val title: String,
    val showTitle: String,
    val artworkUrl: String?,
    val description: String?,
    val positionSeconds: Int,
    val durationSeconds: Int,
    val isPlaying: Boolean,
    val canSeek: Boolean,
    val speed: Float,
    val queue: List<QueuedEpisode>,
    val onPlayPause: () -> Unit,
    val onSeek: (Int) -> Unit,
    val onSkipBack: () -> Unit,
    val onSkipForward: () -> Unit,
    val onSpeed: (Float) -> Unit,
    val onEpisode: (QueuedEpisode) -> Unit,
    val error: String?,
  )

  data class BarModel(val player: PlayerModel) : BaseModel

  data class ScreenModel(val player: PlayerModel) : BaseModel

  /** Renderer-only state and actions stay shared while the playback layer expands or collapses. */
  data class Model(override val collapsed: BarModel?, override val expanded: ScreenModel?) :
    PlaybackPresenter.Model
}
