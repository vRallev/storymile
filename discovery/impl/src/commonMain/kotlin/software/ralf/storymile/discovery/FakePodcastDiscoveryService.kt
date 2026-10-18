package software.ralf.storymile.discovery

import dev.zacsweers.metro.Inject
import software.ralf.storymile.http.HttpResponse

@Inject
class FakePodcastDiscoveryService : PodcastDiscoveryService {
  private val podcasts =
    listOf(
      podcast("science", "Small Science", "Storymile Science"),
      podcast("history", "History on Foot", "Storymile Stories"),
      podcast("design", "Design Notes", "Storymile Studio"),
    )

  override suspend fun search(query: String): HttpResponse<List<DiscoveredPodcast>> {
    val term = query.trim()
    val results =
      if (term.isEmpty()) {
        emptyList()
      } else {
        podcasts.filter {
          it.title.contains(term, ignoreCase = true) ||
            it.author.orEmpty().contains(term, ignoreCase = true)
        }
      }
    return HttpResponse.Success(results, statusCode = 200)
  }

  override suspend fun lookup(id: String): HttpResponse<List<DiscoveredPodcast>> =
    HttpResponse.Success(podcasts.filter { it.id == id }, statusCode = 200)

  override suspend fun popular(): HttpResponse<List<DiscoveredPodcast>> =
    HttpResponse.Success(podcasts, statusCode = 200)

  private fun podcast(id: String, title: String, author: String): DiscoveredPodcast =
    DiscoveredPodcast(
      id = "sample-$id",
      title = title,
      author = author,
      feedUrl = "https://example.invalid/podcasts/$id.xml",
      artworkUrl = null,
      catalogUrl = null,
    )
}
