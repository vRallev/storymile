package software.ralf.storymile.screen

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * App-scoped implementation of [ScreenSizeProvider].
 *
 * The template renderer writes platform window measurements while feature presenters observe only
 * the read-only [screenSize] flow.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultScreenSizeProvider : ScreenSizeProvider {
  override val screenSize: StateFlow<ScreenSize>
    field = MutableStateFlow(ScreenSize.Zero)

  /** Publishes a new platform window measurement to shared presentation code. */
  fun update(screenSize: ScreenSize) {
    this.screenSize.value = screenSize
  }
}
