package software.ralf.storymile.screen

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class ScreenSizeTest {
  @Test
  fun `landscape window below width threshold is a phone`() {
    assertCategory(width = 599.dp, height = 400.dp, expected = ScreenSize.Category.PHONE)
  }

  @Test
  fun `landscape window at width threshold is a tablet`() {
    assertCategory(width = 600.dp, height = 599.dp, expected = ScreenSize.Category.TABLET)
  }

  @Test
  fun `square window at width threshold is a tablet`() {
    assertCategory(width = 600.dp, height = 600.dp, expected = ScreenSize.Category.TABLET)
  }

  @Test
  fun `portrait window at width threshold is a tablet`() {
    assertCategory(width = 600.dp, height = 900.dp, expected = ScreenSize.Category.TABLET)
  }

  private fun assertCategory(width: Dp, height: Dp, expected: ScreenSize.Category) {
    assertThat(ScreenSize.from(width = width, height = height).category).isEqualTo(expected)
  }
}
