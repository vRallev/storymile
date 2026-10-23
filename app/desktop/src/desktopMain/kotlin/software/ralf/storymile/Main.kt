package software.ralf.storymile

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
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
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import software.ralf.storymile.restart.DesktopProcessRestarter
import software.ralf.storymile.theme.LocalThemeController

/**
 * Launches the Desktop application. Command/Ctrl+S cycles phone, tablet, and desktop sizes;
 * Command/Ctrl+D toggles light and dark themes; Command/Ctrl+F toggles fake mode and restarts the
 * app.
 */
fun main(args: Array<String>) {
  DesktopProcessRestarter.configure("software.ralf.storymile.MainKt", args)

  val initialSize =
    when (val size = args.firstOrNull { it.startsWith("--window-size=") }?.substringAfter('=')) {
      null,
      "phone" -> DesktopWindowSizes.phone
      "tablet" -> DesktopWindowSizes.tablet
      "desktop" -> DesktopWindowSizes.desktop
      else -> error("Unknown window size '$size'. Use phone, tablet, or desktop.")
    }

  val desktopApp = DesktopApp { createGraphFactory<DesktopAppGraph.Factory>().create(it) }

  application {
    var windowIcon by remember { mutableStateOf<BitmapPainter?>(null) }
    LaunchedEffect(Unit) {
      val image = withContext(Dispatchers.IO) { loadAppIcon() }
      if (Taskbar.isTaskbarSupported()) {
        val taskbar = Taskbar.getTaskbar()
        if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
          taskbar.iconImage = image
        }
      }
      windowIcon = BitmapPainter(image.toComposeImageBitmap())
    }
    var windowSize by remember { mutableStateOf(initialSize) }
    val windowState = rememberWindowState(width = initialSize.width, height = initialSize.height)
    val themeController = LocalThemeController.current
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
              // Track the preset even if the OS limits the actual window size.
              windowSize = DesktopWindowSizes.next(windowSize)
              windowState.size = windowSize
              true
            }
            Key.D -> {
              themeController.toggle(systemDarkTheme)
              true
            }
            Key.F -> {
              desktopApp.toggleRuntimeMode()
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
      desktopApp.renderTemplates()
    }
  }
}

private fun loadAppIcon(): BufferedImage {
  val osName = System.getProperty("os.name")
  val isMac =
    osName.startsWith("Mac", ignoreCase = true) || osName.equals("Darwin", ignoreCase = true)
  val iconResource = if (isMac) "icon-macos.png" else "icon.png"
  return ImageIO.read(
    checkNotNull(Thread.currentThread().contextClassLoader.getResource(iconResource)),
  )
}
