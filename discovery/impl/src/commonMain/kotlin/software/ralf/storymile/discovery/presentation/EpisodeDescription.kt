package software.ralf.storymile.discovery.presentation

internal object EpisodeDescription {
  private val hiddenContent =
    Regex(
      pattern = "<!--.*?-->|<(script|style)\\b[^>]*>.*?</\\1\\s*>",
      options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
  private val lineBreaks =
    Regex(
      pattern = "<\\s*(?:br\\b[^>]*|/?(?:p|div|li|h[1-6]|ul|ol|blockquote)\\b[^>]*)>",
      option = RegexOption.IGNORE_CASE,
    )
  private val tags = Regex("</?[a-zA-Z][^>]*>")
  private val entities = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);")
  private val namedEntities =
    mapOf(
      "amp" to "&",
      "lt" to "<",
      "gt" to ">",
      "quot" to "\"",
      "apos" to "'",
      "nbsp" to " ",
      "ndash" to "–",
      "mdash" to "—",
      "lsquo" to "‘",
      "rsquo" to "’",
      "ldquo" to "“",
      "rdquo" to "”",
      "hellip" to "…",
      "copy" to "©",
    )

  fun plainText(description: String?): String {
    val text =
      description.orEmpty().replace(hiddenContent, "").replace(lineBreaks, "\n").replace(tags, "")
    return entities
      .replace(text) { match ->
        val entity = match.groupValues[1]
        namedEntities[entity] ?: decodeCodePoint(entity) ?: match.value
      }
      .replace(Regex("[^\\S\\n]+"), " ")
      .replace(Regex(" *\n *"), "\n")
      .replace(Regex("\n{3,}"), "\n\n")
      .trim()
  }

  private fun decodeCodePoint(entity: String): String? {
    val codePoint =
      when {
        entity.startsWith("#x") -> entity.drop(2).toIntOrNull(16)
        entity.startsWith("#") -> entity.drop(1).toIntOrNull()
        else -> null
      } ?: return null
    return when (codePoint) {
      in 1..0xD7FF,
      in 0xE000..0xFFFF -> codePoint.toChar().toString()
      in 0x10000..0x10FFFF -> {
        val offset = codePoint - 0x10000
        "${(0xD800 + (offset shr 10)).toChar()}${(0xDC00 + (offset and 0x3FF)).toChar()}"
      }
      else -> null
    }
  }
}
