package software.ralf.storymile

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import dev.zacsweers.metro.createGraphFactory
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import software.ralf.storymile.podcast.FakePodcasts

@OptIn(DelicateCoilApi::class)
class CoilStorageTest {
  @Test
  fun `fake shows and queued episodes have distinct loadable artwork`() = runTest {
    val artworkUrls =
      FakePodcasts.feeds.values.map { checkNotNull(it.artworkUrl) } +
        FakePodcasts.queue.map { checkNotNull(it.artworkUrl) }
    assertThat(artworkUrls.toSet()).hasSize(artworkUrls.size)

    val context = PlatformContext.INSTANCE
    val loader = ImageLoader.Builder(context).build()
    try {
      for (url in artworkUrls) {
        assertThat(url.startsWith("file:") || url.startsWith("jar:file:"), name = url).isTrue()
        val request = ImageRequest.Builder(context).data(url).size(128).build()
        assertThat(loader.execute(request), name = url).isInstanceOf<SuccessResult>()
      }
    } finally {
      loader.shutdown()
    }
  }

  @Test
  fun `application startup configures a memory-only singleton with in-memory storage`() = runTest {
    SingletonImageLoader.reset()
    val application = KmpApplication {
      createGraphFactory<TestDesktopAppGraph.Factory>().create(it)
    }
    try {
      application.awaitReady()
      val loader = SingletonImageLoader.get(PlatformContext.INSTANCE)
      try {
        assertThat(loader.diskCache).isNull()
        assertThat(loader.memoryCache).isNotNull()
      } finally {
        loader.shutdown()
      }
    } finally {
      SingletonImageLoader.reset()
      application.destroy()
    }
  }
}
