package software.ralf.storymile.podcast

import io.ktor.http.URLBuilder
import io.ktor.http.URLParserException
import io.ktor.http.URLProtocol
import io.ktor.http.takeFrom

object RssUrl {
  fun normalize(url: String): String? = normalize(url, parsed = null)

  private fun normalize(url: String, parsed: URLBuilder?): String? {
    if (
      (!url.startsWith("http://", ignoreCase = true) &&
        !url.startsWith("https://", ignoreCase = true)) ||
        url.any { it.isWhitespace() || it.isISOControl() || it == '\\' } ||
        url.substringAfter("://").startsWith('/')
    ) {
      return null
    }
    return parse {
      val builder = parsed ?: URLBuilder().takeFrom(url)
      if (
        builder.host.isBlank() ||
          builder.user != null ||
          builder.password != null ||
          (builder.protocol != URLProtocol.HTTP && builder.protocol != URLProtocol.HTTPS)
      ) {
        return@parse null
      }
      builder.fragment = ""
      builder.encodedPathSegments = removeDotSegments(builder.encodedPathSegments)
      builder.buildString()
    }
  }

  fun resolve(baseUrl: String, reference: String): String? {
    val target = reference.trim()
    if (target.isEmpty() || target.any { it.isWhitespace() || it.isISOControl() || it == '\\' }) {
      return null
    }
    val colon = target.indexOf(':')
    val firstDelimiter = target.indexOfAny(charArrayOf('/', '?', '#'))
    if (colon >= 0 && (firstDelimiter == -1 || colon < firstDelimiter)) {
      return normalize(target)
    }
    return parse {
      val builder = URLBuilder().takeFrom(baseUrl)
      if (!target.startsWith('#')) {
        builder.parameters.clear()
        builder.trailingQuery = false
      }
      builder.fragment = ""
      builder.takeFrom(target)
      normalize(url = builder.buildString(), parsed = builder)
    }
  }

  private inline fun parse(block: () -> String?): String? =
    try {
      block()
    } catch (_: URLParserException) {
      null
    } catch (_: IllegalArgumentException) {
      null
    }

  private fun removeDotSegments(segments: List<String>): List<String> {
    val result = mutableListOf<String>()
    segments.forEachIndexed { index, segment ->
      when (segment) {
        ".",
        ".." -> {
          if (segment == ".." && (result.size > 1 || result.lastOrNull()?.isNotEmpty() == true)) {
            result.removeAt(result.lastIndex)
          }
          if (index == segments.lastIndex) {
            result.add("")
          }
        }
        else -> result.add(segment)
      }
    }
    return result
  }
}
