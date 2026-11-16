package software.ralf.storymile.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.storymile.player.Player
import software.ralf.storymile.playlist.PlaylistRepository
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.RuntimeModeController
import software.ralf.storymile.user.UserScope
import software.ralf.storymile.util.HtmlDescription

@Inject
@ContributesBinding(UserScope::class)
class PlaybackPresenterImpl(
  private val playlistRepository: PlaylistRepository,
  private val player: Player,
  private val runtimeModeController: RuntimeModeController,
) : PlaybackPresenter {
  @Composable
  override fun present(input: Unit): Model {
    val audio by player.state.collectAsState()
    val isPreview = remember { runtimeModeController.mode == RuntimeMode.Fake }
    return if (isPreview) {
      presentFake(audio)
    } else {
      presentReal(audio)
    }
  }

  @Composable
  private fun presentReal(audio: Player.State): Model {
    val current = audio.episode ?: return Model(collapsed = null, expanded = null)
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
        positionSeconds = (audio.positionMillis / 1000).toInt(),
        durationSeconds = (audio.durationMillis / 1000).toInt(),
        isPlaying = audio.playWhenReady || audio.isPlaying,
        canSeek = audio.canSeek,
        description = description,
        speed = audio.speed,
        queue = upcoming,
        onPlayPause = { player.togglePlayback() },
        onSeek = { player.seekTo(it.toLong() * 1000) },
        onSkipBack = { player.seekTo(player.state.value.positionMillis - 15_000) },
        onSkipForward = { player.seekTo(player.state.value.positionMillis + 30_000) },
        onSpeed = { player.setSpeed(it) },
        onEpisode = { index -> audio.upcomingEpisodes.getOrNull(index)?.let { player.play(it) } },
        error = audio.error,
        isPreview = false,
        artworkUrl = current.artworkUrl,
      )
    return Model(collapsed = BarModel(model), expanded = ScreenModel(model))
  }

  @Composable
  private fun presentFake(audio: Player.State): Model {
    val playlists by playlistRepository.playlists.collectAsState()
    val hasEpisodes = playlists.any { it.episodeIds.isNotEmpty() }
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
    var speed by remember { mutableFloatStateOf(1.2f) }

    val placeholder =
      PlayerModel(
        title = episode.title,
        showTitle = episode.showTitle,
        positionSeconds = positionSeconds,
        durationSeconds = episode.durationSeconds,
        isPlaying = isPlaying,
        canSeek = true,
        speed = speed,
        queue = queue,
        onPlayPause = { isPlaying = !isPlaying },
        onSeek = { positionSeconds = it.coerceIn(0, episode.durationSeconds) },
        onSkipBack = { positionSeconds = (positionSeconds - 15).coerceAtLeast(0) },
        onSkipForward = {
          positionSeconds = (positionSeconds + 30).coerceAtMost(episode.durationSeconds)
        },
        onSpeed = {
          if (it.isFinite()) {
            speed = it.coerceIn(0.5f, 3f)
          }
        },
        onEpisode = { index ->
          queue.getOrNull(index)?.let { next ->
            episode = next
            queue = queue.filterIndexed { queueIndex, _ -> queueIndex != index }
            positionSeconds = 0
            isPlaying = true
          }
        },
      )

    val playerModel =
      if (audio.title == null) {
        placeholder
      } else {
        val duration = (audio.durationMillis / 1000).toInt()
        val position = (audio.positionMillis / 1000).toInt()
        placeholder.copy(
          title = requireNotNull(audio.title),
          showTitle = "Storymile test episodes",
          positionSeconds = position,
          durationSeconds = duration,
          isPlaying = audio.playWhenReady || audio.isPlaying,
          canSeek = audio.canSeek,
          speed = audio.speed,
          error = audio.error,
          onPlayPause = { player.togglePlayback() },
          onSeek = { player.seekTo(it.toLong() * 1000) },
          onSkipBack = { player.seekTo(player.state.value.positionMillis - 15_000) },
          onSkipForward = {
            player.seekTo(player.state.value.positionMillis + 30_000)
          },
          onSpeed = { player.setSpeed(it) },
        )
      }

    return Model(
      collapsed = BarModel(playerModel).takeIf { hasEpisodes },
      expanded = ScreenModel(playerModel).takeIf { hasEpisodes },
    )
  }

  private val Player.State.canSeek: Boolean
    get() = isReady && !isComplete && error == null

  data class Episode(val title: String, val showTitle: String, val durationSeconds: Int)

  data class PlayerModel(
    val title: String,
    val showTitle: String,
    val positionSeconds: Int,
    val durationSeconds: Int,
    val isPlaying: Boolean,
    val canSeek: Boolean,
    val speed: Float,
    val queue: List<Episode>,
    val onPlayPause: () -> Unit,
    val onSeek: (Int) -> Unit,
    val onSkipBack: () -> Unit,
    val onSkipForward: () -> Unit,
    val onSpeed: (Float) -> Unit,
    val onEpisode: (Int) -> Unit,
    val error: String? = null,
    val isPreview: Boolean = true,
    val artworkUrl: String? = null,
    val description: String? = null,
  )

  data class BarModel(val player: PlayerModel) : BaseModel

  data class ScreenModel(val player: PlayerModel) : BaseModel

  /** Renderer-only state and actions stay shared while the playback layer expands or collapses. */
  data class Model(override val collapsed: BarModel?, override val expanded: ScreenModel?) :
    PlaybackPresenter.Model
}
