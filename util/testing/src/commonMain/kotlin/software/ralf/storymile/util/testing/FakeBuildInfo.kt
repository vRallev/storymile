package software.ralf.storymile.util.testing

import software.ralf.storymile.util.BuildInfo

data class FakeBuildInfo(
  override val versionName: String = "1.0.0",
  override val versionCode: Int = 1,
  override val gitSha: String = "0000000000000000000000000000000000000000",
) : BuildInfo
