package software.ralf.storymile.rss

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.ExperimentalXmlUtilApi
import nl.adaptivity.xmlutil.XmlException
import nl.adaptivity.xmlutil.XmlReader
import nl.adaptivity.xmlutil.core.KtXmlReader

@OptIn(ExperimentalXmlUtilApi::class)
internal class RssFeedParser {
  fun decode(xml: String, feedUrl: String): PodcastFeed {
    require(xml.length <= 10_000_000) { "The RSS document is too large." }
    rejectDocumentTypes(xml)
    val reader = KtXmlReader(xml, expandEntities = true)
    try {
      var feed: PodcastFeed? = null
      while (reader.hasNext()) {
        when (reader.nextChecked()) {
          EventType.START_ELEMENT -> {
            require(feed == null && reader.matches("rss")) { "Expected one RSS document." }
            require(reader.getAttributeValue(null, "version") == "2.0") {
              "Expected RSS version 2.0."
            }
            reader.readChildren {
              if (reader.matches("channel")) {
                require(feed == null) { "Expected one RSS channel." }
                feed = reader.readChannel(feedUrl)
              } else {
                reader.skipElement()
              }
            }
          }
          EventType.TEXT,
          EventType.CDSECT -> {
            require(reader.text.isBlank()) { "Unexpected content outside the RSS document." }
          }
          else -> Unit
        }
      }
      return requireNotNull(feed) { "The RSS channel is missing." }
    } catch (exception: XmlException) {
      throw IllegalArgumentException("The RSS document is invalid.", exception)
    } finally {
      reader.close()
    }
  }

  private fun XmlReader.readChannel(feedUrl: String): PodcastFeed {
    val metadata = Metadata()
    val episodes = mutableListOf<Episode>()
    var link: String? = null
    readChildren {
      when {
        matches("item") -> readEpisode(feedUrl)?.let { episodes += it }
        matches("link") -> {
          val value = RssUrl.resolve(feedUrl, readText())
          link = link ?: value
        }
        !readMetadata(metadata, feedUrl) -> skipElement()
      }
    }
    val author = metadata.author ?: metadata.rssAuthor
    val artworkUrl = metadata.artworkUrl ?: metadata.rssArtworkUrl
    return PodcastFeed(
      feedUrl = feedUrl,
      title = requireNotNull(metadata.title) { "The RSS channel title is missing." },
      description = metadata.description(),
      author = author,
      artworkUrl = artworkUrl,
      link = link,
      episodes =
        episodes
          .distinctBy { it.id }
          .map { episode ->
            PodcastEpisode(
              id = episode.id,
              title = checkNotNull(episode.metadata.title),
              description = episode.metadata.description(),
              author = episode.metadata.author ?: episode.metadata.rssAuthor ?: author,
              artworkUrl =
                episode.metadata.artworkUrl ?: episode.metadata.rssArtworkUrl ?: artworkUrl,
              publishedAt = episode.publishedAt,
              duration = episode.duration,
              enclosure = episode.enclosure,
            )
          },
    )
  }

  private fun XmlReader.readEpisode(feedUrl: String): Episode? {
    val metadata = Metadata()
    var id: String? = null
    var publishedAt: String? = null
    var duration: Duration? = null
    var enclosure: PodcastEnclosure? = null
    readChildren {
      when {
        matches("guid") -> id = readText().nonBlank() ?: id
        matches("pubDate") -> publishedAt = readText().nonBlank() ?: publishedAt
        matches("duration", ITUNES_NAMESPACE) -> duration = readText().duration() ?: duration
        matches("enclosure") -> {
          val value = readEnclosure(feedUrl)
          enclosure = enclosure ?: value
        }
        !readMetadata(metadata, feedUrl) -> skipElement()
      }
    }
    val media = enclosure ?: return null
    if (metadata.title == null) {
      return null
    }
    return Episode(id ?: media.url, metadata, publishedAt, duration, media)
  }

  private fun XmlReader.readEnclosure(feedUrl: String): PodcastEnclosure? {
    val url = getAttributeValue(null, "url")?.let { RssUrl.resolve(feedUrl, it) }
    val enclosure = url?.let {
      PodcastEnclosure(
        url = it,
        type = getAttributeValue(null, "type")?.nonBlank(),
        length = getAttributeValue(null, "length")?.trim()?.toLongOrNull()?.takeIf { it >= 0 },
      )
    }
    skipElement()
    return enclosure
  }

  private fun XmlReader.readMetadata(metadata: Metadata, feedUrl: String): Boolean {
    when {
      matches("title") -> metadata.title = readText().nonBlank() ?: metadata.title
      matches("author", ITUNES_NAMESPACE) ->
        metadata.author = readText().nonBlank() ?: metadata.author
      matches("author") || matches("managingEditor") ->
        metadata.rssAuthor = readText().nonBlank() ?: metadata.rssAuthor
      else -> return readDescription(metadata) || readArtwork(metadata, feedUrl)
    }
    return true
  }

  private fun XmlReader.readDescription(metadata: Metadata): Boolean {
    when {
      matches("description") -> metadata.description = readText().nonBlank() ?: metadata.description
      matches("encoded", CONTENT_NAMESPACE) ->
        metadata.content = readText().nonBlank() ?: metadata.content
      matches("summary", ITUNES_NAMESPACE) ->
        metadata.summary = readText().nonBlank() ?: metadata.summary
      else -> return false
    }
    return true
  }

  private fun XmlReader.readArtwork(metadata: Metadata, feedUrl: String): Boolean {
    when {
      matches("image", ITUNES_NAMESPACE) -> {
        metadata.artworkUrl =
          getAttributeValue(null, "href")?.let { RssUrl.resolve(feedUrl, it) }
            ?: metadata.artworkUrl
        skipElement()
      }
      matches("image") -> {
        readChildren {
          if (matches("url")) {
            metadata.rssArtworkUrl = RssUrl.resolve(feedUrl, readText()) ?: metadata.rssArtworkUrl
          } else {
            skipElement()
          }
        }
      }
      else -> return false
    }
    return true
  }

  private fun XmlReader.readChildren(readChild: () -> Unit) {
    while (hasNext()) {
      when (nextChecked()) {
        EventType.START_ELEMENT -> readChild()
        EventType.END_ELEMENT -> return
        EventType.TEXT,
        EventType.CDSECT -> {
          require(text.isBlank()) { "Unexpected text in an RSS container." }
        }
        else -> Unit
      }
    }
    throw IllegalArgumentException("The RSS document is incomplete.")
  }

  private fun XmlReader.readText(): String {
    val startDepth = depth
    return buildString {
      while (hasNext()) {
        when (nextChecked()) {
          EventType.TEXT,
          EventType.CDSECT,
          EventType.IGNORABLE_WHITESPACE -> append(text)
          EventType.END_ELEMENT -> {
            if (depth == startDepth) {
              return@buildString
            }
          }
          else -> Unit
        }
      }
      throw IllegalArgumentException("The RSS element is incomplete.")
    }
  }

  private fun XmlReader.skipElement() {
    val startDepth = depth
    while (hasNext()) {
      if (nextChecked() == EventType.END_ELEMENT && depth == startDepth) {
        return
      }
    }
    throw IllegalArgumentException("The RSS element is incomplete.")
  }

  private fun XmlReader.nextChecked(): EventType {
    val event =
      try {
        next()
      } catch (exception: IllegalStateException) {
        // Xmlutil 1.0.2 throws this buffer error for bare text at EOF.
        if (exception.message != "Unexpected EOF") {
          throw exception
        }
        throw IllegalArgumentException("The RSS document is incomplete.", exception)
      }
    require(event != EventType.DOCDECL) { "RSS document types are not supported." }
    require(depth <= 64) { "The RSS document is nested too deeply." }
    return event
  }

  private fun XmlReader.matches(name: String, namespace: String = ""): Boolean =
    localName == name && namespaceURI == namespace

  private fun String.nonBlank(): String? = trim().takeIf { it.isNotEmpty() }

  private fun String.duration(): Duration? {
    val parts = trim().split(':')
    if (parts.size !in 1..3) {
      return null
    }
    var seconds = 0L
    parts.forEachIndexed { index, part ->
      if (part.isEmpty() || part.any { it !in '0'..'9' }) {
        return null
      }
      val value = part.toLongOrNull() ?: return null
      if ((index > 0 && value > 59) || seconds > (Long.MAX_VALUE - value) / 60) {
        return null
      }
      seconds = seconds * 60 + value
    }
    return seconds.seconds.takeIf { it.isFinite() }
  }

  private fun rejectDocumentTypes(xml: String) {
    var offset = 0
    while (offset < xml.length) {
      val start = xml.indexOf('<', offset)
      if (start < 0) {
        return
      }
      val terminator =
        when {
          xml.startsWith("<!--", start) -> "-->"
          xml.startsWith("<![CDATA[", start) -> "]]>"
          xml.startsWith("<?", start) -> "?>"
          else -> null
        }
      require(!xml.startsWith("<!DOCTYPE", start)) { "RSS document types are not supported." }
      offset =
        if (terminator == null) {
          start + 1
        } else {
          val end = xml.indexOf(terminator, start + 2)
          if (end < 0) {
            return
          }
          end + terminator.length
        }
    }
  }

  private companion object {
    const val ITUNES_NAMESPACE = "http://www.itunes.com/dtds/podcast-1.0.dtd"
    const val CONTENT_NAMESPACE = "http://purl.org/rss/1.0/modules/content/"
  }

  private class Metadata {
    var title: String? = null
    var description: String? = null
    var content: String? = null
    var summary: String? = null
    var author: String? = null
    var rssAuthor: String? = null
    var artworkUrl: String? = null
    var rssArtworkUrl: String? = null

    fun description(): String? = description ?: content ?: summary
  }

  private data class Episode(
    val id: String,
    val metadata: Metadata,
    val publishedAt: String?,
    val duration: Duration?,
    val enclosure: PodcastEnclosure,
  )
}
