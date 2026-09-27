package software.ralf.storymile.gradle

import com.android.build.api.dsl.TestExtension
import com.android.build.api.variant.TestAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

public open class AndroidBlackboxTestPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.plugins.apply(BasePlugin::class.java)
    target.plugins.apply(Plugins.ANDROID_TEST)
    target.configureAndroid()
    target.configureAndroidKotlin()
    target.configureDetekt()
  }

  private fun Project.configureAndroid() {
    val android = extensions.getByType(TestExtension::class.java)
    android.namespace = "software.ralf.storymile.blackbox"
    android.compileSdk = androidCompileSdk
    android.targetProjectPath = ":app:android"
    // Instrument the test APK itself so the production app runs in its own process.
    android.experimentalProperties["android.experimental.self-instrumenting"] = true
    android.defaultConfig {
      minSdk = androidMinSdk
      targetSdk = androidTargetSdk
      testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    android.buildTypes.create("release") {
      it.isDebuggable = true
      it.signingConfig = android.signingConfigs.getByName("debug")
    }
    extensions.getByType(TestAndroidComponentsExtension::class.java).beforeVariants {
      it.enable = it.buildType == "release"
    }
    android.compileOptions {
      sourceCompatibility = javaVersion
      targetCompatibility = javaVersion
    }
    configureAndroidPackaging(android.packaging)
    // ATD images disable rendering, which produces blank failure screenshots.
    configureAndroidEmulator(android.testOptions, systemImageSource = "google")
    releaseTask.configure { it.dependsOn("assembleRelease") }
  }
}
