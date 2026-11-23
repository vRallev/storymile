package software.ralf.storymile.podcast

/** The bundled catalog and audio used by Fake mode tests. */
object BundledPodcastFixtures {
  val feeds: Map<String, PodcastFeed>
    get() = FakePodcasts.feeds

  val queue: List<QueuedEpisode>
    get() = FakePodcasts.queue

  fun audioUri(enclosureUrl: String): String? = FakePodcasts.audioUri(enclosureUrl)
}
