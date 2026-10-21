package software.ralf.storymile.restart.testing

import software.ralf.storymile.restart.ApplicationRestarter

/** Calls [onRestart] and counts successful restart requests without restarting the app. */
class FakeApplicationRestarter(private val onRestart: () -> Unit = {}) : ApplicationRestarter {
  var restartCount = 0
    private set

  override fun restart() {
    onRestart()
    restartCount++
  }
}
