package software.ralf.storymile.podcast

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides

@ContributesTo(AppScope::class)
@BindingContainer
interface FakePodcastCatalogBindings {
  companion object {
    @Provides @Named("fakePodcastFeeds") fun feeds(): Map<String, PodcastFeed> = FakePodcasts.feeds

    @Provides
    @Named("fakePodcastEpisodeIds")
    fun episodeIds(): List<String> = FakePodcasts.queue.map { it.episode.id }

    @Provides
    @Named("fakePodcastAudioUri")
    fun audioUri(): (String) -> String? = { FakePodcasts.audioUri(it) }
  }
}
