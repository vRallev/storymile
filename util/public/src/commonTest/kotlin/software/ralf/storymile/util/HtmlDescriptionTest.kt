package software.ralf.storymile.util

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test

class HtmlDescriptionTest {
  @Test
  fun `feed markup becomes readable text with line breaks and decoded entities`() {
    assertThat(
        HtmlDescription.plainText(
          "<p>Science &amp; stories&nbsp;&#8212; &#x1F30D;</p><div>With <b>guests</b>.<br>Every week.</div>",
        ),
      )
      .isEqualTo("Science & stories — 🌍\n\nWith guests.\nEvery week.")
  }

  @Test
  fun `hidden markup is omitted and encoded tags remain literal text`() {
    assertThat(
        HtmlDescription.plainText(
          "<style>\np { color: red; }\n</style><!-- hidden --><script>ignore()</script>About &lt;science&gt; &unknown; &#99999999;",
        ),
      )
      .isEqualTo("About <science> &unknown; �")
  }

  @Test
  fun `quoted delimiters malformed markup and HTML entities retain readable content`() {
    assertThat(
        HtmlDescription.plainText(
          "<p title=\"1 > 0\">Caf&eacute; &NotEqualTilde; &#X1F30D;<p>Second <b>paragraph",
        ),
      )
      .isEqualTo("Café ≂̸ 🌍\n\nSecond paragraph")
  }

  @Test
  fun `entities decode once including unicode while unknown names remain text`() {
    assertThat(
        HtmlDescription.plainText(
          "&ldquo;Hello&nbsp;&#233;&#x1F30D;&rdquo; &amp;lt; &unknown;",
        ),
      )
      .isEqualTo("“Hello é🌍” &lt; &unknown;")
  }

  @Test
  fun `plain text paragraphs and angle comparisons are preserved`() {
    assertThat(HtmlDescription.plainText("One < two & three\n\nAnother paragraph."))
      .isEqualTo("One < two & three\n\nAnother paragraph.")
    assertThat(HtmlDescription.plainText("2 < 3 and 5 > 4")).isEqualTo("2 < 3 and 5 > 4")
  }

  @Test
  fun `absent or empty descriptions return null`() {
    listOf(null, "", "  ", "<p> &nbsp; </p>\n").forEach {
      assertThat(HtmlDescription.plainText(it)).isNull()
    }
  }
}
