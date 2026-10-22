package software.ralf.storymile

import assertk.assertThat
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import dev.zacsweers.metro.createGraphFactory
import kotlin.test.Test

@OptIn(DelicateCoilApi::class)
class CoilStorageTest {
  @Test
  fun `application startup configures a memory-only singleton with in-memory storage`() {
    SingletonImageLoader.reset()
    val application = KmpApplication()
    application.create(createGraphFactory<TestDesktopAppGraph.Factory>().create(application))
    try {
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
