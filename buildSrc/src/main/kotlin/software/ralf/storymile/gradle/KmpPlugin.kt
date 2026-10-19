package software.ralf.storymile.gradle

import dev.zacsweers.metro.gradle.DiagnosticSeverity
import dev.zacsweers.metro.gradle.MetroPluginExtension
import guru.nidi.graphviz.engine.Format
import io.github.terrakok.KmpHierarchyConfig
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.plugins.ExtensionAware
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.resources.ResourcesExtension
import software.ralf.app.platform.gradle.ModuleStructurePlugin.Companion.namespace
import software.ralf.app.platform.gradle.moduleType
import software.ralf.storymile.gradle.Platform.Companion.platforms

internal class KmpPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.plugins.apply(Plugins.KOTLIN_MULTIPLATFORM)

    target.configureKotlin()
    target.addExtraSourceSets()
    target.configureHierarchyPlugin()
    target.configureDependencies()
    target.configureComposeDependencies()
    target.configureMetro()
    target.configureDetekt()
  }

  private fun Project.configureKotlin() {
    extensions.getByType(BasePluginExtension::class.java).archivesName.set(safePathString)
    kmpExtension.applyDefaultHierarchyTemplate {
      common {
        // Compose shares its system-theme API across the Skiko targets.
        group("skiko") {
          withJvm()
          withIos()
          withWasmJs()
        }
      }
    }
    kmpExtension.compilerOptions {
      extraWarnings.set(false)
      allWarningsAsErrors.set(ci)
      optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
    }

    platforms().forEach { it.configurePlatform() }
  }

  private fun Project.addExtraSourceSets() {
    val sourceSets = kmpExtension.sourceSets
    if (
      sourceSets.findByName("androidMain") == null || sourceSets.findByName("desktopMain") == null
    ) {
      return
    }

    setOf("Main", "Test").forEach { suffix ->
      val noWasmJs = sourceSets.create("noWasmJs$suffix")
      noWasmJs.dependsOn(sourceSets.getByName("common$suffix"))
      sourceSets.named("native$suffix").configure { it.dependsOn(noWasmJs) }

      val appleAndDesktop = sourceSets.create("appleAndDesktop$suffix")
      appleAndDesktop.dependsOn(noWasmJs)
      sourceSets.named("apple$suffix").configure { it.dependsOn(appleAndDesktop) }
      sourceSets.named("desktop$suffix").configure { it.dependsOn(appleAndDesktop) }

      val commonJvm = sourceSets.create("commonJvm$suffix")
      commonJvm.dependsOn(noWasmJs)
      sourceSets.named("desktop$suffix").configure { it.dependsOn(commonJvm) }
      val androidSourceSet = if (suffix == "Main") "androidMain" else "androidHostTest"
      sourceSets.named(androidSourceSet).configure { it.dependsOn(commonJvm) }
    }
  }

  private fun Project.configureHierarchyPlugin() {
    plugins.apply(Plugins.KOTLIN_HIERARCHY)
    (kmpExtension as ExtensionAware).extensions.getByType(KmpHierarchyConfig::class.java).apply {
      formats(Format.PNG, Format.SVG)
      withTestHierarchy = true
    }
  }

  private fun Project.configureDependencies() {
    kmpExtension.sourceSets.getByName("commonMain").dependencies {
      implementation(libs.findLibrary("coroutines-core").get().get().toString())
    }

    val testDependenciesInMain = moduleType.useTestDependenciesInMain
    val testSourceSetName = if (testDependenciesInMain) "commonMain" else "commonTest"
    kmpExtension.sourceSets.getByName(testSourceSetName).dependencies {
      if (testDependenciesInMain) {
        api(kotlin("test"))
        api(libs.findLibrary("assertk").get().get().toString())
        api(libs.findLibrary("coroutines-test").get().get().toString())
      } else {
        implementation(kotlin("test"))
        implementation(libs.findLibrary("assertk").get().get().toString())
        implementation(libs.findLibrary("coroutines-test").get().get().toString())
      }
    }
  }

  private fun Project.configureComposeDependencies() {
    plugins.withId(Plugins.COMPOSE_MULTIPLATFORM) {
      kmpExtension.compilerOptions.optIn.addAll(
        "androidx.compose.animation.ExperimentalSharedTransitionApi",
        "androidx.compose.material3.ExperimentalMaterial3Api",
      )
      if (moduleType.isRobotsModule) {
        kmpExtension.compilerOptions.optIn.add("androidx.compose.ui.test.ExperimentalTestApi")
      }

      kmpExtension.sourceSets.getByName("commonMain").dependencies {
        implementation(libs.findLibrary("compose-material").get().get().toString())
        implementation(libs.findLibrary("compose-material-icons").get().get().toString())
        implementation(libs.findLibrary("compose-resources").get().get().toString())
      }

      val composeExtension = extensions.getByType(ComposeExtension::class.java)
      (composeExtension as ExtensionAware)
        .extensions
        .getByType(ResourcesExtension::class.java)
        .packageOfResClass = "${namespace()}.generated.resources"

      platforms().forEach { it.configureCompose() }
    }
  }

  private fun Project.configureMetro() {
    plugins.withId(Plugins.METRO) {
      extensions
        .getByType(MetroPluginExtension::class.java)
        .unusedGraphInputsSeverity
        .set(DiagnosticSeverity.NONE)
    }
  }
}
