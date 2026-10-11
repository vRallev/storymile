package software.ralf.storymile

import dev.zacsweers.metro.createGraphFactory
import software.ralf.storymile.application.Application

/** Android test application that installs the graph containing integration-test robots. */
class TestAndroidApplication : AndroidApplication() {
  override fun metroGraph(storymileApplication: Application): AppGraph {
    return createGraphFactory<TestAndroidAppGraph.Factory>().create(this, storymileApplication)
  }
}
