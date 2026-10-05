package software.ralf.storymile.screen

import kotlinx.coroutines.flow.StateFlow

/** App-scoped window information that can be injected into presenters and other business logic. */
interface ScreenSizeProvider {
  /**
   * Current dimensions, category, and orientation, updated when the window resizes or rotates.
   * Starts with [ScreenSize.Zero] until the template renderer reports the first measurement.
   */
  val screenSize: StateFlow<ScreenSize>
}
