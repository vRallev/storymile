package software.ralf.storymile.discovery.presentation

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class EpisodeDescriptionTest {
  @Test
  fun `feed markup becomes readable text without embedded content`() {
    assertThat(
        EpisodeDescription.plainText(
          """
          <p>Stories &amp; science with <b>Ada</b>.</p>
          <p>Listen <a href="https://example.test">here</a>.<br>Next line.</p>
          <!-- hidden --><script>alert('hidden')</script><style>hidden {}</style>
          """
            .trimIndent(),
        ),
      )
      .isEqualTo("Stories & science with Ada.\n\nListen here.\nNext line.")
  }

  @Test
  fun `entities decode once including unicode while invalid entities remain text`() {
    assertThat(
        EpisodeDescription.plainText(
          "&ldquo;Hello&nbsp;&#233;&#x1F30D;&rdquo; &amp;lt; &#0; &#xD800; &unknown;",
        ),
      )
      .isEqualTo("“Hello é🌍” &lt; &#0; &#xD800; &unknown;")
    assertThat(EpisodeDescription.plainText("2 < 3 and 5 > 4")).isEqualTo("2 < 3 and 5 > 4")
  }

  @Test
  fun `absent or empty descriptions can use the UI fallback`() {
    listOf(null, "", "  ", "<p>&nbsp;</p>").forEach {
      assertThat(EpisodeDescription.plainText(it)).isEqualTo("")
    }
  }
}
