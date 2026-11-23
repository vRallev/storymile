package software.ralf.storymile.util

import com.fleeksoft.ksoup.Ksoup

/** Converts HTML descriptions to readable text with paragraph and line breaks. */
object HtmlDescription {
  private val horizontalWhitespace = Regex("[\\t ]+")
  private val extraLineBreaks = Regex("\n{3,}")

  /** Omits hidden content, decodes HTML entities, and returns null when no text remains. */
  fun plainText(description: String?): String? {
    if (description.isNullOrBlank()) {
      return null
    }
    // Tags, entities, and tokenizer control characters need the HTML parser.
    val text =
      if (description.any { it == '<' || it == '&' || it == '\u0000' || it == '\uFFFF' }) {
        extractText(description)
      } else {
        description
      }
    return text
      .replace('\u00a0', ' ')
      .lineSequence()
      .joinToString("\n") { it.replace(horizontalWhitespace, " ").trim() }
      .replace(extraLineBreaks, "\n\n")
      .trim()
      .takeIf { it.isNotEmpty() }
  }

  private fun extractText(html: String): String {
    val body = Ksoup.parseBodyFragment(html).body()
    body.getAllElements().forEach { element ->
      if (element.isBlock()) {
        element.prependText("\n")
        element.appendText("\n")
      }
    }
    return body.wholeText()
  }
}
