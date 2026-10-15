package software.ralf.storymile.gradle

import com.github.gmazzo.buildconfig.BuildConfigExtension
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import software.ralf.app.platform.gradle.ModuleStructurePlugin.Companion.namespace

/** Project build options configured through the `storymile` Gradle DSL. */
public open class StorymileExtension(private val project: Project) {
  public fun enableBuildInfo(enabled: Boolean) {
    if (!enabled) {
      return
    }

    project.plugins.apply(Plugins.BUILD_CONFIG)
    project.extensions.getByType(BuildConfigExtension::class.java).apply {
      packageName(project.namespace())
      className("BuildInfoValues")
      useKotlinOutput()
      buildConfigField(String::class.java, "VERSION_NAME", project.versionName)
      buildConfigField(Int::class.java, "VERSION_CODE", project.versionCode)
      buildConfigField(
        String::class.java,
        "GIT_SHA",
        if (
          project.providers
            .gradleProperty("storymile.useRealGitSha")
            .map { it.toBoolean() }
            .getOrElse(false)
        ) {
          project.providers
            .exec { spec ->
              spec.workingDir(project.isolated.rootProject.projectDirectory.asFile)
              spec.commandLine("git", "rev-parse", "HEAD")
            }
            .standardOutput
            .asText
            .map { sha -> sha.trim() }
        } else {
          project.providers.provider { "0000000000000000000000000000000000000000" }
        },
      )
    }
  }

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
