package software.ralf.storymile.gradle

import com.android.build.api.dsl.Lint
import com.android.build.api.dsl.Packaging
import kotlin.collections.plusAssign

internal fun configureAndroidLint(lint: Lint) {
  lint.warningsAsErrors = true
  lint.disable +=
    setOf(
      "GradleDependency",
      "ObsoleteLintCustomCheck",
      "NewerVersionAvailable",
      "AndroidGradlePluginVersion",
      "OldTargetApi",
    )
}

internal fun configureAndroidPackaging(packaging: Packaging) {
  packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
}
