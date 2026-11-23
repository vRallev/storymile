package software.ralf.storymile.libraryqueue

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import software.ralf.storymile.episodedetails.EpisodeDetails

internal class LibraryQueueDrag(private val list: LazyListState) {
  var key by mutableStateOf<String?>(null)
    private set

  private var sourceIndex by mutableIntStateOf(-1)
  private var targetIndex by mutableIntStateOf(-1)
  private var center by mutableFloatStateOf(0f)

  val awaitingSave: Boolean
    get() = key == null && sourceIndex != targetIndex

  fun start(episodeKey: String) {
    if (key != null || awaitingSave) {
      return
    }
    val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == episodeKey } ?: return
    sourceIndex = item.index
    targetIndex = sourceIndex
    center = item.center
    key = episodeKey
  }

  fun dragBy(episodeKey: String, delta: Float) {
    if (key == episodeKey) {
      center += delta
      updateTarget()
    }
  }

  fun offset(episodeKey: String): Float {
    if (key != episodeKey) {
      return 0f
    }
    val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == episodeKey } ?: return 0f
    return center - item.center
  }

  fun preview(episodes: List<EpisodeDetails>): List<EpisodeDetails> {
    if (sourceIndex == targetIndex) {
      return episodes
    }
    return QueueDragPreview(episodes = episodes, source = sourceIndex, target = targetIndex)
  }

  fun drop(
    episodeKey: String,
    episodes: List<EpisodeDetails>,
    onReorder: (EpisodeDetails, EpisodeDetails) -> Unit,
  ) {
    if (key != episodeKey) {
      return
    }
    val source = episodes[sourceIndex]
    val target = episodes[targetIndex]
    key = null
    if (source != target) {
      onReorder(source, target)
    }
  }

  fun cancel(episodeKey: String) {
    if (key == episodeKey) {
      restoreSavedOrder()
      key = null
    }
  }

  fun restoreSavedOrder() {
    targetIndex = sourceIndex
  }

  suspend fun scrollNearEdges(edge: Float) {
    var previousFrame = withFrameNanos { it }
    while (key != null) {
      val frame = withFrameNanos { it }
      if (key == null) {
        return
      }
      val seconds = ((frame - previousFrame) / 1_000_000_000f).coerceAtMost(0.05f)
      previousFrame = frame
      val layout = list.layoutInfo
      val threshold = edge.coerceAtMost(layout.viewportSize.height / 2f)
      val top = layout.viewportStartOffset + threshold
      val bottom = layout.viewportEndOffset - threshold
      val distance =
        when {
          center < top && list.canScrollBackward -> center - top
          center > bottom && list.canScrollForward -> center - bottom
          else -> 0f
        }
      if (distance != 0f) {
        list.scrollBy(distance.coerceIn(minimumValue = -edge, maximumValue = edge) * 16 * seconds)
        updateTarget()
      }
    }
  }

  private fun updateTarget() {
    val items = list.layoutInfo.visibleItemsInfo
    val dragged = items.firstOrNull { it.key == key } ?: return
    val target =
      if (center > dragged.center) {
        items.lastOrNull { it.index > dragged.index && it.center <= center }
      } else {
        items.firstOrNull { it.index < dragged.index && it.center >= center }
      }
    val destination = (target ?: dragged).index
    if (destination != targetIndex) {
      // Keep the viewport in place if the reorder moves the first visible item.
      val firstIndex = list.firstVisibleItemIndex
      val firstMovedIndex = minOf(a = dragged.index, b = destination)
      val lastMovedIndex = maxOf(a = dragged.index, b = destination)
      if (firstIndex in firstMovedIndex..lastMovedIndex) {
        list.requestScrollToItem(
          index = firstIndex,
          scrollOffset = list.firstVisibleItemScrollOffset,
        )
      }
      targetIndex = destination
    }
  }

  private val LazyListItemInfo.center: Float
    get() = offset + size / 2f
}
