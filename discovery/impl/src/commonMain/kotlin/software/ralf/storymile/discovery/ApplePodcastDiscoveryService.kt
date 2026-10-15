package software.ralf.storymile.discovery

import dev.zacsweers.metro.Inject
import software.ralf.storymile.http.HttpClient
import software.ralf.storymile.http.HttpResponse

@Inject
class ApplePodcastDiscoveryService(private val client: HttpClient) : PodcastDiscoveryService {
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
          "country" to "US",
          "limit" to "25",
        ),
      decode = { ApplePodcastSearchResponse().decode(it) },
    )
  }
}
