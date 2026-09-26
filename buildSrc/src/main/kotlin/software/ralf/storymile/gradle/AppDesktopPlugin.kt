package software.ralf.storymile.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.desktop.DesktopExtension
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

public open class AppDesktopPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.plugins.apply(BasePlugin::class.java)
    target.plugins.apply(KmpPlugin::class.java)
    target.plugins.apply(Plugins.COMPOSE_HOT_RELOAD)

    target.plugins.withId(Plugins.COMPOSE_MULTIPLATFORM) { target.configureDesktopApplication() }
  }

  private fun Project.configureDesktopApplication() {
    val composeExtension = extensions.getByType(ComposeExtension::class.java)
    composeExtension.extensions.getByType(DesktopExtension::class.java).application.apply {
      mainClass = "software.ralf.storymile.MainKt"

      nativeDistributions.apply {
        targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
        packageName = "Storymile"
        packageVersion = versionName

        macOS { it.iconFile.set(file("icons/icon.icns")) }
        windows { it.iconFile.set(file("icons/icon.ico")) }
        linux { it.iconFile.set(file("src/desktopMain/resources/icon.png")) }
      }
    }
  }
}
