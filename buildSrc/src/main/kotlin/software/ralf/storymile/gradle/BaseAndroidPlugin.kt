package software.ralf.storymile.gradle

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider

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
      it.signingConfig = android.signingConfigs.getByName("debug")
    }
    configureReleaseSigning(android)
    android.testOptions.unitTests {
      isIncludeAndroidResources = false
      isReturnDefaultValues = true
    }
    configureAndroidLint(android.lint)
    configureAndroidPackaging(android.packaging)
    configureAndroidInstrumentedTests(android)

    releaseTask.configure { it.dependsOn("lintDebug") }
  }

  private fun Project.configureReleaseSigning(android: ApplicationExtension) {
    val storeFile = signingProperty("storeFile", "STORYMILE_ANDROID_KEYSTORE_PATH")
    val storePassword = signingProperty("storePassword", "STORYMILE_ANDROID_KEYSTORE_PASSWORD")
    val keyAlias = signingProperty("keyAlias", "STORYMILE_ANDROID_KEY_ALIAS")
    val keyPassword = signingProperty("keyPassword", "STORYMILE_ANDROID_KEY_PASSWORD")
    val required =
      signingProperty("required", "STORYMILE_ANDROID_SIGNING_REQUIRED")
        .map { it.toBooleanStrict() }
        .getOrElse(false)

    if (
      !required && listOf(storeFile, storePassword, keyAlias, keyPassword).none { it.isPresent }
    ) {
      return
    }

    check(storeFile.isPresent && storePassword.isPresent && keyAlias.isPresent) {
      "Release signing requires storymile.android.signing.storeFile, " +
        "storymile.android.signing.storePassword, and storymile.android.signing.keyAlias " +
        "or their STORYMILE_ANDROID_KEYSTORE_PATH, STORYMILE_ANDROID_KEYSTORE_PASSWORD, " +
        "and STORYMILE_ANDROID_KEY_ALIAS environment variables."
    }

    val signing =
      android.signingConfigs.create("release") {
        it.storeFile = file(storeFile.get())
        it.storePassword = storePassword.get()
        it.keyAlias = keyAlias.get()
        it.keyPassword = keyPassword.orElse(storePassword).get()
      }
    android.buildTypes.getByName("release").signingConfig = signing
  }

  private fun Project.signingProperty(name: String, environmentVariable: String): Provider<String> =
    providers
      .environmentVariable(environmentVariable)
      .filter { it.isNotBlank() }
      .orElse(
        providers.gradleProperty("storymile.android.signing.$name").filter { it.isNotBlank() },
      )

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
