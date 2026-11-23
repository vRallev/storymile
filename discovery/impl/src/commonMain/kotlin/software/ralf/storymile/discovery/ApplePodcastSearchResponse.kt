package software.ralf.storymile.discovery

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal object ApplePodcastSearchResponse {
  fun decode(body: String): List<DiscoveredPodcast> =
    applePodcastJson.decodeFromString<Response>(body).results.map { it.toPodcast() }

  @Serializable private data class Response(val results: List<Podcast>)

  @Serializable
  private data class Podcast(
    @SerialName("collectionId") val id: Long,
    @SerialName("collectionName") val title: String,
    @SerialName("artistName") val author: String? = null,
    val feedUrl: String? = null,
    @SerialName("artworkUrl600") val largeArtworkUrl: String? = null,
    @SerialName("artworkUrl100") val smallArtworkUrl: String? = null,
    @SerialName("collectionViewUrl") val catalogUrl: String? = null,
  ) {
    fun toPodcast(): DiscoveredPodcast {
      require(id > 0) { "Missing or invalid podcast ID." }
      require(title.isNotBlank()) { "Missing podcast title." }
      return DiscoveredPodcast(
        id = id.toString(),
        title = title,
        author = author.nonBlank(),
        feedUrl = feedUrl.nonBlank(),
        artworkUrl =
          (largeArtworkUrl.nonBlank() ?: smallArtworkUrl.nonBlank())?.let {
            ApplePodcastArtwork.highResolutionUrl(it)
          },
        catalogUrl = catalogUrl.nonBlank(),
      )
    }
  }
}
