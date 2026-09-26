package software.ralf.storymile.util

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@ContributesTo(AppScope::class)
@BindingContainer
object PlatformBindings {
  @Provides @SingleIn(AppScope::class) fun providePlatform(): Platform = Platform.Wasm
}
