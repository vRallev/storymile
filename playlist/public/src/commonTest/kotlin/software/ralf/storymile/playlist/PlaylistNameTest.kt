package software.ralf.storymile.playlist

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import kotlin.test.Test

class PlaylistNameTest {
  @Test
  fun `surrounding whitespace is removed while internal whitespace is preserved`() {
    val name = PlaylistName("\u00a0\t Morning  stories\n ")

    assertThat(name.value).isEqualTo("Morning  stories")
    assertThat(name).isEqualTo(PlaylistName("Morning  stories"))
  }

  @Test
  fun `names must contain text`() {
    for (value in listOf("", " \t\n ", "\u00a0\u2003")) {
      assertFailure { PlaylistName(value) }.isInstanceOf<IllegalArgumentException>()
    }
  }
}
