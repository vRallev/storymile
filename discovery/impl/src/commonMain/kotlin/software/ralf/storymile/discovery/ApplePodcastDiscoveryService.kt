package software.ralf.storymile.discovery

import dev.zacsweers.metro.Inject
import software.ralf.storymile.http.HttpClient
import software.ralf.storymile.http.HttpResponse
import software.ralf.storymile.util.Device

@Inject
class ApplePodcastDiscoveryService(private val client: HttpClient, private val device: Device) :
  PodcastDiscoveryService {
  override suspend fun search(query: String): HttpResponse<List<DiscoveredPodcast>> {
    val term = query.trim()
    if (term.isEmpty()) {
      return HttpResponse.Success(emptyList(), statusCode = 200)
    }
    return client.get(
      url = "https://itunes.apple.com/search",
      parameters =
        mapOf(
          "term" to term,
          "media" to "podcast",
          "entity" to "podcast",
          "country" to (device.countryCode ?: "US"),
          "limit" to "200",
        ),
      decode = { ApplePodcastSearchResponse().decode(it) },
    )
  }

  override suspend fun popular(): HttpResponse<List<DiscoveredPodcast>> =
    client.get(
      url = "https://rss.marketingtools.apple.com/api/v2/us/podcasts/top/25/podcasts.json",
      decode = { ApplePodcastChartResponse().decode(it) },
    )
}
