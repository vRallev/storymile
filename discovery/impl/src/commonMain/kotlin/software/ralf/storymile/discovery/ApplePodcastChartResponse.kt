package software.ralf.storymile.discovery

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal class ApplePodcastChartResponse {
  private val json = Json { ignoreUnknownKeys = true }

  fun decode(body: String): List<DiscoveredPodcast> =
    json.decodeFromString<Response>(body).feed.results.map { it.toPodcast() }

  @Serializable private data class Response(val feed: Feed)

  @Serializable private data class Feed(val results: List<Podcast>)

  @Serializable
  private data class Podcast(
    val id: String,
    @SerialName("name") val title: String,
    @SerialName("artistName") val author: String? = null,
    @SerialName("artworkUrl100") val artworkUrl: String? = null,
    @SerialName("url") val catalogUrl: String? = null,
  ) {
    fun toPodcast(): DiscoveredPodcast {
      require(id.toLongOrNull()?.let { it > 0 } == true) { "Missing or invalid podcast ID." }
      require(title.isNotBlank()) { "Missing podcast title." }
      return DiscoveredPodcast(
        id = id,
        title = title,
        author = author.nonBlank(),
        feedUrl = null,
        artworkUrl = artworkUrl.nonBlank()?.let { ApplePodcastArtwork.highResolutionUrl(it) },
        catalogUrl = catalogUrl.nonBlank(),
      )
    }

    private fun String?.nonBlank(): String? = this?.takeIf { it.isNotBlank() }
  }
}
