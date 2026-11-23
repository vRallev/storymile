package software.ralf.storymile.libraryqueue

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class QueueDragPreviewTest {
  @Test
  fun `moving a row shifts only the intervening rows in either direction`() {
    val queue = (0..5).toList()
    for (source in queue.indices) {
      for (target in queue.indices) {
        val expected = queue.toMutableList().apply { add(target, removeAt(source)) }
        assertThat(QueueDragPreview(episodes = queue, source = source, target = target).toList())
          .isEqualTo(expected)
      }
    }
  }
}
