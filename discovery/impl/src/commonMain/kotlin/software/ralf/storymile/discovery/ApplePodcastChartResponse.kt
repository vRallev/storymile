package software.ralf.storymile.discovery

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import software.ralf.storymile.util.HtmlDescription

internal class ApplePodcastChartResponse {
  private val json = Json { ignoreUnknownKeys = true }

  fun decode(body: String): List<DiscoveredPodcast> =
    json.decodeFromString<Response>(body).feed.entry.map { it.toPodcast() }

  @Serializable private data class Response(val feed: Feed)

  @Serializable private data class Feed(val entry: List<Podcast>)

  @Serializable
  private data class Podcast(
    val id: Identifier,
    @SerialName("im:name") val title: Label,
    @SerialName("im:artist") val author: Label? = null,
    @SerialName("im:image") val images: List<Image>? = null,
    val summary: Label? = null,
  ) {
    fun toPodcast(): DiscoveredPodcast {
      val podcastId = id.attributes["im:id"]
      require(podcastId?.toLongOrNull()?.let { it > 0 } == true) {
        "Missing or invalid podcast ID."
      }
      require(title.label.isNotBlank()) { "Missing podcast title." }
      val artwork =
        images
          ?.filter { it.label.isNotBlank() }
          ?.maxByOrNull { it.attributes["height"]?.toIntOrNull() ?: 0 }
      return DiscoveredPodcast(
        id = podcastId,
        title = title.label,
        author = author?.label.nonBlank(),
        feedUrl = null,
        artworkUrl = artwork?.let { ApplePodcastArtwork.highResolutionUrl(it.label) },
        catalogUrl = id.label.nonBlank(),
        description = HtmlDescription.plainText(summary?.label),
      )
    }

    private fun String?.nonBlank(): String? = this?.takeIf { it.isNotBlank() }
  }

  @Serializable private data class Label(val label: String)

  @Serializable
  private data class Identifier(val attributes: Map<String, String>, val label: String? = null)

  @Serializable
  private data class Image(val label: String, val attributes: Map<String, String> = emptyMap())
}
