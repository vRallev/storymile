package software.ralf.storymile

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.zacsweers.metro.createGraphFactory
import java.awt.Taskbar
import javax.imageio.ImageIO
import software.ralf.app.platform.scope.di.metro.metroDependencyGraph
import software.ralf.storymile.restart.DesktopProcessRestarter
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.theme.LocalDarkThemeOverride
import software.ralf.storymile.util.Platform

/**
 * Launches the Desktop application. Command/Ctrl+S cycles phone, tablet, and desktop sizes;
 * Command/Ctrl+D toggles light and dark themes; Command/Ctrl+F toggles fake mode.
 */
fun main(args: Array<String>) {
  // A restart must use the saved selection instead of replaying the initial override.
  DesktopProcessRestarter.configure(
    "software.ralf.storymile.MainKt",
    args.filterNot { it.startsWith("--runtime-mode=") }.toTypedArray(),
  )

  val initialSize = args.windowSize()
  val initialMode = args.runtimeMode()

  val desktopApp = DesktopApp { rootScopeProvider ->
    val graph: AppGraph = createGraphFactory<DesktopAppGraph.Factory>().create(rootScopeProvider)
    initialMode?.let { graph.runtimeModeController.setInitialMode(it) }
    graph
  }
  val graph = desktopApp.rootScope.metroDependencyGraph<AppGraph>()
  val runtimeModeController = graph.runtimeModeController
  val platform = graph.platform
  val windowIcon = configureAppIcon(platform)

  application {
    var windowSize by remember { mutableStateOf(initialSize) }
    val runtimeMode by runtimeModeController.mode.collectAsState()
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
              // Track the preset even if the OS limits the actual window size.
              windowSize = DesktopWindowSizes.next(windowSize)
              windowState.size = windowSize
              true
            }
            Key.D -> {
              darkThemeOverride = !(darkThemeOverride ?: systemDarkTheme)
              true
            }
            Key.F -> {
              runtimeModeController.switchMode(
                if (runtimeModeController.mode.value == RuntimeMode.Fake) {
                  RuntimeMode.Real
                } else {
                  RuntimeMode.Fake
                },
              )
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
      title = if (runtimeMode == RuntimeMode.Fake) "Storymile (Fake)" else "Storymile",
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

private fun Array<String>.windowSize(): DpSize =
  when (val size = firstOrNull { it.startsWith("--window-size=") }?.substringAfter('=')) {
    null,
    "phone" -> DesktopWindowSizes.phone
    "tablet" -> DesktopWindowSizes.tablet
    "desktop" -> DesktopWindowSizes.desktop
    else -> error("Unknown window size '$size'. Use phone, tablet, or desktop.")
  }

private fun Array<String>.runtimeMode(): RuntimeMode? =
  when (val mode = firstOrNull { it.startsWith("--runtime-mode=") }?.substringAfter('=')) {
    null -> null
    "real" -> RuntimeMode.Real
    "fake" -> RuntimeMode.Fake
    else -> error("Unknown runtime mode '$mode'. Use real or fake.")
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
