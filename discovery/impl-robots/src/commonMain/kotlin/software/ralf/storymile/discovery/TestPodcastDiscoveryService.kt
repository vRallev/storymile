package software.ralf.storymile.discovery

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import software.ralf.storymile.http.HttpResponse

/** Supplies a local catalog without fetching feeds, artwork, or network responses. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, replaces = [PodcastDiscoveryServiceImpl::class])
class TestPodcastDiscoveryService : PodcastDiscoveryService {
  private val podcasts =
    listOf(
      podcast("quiet-current", "The Quiet Current", "Stories from the coast"),
      podcast("field-notes", "Field Notes", "Journeys into the wild"),
      podcast("small-wonders", "Small Wonders", "Everyday stories"),
    )

  private var failNextRequest = false

  /** Query used by the last completed request, or an empty string for popular shows. */
  var lastQuery: String? = null
    private set

  /** Completed requests, including failures, since this test graph was created. */
  var completedRequestCount: Int = 0
    private set

  /** Fails the next catalog request, after which requests succeed again. */
  fun failNextRequest() {
    failNextRequest = true
  }

  override suspend fun search(query: String): HttpResponse<List<DiscoveredPodcast>> {
    val term = query.trim()
    val results =
      when {
        term.equals("Nature", ignoreCase = true) -> podcasts.take(2)
        term.equals("Nature Oceans", ignoreCase = true) -> podcasts.take(1)
        term.equals("Nature Outdoors", ignoreCase = true) -> listOf(podcasts[1])
        term.isBlank() -> emptyList()
        else ->
          podcasts.filter {
            it.title.contains(term, ignoreCase = true) ||
              it.author.orEmpty().contains(term, ignoreCase = true)
          }
      }
    return response(results, term)
  }

  override suspend fun lookup(id: String): HttpResponse<List<DiscoveredPodcast>> =
    HttpResponse.Success(podcasts.filter { it.id == id }, statusCode = 200)

  override suspend fun popular(): HttpResponse<List<DiscoveredPodcast>> = response(podcasts, "")

  private fun response(
    podcasts: List<DiscoveredPodcast>,
    query: String,
  ): HttpResponse<List<DiscoveredPodcast>> {
    lastQuery = query
    completedRequestCount++
    if (failNextRequest) {
      failNextRequest = false
      return HttpResponse.Error.Server(503)
    }
    return HttpResponse.Success(podcasts, statusCode = 200)
  }

  private fun podcast(id: String, title: String, author: String): DiscoveredPodcast =
    DiscoveredPodcast(
      id = id,
      title = title,
      author = author,
      feedUrl = "https://feeds.test/$id",
      artworkUrl = null,
      catalogUrl = null,
    )
}
