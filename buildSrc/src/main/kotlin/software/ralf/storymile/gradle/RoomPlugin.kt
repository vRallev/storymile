package software.ralf.storymile.gradle

import androidx.room3.gradle.RoomExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

internal class RoomPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.plugins.apply(Plugins.KSP)
    target.plugins.apply(Plugins.ROOM)
    target.kmpExtension.compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")
    target.extensions
      .getByType(RoomExtension::class.java)
      .schemaDirectory(
        target.layout.projectDirectory.dir("schemas").asFile.path,
      )
    // Android host tests need the JVM SQLite binary instead of the Android AAR's JNI library.
    val sqliteVersion = target.libs.findVersion("androidx-sqlite").get().requiredVersion
    target.configurations.configureEach { configuration ->
      if (configuration.name.contains("HostTest") && configuration.name.endsWith("Classpath")) {
        configuration.resolutionStrategy.dependencySubstitution { substitutions ->
          substitutions
            .substitute(substitutions.module("androidx.sqlite:sqlite-bundled"))
            .using(substitutions.module("androidx.sqlite:sqlite-bundled-jvm:$sqliteVersion"))
        }
      }
    }
    target.kmpExtension.targets.configureEach { platform ->
      val name = platform.name.replaceFirstChar { it.uppercase() }
      target.configurations.configureEach { configuration ->
        if (
          configuration.name == "ksp$name" ||
            configuration.name == "ksp${name}Test" ||
            configuration.name == "ksp${name}HostTest"
        ) {
          target.dependencies.add(
            configuration.name,
            target.libs.findLibrary("androidx-room-compiler").get().get().toString(),
          )
        }
      }
    }
  }

  companion object {
    fun configureBrowserDatabaseDriver(project: Project) {
      val sourceSet = project.kmpExtension.sourceSets.findByName("wasmJsMain") ?: return
      val workerDirectory = project.layout.projectDirectory.dir("sqlite-worker")
      sourceSet.dependencies {
        implementation(project.libs.findLibrary("androidx-sqlite-web").get().get().toString())
        implementation(project.libs.findLibrary("kotlinx-browser").get().get().toString())
        implementation(npm("@storymile/sqlite-worker", workerDirectory.asFile))
      }
      val sourceWorker = workerDirectory.file("worker.js").asFile
      val installedWorker =
        project.isolated.rootProject.projectDirectory
          .file("build/wasm/node_modules/@storymile/sqlite-worker/worker.js")
          .asFile
      // Wasm requires --no-isolated-projects; its npm installer belongs to the root project.
      @Suppress("GradleProjectIsolation") val rootProject = project.rootProject
      rootProject.tasks.named("kotlinWasmNpmInstall").configure { install ->
        install.inputs.dir(workerDirectory)
        // Yarn can retain a local package's old files when its version has not changed.
        install.doLast { sourceWorker.copyTo(installedWorker, overwrite = true) }
      }
    }
  }
}
