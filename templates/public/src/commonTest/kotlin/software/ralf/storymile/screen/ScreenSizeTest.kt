package software.ralf.storymile.screen

import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class ScreenSizeTest {
  @Test
  fun `category boundaries apply to the shorter side in either orientation`() {
    listOf(
        599 to ScreenSize.Category.PHONE,
        600 to ScreenSize.Category.TABLET_SMALL,
        839 to ScreenSize.Category.TABLET_SMALL,
        840 to ScreenSize.Category.TABLET_LARGE,
      )
      .forEach { (shortSide, category) ->
        val portrait = ScreenSize.from(width = shortSide.dp, height = 1200.dp)
        val landscape = ScreenSize.from(width = 1200.dp, height = shortSide.dp)

        assertThat(portrait.category).isEqualTo(category)
        assertThat(landscape.category).isEqualTo(category)
        assertThat(portrait.orientation).isEqualTo(ScreenSize.Orientation.PORTRAIT)
        assertThat(landscape.orientation).isEqualTo(ScreenSize.Orientation.LANDSCAPE)
      }
  }

  @Test
  fun `square windows use portrait orientation`() {
    assertThat(ScreenSize.from(width = 600.dp, height = 600.dp).orientation)
      .isEqualTo(ScreenSize.Orientation.PORTRAIT)
  }
}
