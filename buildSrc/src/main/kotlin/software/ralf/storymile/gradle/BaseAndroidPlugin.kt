package software.ralf.storymile.gradle

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

internal class BaseAndroidPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.configureAndroid()
  }

  private fun Project.configureAndroid() {
    val android = extensions.getByType(ApplicationExtension::class.java)
    android.compileSdk = androidCompileSdk
    android.defaultConfig {
      applicationId = "software.ralf.storymile"
      minSdk = androidMinSdk
      targetSdk = androidTargetSdk
      versionCode = 1
      versionName = this@configureAndroid.versionName
    }

    android.compileOptions {
      sourceCompatibility = javaVersion
      targetCompatibility = javaVersion
    }
    android.buildTypes.named("release") {
      it.optimization { enable = true }
      it.isDebuggable = false
      // Use local debug signing until a release signing key is configured.
      it.signingConfig = android.signingConfigs.getByName("debug")
    }
    android.testOptions.unitTests {
      isIncludeAndroidResources = false
      isReturnDefaultValues = true
    }
    configureAndroidLint(android.lint)
    configureAndroidPackaging(android.packaging)
    configureAndroidInstrumentedTests(android)

    releaseTask.configure { it.dependsOn("lintDebug") }
  }

  private fun Project.configureAndroidInstrumentedTests(android: ApplicationExtension) {
    android.defaultConfig {
      testInstrumentationRunner = "software.ralf.storymile.TestRunner"
      testInstrumentationRunnerArguments += "clearPackageData" to "true"
    }
    android.testOptions.execution = "ANDROIDX_TEST_ORCHESTRATOR"
    configureAndroidEmulator(android.testOptions)
    dependencies.add(
      "androidTestUtil",
      libs.findLibrary("androidx-test-orchestrator").get().get().toString(),
    )
    dependencies.add(
      "androidTestImplementation",
      libs.findLibrary("androidx-test-junit").get().get().toString(),
    )
    dependencies.add(
      "androidTestImplementation",
      libs.findLibrary("androidx-test-rules").get().get().toString(),
    )
    dependencies.add(
      "androidTestImplementation",
      libs.findLibrary("androidx-test-runner").get().get().toString(),
    )
    dependencies.add(
      "androidTestImplementation",
      libs.findLibrary("compose-ui-test-junit4").get().get().toString(),
    )
    dependencies.add(
      "androidTestImplementation",
      libs.findLibrary("compose-ui-test-junit4-android").get().get().toString(),
    )
    dependencies.add(
      "androidTestImplementation",
      libs.findLibrary("compose-ui-test-manifest").get().get().toString(),
    )

    releaseTask.configure { it.dependsOn("assembleDebugAndroidTest") }
  }
}
