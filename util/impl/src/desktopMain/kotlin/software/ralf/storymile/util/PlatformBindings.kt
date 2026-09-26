package software.ralf.storymile.util

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@ContributesTo(AppScope::class)
@BindingContainer
object PlatformBindings {
  @Provides
  @SingleIn(AppScope::class)
  fun providePlatform(): Platform {
    val osName = System.getProperty("os.name")
    return when {
      osName.startsWith("Mac", ignoreCase = true) || osName.equals("Darwin", ignoreCase = true) ->
        Platform.Desktop.Mac

      osName.startsWith("Windows", ignoreCase = true) -> Platform.Desktop.Windows
      osName.startsWith("Linux", ignoreCase = true) -> Platform.Desktop.Linux
      else -> error("Unsupported desktop operating system: ${osName}")
    }
  }
}
