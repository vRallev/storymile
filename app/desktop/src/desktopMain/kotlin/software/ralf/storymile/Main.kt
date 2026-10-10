package software.ralf.storymile

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.zacsweers.metro.createGraphFactory
import java.awt.Taskbar
import javax.imageio.ImageIO
import software.ralf.app.platform.scope.di.metro.metroDependencyGraph
import software.ralf.storymile.theme.LocalDarkThemeOverride
import software.ralf.storymile.util.Platform

/**
 * Launches the Desktop application. Command/Ctrl+S toggles phone and tablet sizes; Command/Ctrl+D
 * toggles light and dark themes.
 */
fun main(args: Array<String>) {

  val initialSize =
    when (val size = args.firstOrNull { it.startsWith("--window-size=") }?.substringAfter('=')) {
      null,
      "phone" -> DesktopWindowSizes.phone
      "tablet" -> DesktopWindowSizes.tablet
      else -> error("Unknown window size '$size'. Use phone or tablet.")
    }

  val desktopApp = DesktopApp { createGraphFactory<DesktopAppGraph.Factory>().create(it) }
  val platform = desktopApp.rootScope.metroDependencyGraph<AppGraph>().platform
  val windowIcon = configureAppIcon(platform)

  application {
    val phoneSize = DesktopWindowSizes.phone
    val tabletSize = DesktopWindowSizes.tablet
    val windowState = rememberWindowState(width = initialSize.width, height = initialSize.height)
    var darkThemeOverride by remember { mutableStateOf<Boolean?>(null) }
    var systemDarkTheme by remember { mutableStateOf(false) }

    Window(
      onCloseRequest = {
        desktopApp.destroy()
        exitApplication()
      },
      onPreviewKeyEvent = { keyEvent ->
        if (
          keyEvent.type == KeyEventType.KeyDown &&
            (keyEvent.isMetaPressed || keyEvent.isCtrlPressed)
        ) {
          when (keyEvent.key) {
            Key.S -> {
              windowState.size =
                if (DesktopWindowSizes.isTablet(windowState.size)) phoneSize else tabletSize
              true
            }
            Key.D -> {
              darkThemeOverride = !(darkThemeOverride ?: systemDarkTheme)
              true
            }
            else -> false
          }
        } else {
          false
        }
      },
      state = windowState,
      icon = windowIcon,
      title = "Storymile",
      alwaysOnTop = true,
    ) {
      // Desktop supplies live system appearance updates inside the window's composition.
      val currentSystemDarkTheme = isSystemInDarkTheme()
      SideEffect { systemDarkTheme = currentSystemDarkTheme }
      CompositionLocalProvider(LocalDarkThemeOverride provides darkThemeOverride) {
        desktopApp.renderTemplates()
      }
    }
  }
}

private fun configureAppIcon(platform: Platform): BitmapPainter {
  val iconResource = if (platform == Platform.Desktop.Mac) "icon-macos.png" else "icon.png"
  val iconImage =
    ImageIO.read(checkNotNull(Thread.currentThread().contextClassLoader.getResource(iconResource)))
  if (Taskbar.isTaskbarSupported()) {
    val taskbar = Taskbar.getTaskbar()
    if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
      taskbar.iconImage = iconImage
    }
  }
  return BitmapPainter(iconImage.toComposeImageBitmap())
}
