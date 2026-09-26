package software.ralf.storymile.gradle

import org.gradle.api.Project

/** Project build options configured through the `storymile` Gradle DSL. */
public open class StorymileExtension(private val project: Project) {
  public fun enableComposeUi(enabled: Boolean) {
    project.appPlatform.enableComposeUi(enabled)
  }

  public fun enableMetro(enabled: Boolean) {
    project.appPlatform.enableMetro(enabled)
  }

  public fun enableComposePresenters(enabled: Boolean) {
    project.appPlatform.enableComposePresenters(enabled)
  }

  public fun enableComposePresenterBackstack(enabled: Boolean) {
    project.appPlatform.enableComposePresenterBackstack(enabled)
  }

  public fun addPublicModuleDependencies(add: Boolean) {
    project.appPlatform.addPublicModuleDependencies(add)
  }

  public fun addImplModuleDependencies(add: Boolean) {
    project.appPlatform.addImplModuleDependencies(add)
  }

  /** Controls dependency checks while preserving module structure defaults. */
  public fun enableModuleStructureDependencyCheck(enabled: Boolean) {
    project.appPlatform.enableModuleStructure { it.enableDependencyCheck(enabled) }
  }
}
