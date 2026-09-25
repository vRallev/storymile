package software.ralf.storymile.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test

internal class BasePlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.version = target.versionName

    target.tasks.register("release")
    target.extensions.create("storymile", StorymileExtension::class.java, target)
    target.configureAppPlatform()
    target.configureTestLogging()
    target.sortDependencies()
  }

  private fun Project.configureAppPlatform() {
    plugins.apply(Plugins.APP_PLATFORM)

    if (path == ":") {
      appPlatform.enableModuleStructureNestingCheck(true)
      releaseTask.configure { it.dependsOn("checkModuleStructureNesting") }
    } else {
      appPlatform.enableModuleStructure(true)
      releaseTask.configure { it.dependsOn("checkModuleStructureDependencies") }
    }
  }

  private fun Project.configureTestLogging() {
    tasks.withType(Test::class.java).configureEach {
      it.systemProperty("java.awt.headless", "true")

      if (ci) {
        it.testLogging { logging ->
          logging.showExceptions = true
          logging.showCauses = true
          logging.showStackTraces = true
          logging.showStandardStreams = true
        }
      }
    }
  }

  private fun Project.sortDependencies() {
    plugins.apply(Plugins.SORT_DEPENDENCIES)
    releaseTask.configure { it.dependsOn("checkSortDependencies") }
  }
}
