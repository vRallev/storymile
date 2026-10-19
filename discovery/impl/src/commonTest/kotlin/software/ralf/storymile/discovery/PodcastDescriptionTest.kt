package software.ralf.storymile.discovery

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test

class PodcastDescriptionTest {
  @Test
  fun `feed markup becomes readable text with line breaks and decoded entities`() {
    assertThat(
        PodcastDescription.plainText(
          "<p>Science &amp; stories&nbsp;&#8212; &#x1F30D;</p><div>With <b>guests</b>.<br>Every week.</div>",
        ),
      )
      .isEqualTo("Science & stories — 🌍\n\nWith guests.\nEvery week.")
  }

  @Test
  fun `hidden markup is omitted and encoded tags remain literal text`() {
    assertThat(
        PodcastDescription.plainText(
          "<style>\np { color: red; }\n</style><!-- hidden --><script>ignore()</script>About &lt;science&gt; &unknown; &#99999999;",
        ),
      )
      .isEqualTo("About <science> &unknown; �")
  }

  @Test
  fun `quoted delimiters malformed markup and HTML entities retain readable content`() {
    assertThat(
        PodcastDescription.plainText(
          "<p title=\"1 > 0\">Caf&eacute; &NotEqualTilde; &#X1F30D;<p>Second <b>paragraph",
        ),
      )
      .isEqualTo("Café ≂̸ 🌍\n\nSecond paragraph")
  }

  @Test
  fun `plain text paragraphs and empty descriptions are preserved`() {
    assertThat(PodcastDescription.plainText("One < two & three\n\nAnother paragraph."))
      .isEqualTo("One < two & three\n\nAnother paragraph.")
    assertThat(PodcastDescription.plainText(null)).isNull()
    assertThat(PodcastDescription.plainText("<p> &nbsp; </p>\n")).isNull()
  }
}
