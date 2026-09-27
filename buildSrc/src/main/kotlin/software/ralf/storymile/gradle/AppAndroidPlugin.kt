package software.ralf.storymile.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

public open class AppAndroidPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.plugins.apply(BasePlugin::class.java)
    target.plugins.apply(Plugins.ANDROID_APP)
    target.plugins.apply(BaseAndroidPlugin::class.java)
    target.configureAndroidKotlin()
    target.configureDetekt()
  }
}
