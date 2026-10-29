package software.ralf.storymile.gradle

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.Sync
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithSimulatorTests
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest
import software.ralf.app.platform.gradle.AppPlatformPlugin

internal sealed interface Platform {
  fun configurePlatform()

  fun configureCompose() = Unit

  private class Android(private val project: Project) : Platform {
    override fun configurePlatform() {
      project.plugins.apply(Plugins.ANDROID_LINT)
      project.plugins.apply(Plugins.ANDROID_MULTIPLATFORM)

      with(project.androidKmpTarget) {
        compileSdk = project.androidCompileSdk
        minSdk = project.androidMinSdk
        compilerOptions { jvmTarget.set(project.jvmTarget) }
        withHostTest { isReturnDefaultValues = true }
        lint {
          configureAndroidLint(this)
          targetSdk = project.androidTargetSdk
        }
        configureAndroidPackaging(packaging)
      }

      project.configureAndroidLifecycle(configurationName = "androidMainImplementation")
      project.releaseTask.configure { it.dependsOn("lintAndroidMain") }
    }

    override fun configureCompose() {
      project.androidKmpTarget.androidResources.enable = true
    }
  }

  private class Desktop(private val project: Project) : Platform {
    override fun configurePlatform() {
      project.kmpExtension.jvm("desktop").compilerOptions { jvmTarget.set(project.jvmTarget) }

      with(project.extensions.getByType(JavaPluginExtension::class.java)) {
        sourceCompatibility = project.javaVersion
        targetCompatibility = project.javaVersion
      }
    }

    override fun configureCompose() {
      val desktopArtifact =
        project.libs.findLibrary("compose-desktop-${currentOsTarget()}").get().get().toString()

      project.kmpExtension.sourceSets.getByName("desktopMain").dependencies {
        implementation(project.libs.findLibrary("coroutines-swing").get().get().toString())
        implementation(desktopArtifact)
      }
      project.kmpExtension.sourceSets.getByName("desktopTest").apply {
        languageSettings.optIn("androidx.compose.ui.test.ExperimentalTestApi")
        dependencies {
          implementation(
            project.libs.findLibrary("compose-multiplatform-ui-test-junit4").get().get().toString(),
          )
          implementation(desktopArtifact)
        }
      }
    }

    private fun currentOsTarget(): String {
      val os = System.getProperty("os.name").lowercase()
      val arch = System.getProperty("os.arch").lowercase()
      return when {
        os.contains("mac") || os.contains("darwin") ->
          if (arch.contains("aarch64") || arch.contains("arm")) "macos-arm64" else "macos-x64"
        os.contains("win") ->
          if (arch.contains("aarch64") || arch.contains("arm")) {
            "windows-arm64"
          } else {
            "windows-x64"
          }
        else -> if (arch.contains("aarch64") || arch.contains("arm")) "linux-arm64" else "linux-x64"
      }
    }
  }

  private abstract class Ios : Platform {
    protected abstract val project: Project
    protected abstract val target: KotlinNativeTarget

    override fun configurePlatform() {
      val configuredTarget = target
      if (project.path != ":app-framework:impl") return

      configuredTarget.binaries.framework {
        baseName = "StorymileApp"
        isStatic = true
        binaryOption("bundleId", "software.ralf.storymile")

        val commonMain = project.kmpExtension.sourceSets.getByName("commonMain")
        AppPlatformPlugin.exportedDependencies().forEach { dependency ->
          export(dependency)
          commonMain.dependencies { api(dependency) }
        }
      }
    }
  }

  private class IosSimulatorArm64(override val project: Project) : Ios() {
    override val target: KotlinNativeTargetWithSimulatorTests by lazy {
      project.kmpExtension.iosSimulatorArm64()
    }

    override fun configureCompose() {
      // Compose copies test resources under the native link output directory by default.
      // These overlapping outputs disable Gradle caching for the link task.
      // Give resources their own output directory so Gradle can cache the executable.
      val testRun = target.testRuns.getByName("test")
      val binary = testRun.executionSource.binary
      val linkTask = binary.linkTaskProvider
      val resources =
        project.tasks.named("copyTestComposeResourcesForIosSimulatorArm64", Copy::class.java)
      val resourceDirectory =
        project.layout.buildDirectory.dir("compose/iosSimulatorArm64/testResources")
      resources.configure { it.into(resourceDirectory) }

      // For native tests, Compose resolves resources beside the executable through NSBundle.
      // Stage the linked binary and resources together, then run that copy of the executable.
      // Sync owns this runtime directory; neither source task writes into it.
      val runtimeDirectory =
        project.layout.buildDirectory.dir("ios-test-runtime/iosSimulatorArm64/${binary.name}")
      val runtime =
        project.tasks.register("prepareIosSimulatorArm64TestRuntime", Sync::class.java) {
          it.into(runtimeDirectory)
          // The destinationDirectory provider does not carry the link task dependency.
          it.dependsOn(linkTask)
          it.from(linkTask.flatMap { link -> link.destinationDirectory }) { spec ->
            // Ignore resources left in the link output by builds that used the old layout.
            spec.exclude("compose-resources/**")
          }
          it.from(resources) { spec -> spec.into("compose-resources") }
        }
      val executableName = linkTask.flatMap { it.outputFile }.map { it.name }
      project.tasks
        .named("iosSimulatorArm64Test", KotlinNativeSimulatorTest::class.java)
        .configure { test ->
          test.executable(
            runtime.zip(executableName) { task, name -> task.destinationDir.resolve(name) },
          )
          // Track resources as well as the executable so resource changes invalidate test results.
          test.inputs
            .files(runtime)
            .withPropertyName("testRuntime")
            .withPathSensitivity(PathSensitivity.RELATIVE)
        }
    }
  }

  private class IosArm64(override val project: Project) : Ios() {
    override val target: KotlinNativeTarget by lazy { project.kmpExtension.iosArm64() }
  }

  private class Wasm(private val project: Project) : Platform {
    @OptIn(ExperimentalWasmDsl::class)
    override fun configurePlatform() {
      project.kmpExtension.wasmJs {
        compilerOptions { optIn.add("kotlin.js.ExperimentalWasmJsInterop") }
        browser {
          outputModuleName.set(project.safePathString)

          if (project.path == ":app:web") {
            commonWebpackConfig {
              it.outputFileName = "storymile.js"
              it.devServer = it.devServer ?: KotlinWebpackConfig.DevServer()
            }
          }
        }
      }

      project.plugins.withId(Plugins.COMPOSE_MULTIPLATFORM) {
        project.kmpExtension.wasmJs { binaries.executable() }
      }
    }
  }

  companion object {
    fun Project.platforms(): Set<Platform> = buildSet {
      when (path) {
        ":app:android" -> add(Android(project = this@platforms))
        ":app:desktop" -> add(Desktop(project = this@platforms))
        ":app:web" -> add(Wasm(project = this@platforms))
        else -> {
          add(Android(project = this@platforms))
          add(Desktop(project = this@platforms))
          add(IosSimulatorArm64(project = this@platforms))
          add(IosArm64(project = this@platforms))
          if (enableWasm) {
            add(Wasm(project = this@platforms))
          }
        }
      }
    }
  }
}
