package software.ralf.storymile.gradle

import com.android.build.api.dsl.Lint
import com.android.build.api.dsl.Packaging
import com.android.build.api.dsl.TestOptions
import kotlin.collections.plusAssign
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

internal fun configureAndroidLint(lint: Lint) {
  lint.warningsAsErrors = true
  lint.disable += setOf("NewerVersionAvailable")
}

internal fun configureAndroidPackaging(packaging: Packaging) {
  packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
}

internal fun configureAndroidEmulator(
  testOptions: TestOptions,
  systemImageSource: String = "aosp-atd",
) {
  @Suppress("UnstableApiUsage")
  testOptions.managedDevices.localDevices.create("emulator") {
    it.device = "Pixel 3"
    it.apiLevel = 30
    it.require64Bit = true
    it.systemImageSource = systemImageSource
  }
}

internal fun Project.configureAndroidKotlin() {
  extensions.getByType(KotlinAndroidProjectExtension::class.java).compilerOptions {
    extraWarnings.set(false)
    allWarningsAsErrors.set(ci)
    jvmTarget.set(this@configureAndroidKotlin.jvmTarget)
  }
}
