package software.ralf.storymile.login

import android.app.Activity
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@SingleIn(AppScope::class)
@Inject
class AndroidLoginHost {
  var activity: Activity? = null
    private set

  fun attach(activity: Activity) {
    this.activity = activity
  }

  fun detach(activity: Activity) {
    if (this.activity === activity) {
      this.activity = null
    }
  }

  @ContributesTo(AppScope::class)
  interface Graph {
    val loginHost: AndroidLoginHost
  }
}
