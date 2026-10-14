package software.ralf.storymile.gradle

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

public open class AppAndroidPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.plugins.apply(BasePlugin::class.java)
    target.plugins.apply(Plugins.ANDROID_APP)
    target.plugins.apply(BaseAndroidPlugin::class.java)
    target.configureBuildInfo()
    target.configureAndroidKotlin()
    target.configureDetekt()
  }

  private fun Project.configureBuildInfo() {
    val android = extensions.getByType(ApplicationExtension::class.java)
    android.buildFeatures.buildConfig = true
    val androidComponents = extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
    androidComponents.onVariants(androidComponents.selector().withBuildType("release")) { variant ->
      val releaseVersionCode =
        providers
          .environmentVariable("GITHUB_RUN_NUMBER")
          .orElse(providers.gradleProperty("VERSION_CODE"))
          .map { it.toInt() }
      variant.outputs.forEach { it.versionCode.set(releaseVersionCode) }
    }
  }
}
