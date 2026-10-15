package software.ralf.storymile.util

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import software.ralf.storymile.util.impl.BuildInfoValues

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class BuildInfoImpl(
  override val versionName: String = BuildInfoValues.VERSION_NAME,
  override val versionCode: Int = BuildInfoValues.VERSION_CODE,
  override val gitSha: String = BuildInfoValues.GIT_SHA,
) : BuildInfo
