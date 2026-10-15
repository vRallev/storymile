package software.ralf.storymile.util

import android.icu.util.ULocale
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import java.util.Locale

@ContributesTo(AppScope::class)
@BindingContainer
object PlatformBindings {
  @Provides @SingleIn(AppScope::class) fun providePlatform(): Platform = Platform.Android

  @Provides
  @SingleIn(AppScope::class)
  fun provideDevice(): Device = LocaleDevice {
    ULocale.addLikelySubtags(ULocale.forLocale(Locale.getDefault())).country
  }
}
