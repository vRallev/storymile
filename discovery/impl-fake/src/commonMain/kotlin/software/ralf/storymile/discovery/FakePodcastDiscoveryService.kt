package software.ralf.storymile.discovery

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import software.ralf.storymile.http.HttpResponse
import software.ralf.storymile.podcast.PodcastFeed

@Inject
class FakePodcastDiscoveryService(@Named("fakePodcastFeeds") feeds: Map<String, PodcastFeed>) :
  PodcastDiscoveryService {
  private val podcasts = feeds.map { (id, feed) ->
    DiscoveredPodcast(
      id = id,
      title = feed.title,
      author = feed.author,
      feedUrl = feed.feedUrl,
      artworkUrl = feed.artworkUrl,
      catalogUrl = null,
    )
  }

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
}
