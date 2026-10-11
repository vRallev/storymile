package software.ralf.storymile

import dev.zacsweers.metro.createGraphFactory

/** Android test application that installs the graph containing integration-test robots. */
class TestAndroidApplication : AndroidApplication() {
  override fun metroGraph(storymileApplication: KmpApplication): AppGraph {
    return createGraphFactory<TestAndroidAppGraph.Factory>().create(this, storymileApplication)
  }
}
