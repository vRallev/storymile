package software.ralf.storymile.discovery

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

internal class ApplePodcastSearchResponse {
  fun decode(body: String): List<DiscoveredPodcast> {
    val document = Json.parseToJsonElement(body).jsonObject
    val results =
      requireNotNull(document["results"]) { "Missing podcast search results." }.jsonArray
    return results.map { element ->
      val podcast = element.jsonObject
      val id = (podcast["collectionId"] as? JsonPrimitive)?.longOrNull
      require(id != null && id > 0) { "Missing or invalid podcast ID." }
      val title = podcast.string("collectionName")
      require(!title.isNullOrBlank()) { "Missing podcast title." }
      DiscoveredPodcast(
        id = id.toString(),
        title = title,
        author = podcast.string("artistName"),
        feedUrl = podcast.string("feedUrl"),
        artworkUrl = podcast.string("artworkUrl600") ?: podcast.string("artworkUrl100"),
        catalogUrl = podcast.string("collectionViewUrl"),
      )
    }
  }

  private fun JsonObject.string(key: String): String? {
    val value = this[key] ?: return null
    require(value is JsonPrimitive) { "Invalid podcast field: $key." }
    if (value.contentOrNull == null) {
      return null
    }
    require(value.isString) { "Invalid podcast field: $key." }
    return value.content.takeIf { it.isNotBlank() }
  }
}
