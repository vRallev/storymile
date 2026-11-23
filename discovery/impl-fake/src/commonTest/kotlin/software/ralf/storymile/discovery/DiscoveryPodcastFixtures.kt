package software.ralf.storymile.discovery

import software.ralf.storymile.podcast.PodcastFeed

internal object DiscoveryPodcastFixtures {
  val feeds: Map<String, PodcastFeed> =
    mapOf(
      feed(id = "science", title = "Small Science", author = "Storymile Science"),
      feed(id = "history", title = "History on Foot", author = "Storymile Stories"),
      feed(id = "design", title = "Design Notes", author = "Storymile Studio"),
    )

  private fun feed(id: String, title: String, author: String): Pair<String, PodcastFeed> =
    "sample-$id" to
      PodcastFeed(
        feedUrl = "https://example.invalid/podcasts/$id.xml",
        title = title,
        description = null,
        author = author,
        artworkUrl = null,
        link = null,
        episodes = emptyList(),
      )
}
