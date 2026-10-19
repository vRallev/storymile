package software.ralf.storymile.util

import com.fleeksoft.ksoup.Ksoup

/** Converts HTML descriptions to readable text with paragraph and line breaks. */
object HtmlDescription {
  /** Omits hidden content, decodes HTML entities, and returns null when no text remains. */
  fun plainText(description: String?): String? {
    if (description.isNullOrBlank()) {
      return null
    }
    val body = Ksoup.parseBodyFragment(description).body()
    body
      .getAllElements()
      .filter { it.isBlock() }
      .forEach { element ->
        element.prependText("\n")
        element.appendText("\n")
      }
    return body
      .wholeText()
      .replace('\u00a0', ' ')
      .lines()
      .joinToString("\n") { it.replace(Regex("[\\t ]+"), " ").trim() }
      .replace(Regex("\n{3,}"), "\n\n")
      .trim()
      .takeIf { it.isNotEmpty() }
  }
}
