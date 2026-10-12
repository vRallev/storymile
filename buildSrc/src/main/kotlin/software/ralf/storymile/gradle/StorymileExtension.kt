package software.ralf.storymile.gradle

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

/** Project build options configured through the `storymile` Gradle DSL. */
public open class StorymileExtension(private val project: Project) {
  public fun enableComposeUi(enabled: Boolean) {
    project.appPlatform.enableComposeUi(enabled)
    if (enabled) {
      addPublicModuleDependencies(true)
    }
  }

  public fun enableMetro(enabled: Boolean) {
    project.appPlatform.enableMetro(enabled)
    if (enabled) {
      addPublicModuleDependencies(true)
    }
  }

  public fun enableComposePresenters(enabled: Boolean) {
    project.appPlatform.enableComposePresenters(enabled)
    if (enabled) {
      addPublicModuleDependencies(true)
    }
  }

  public fun enableComposePresenterBackstack(enabled: Boolean) {
    project.appPlatform.enableComposePresenterBackstack(enabled)
    if (enabled) {
      enableComposePresenters(true)
      enableComposeUi(true)
    }
  }

  public fun addPublicModuleDependencies(add: Boolean) {
    project.appPlatform.addPublicModuleDependencies(add)
    if (add) {
      project.tasks.withType(KotlinCompilationTask::class.java).configureEach {
        it.compilerOptions.optIn.add("software.ralf.app.platform.ExperimentalAppPlatform")
      }
    }
  }

  public fun addImplModuleDependencies(add: Boolean) {
    project.appPlatform.addImplModuleDependencies(add)
    if (add) {
      addPublicModuleDependencies(true)
    }
  }

  /** Controls dependency checks while preserving module structure defaults. */
  public fun enableModuleStructureDependencyCheck(enabled: Boolean) {
    project.appPlatform.enableModuleStructure { it.enableDependencyCheck(enabled) }
  }
}
