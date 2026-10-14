package software.ralf.storymile.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

@CacheableTask
public abstract class GenerateBuildInfoTask : DefaultTask() {
  @get:Input public abstract val versionName: Property<String>

  @get:Input public abstract val versionCode: Property<Int>

  @get:Input public abstract val gitSha: Property<String>

  @get:OutputDirectory public abstract val outputDirectory: DirectoryProperty

  @TaskAction
  public fun generate() {
    val output =
      outputDirectory.file("software/ralf/storymile/util/BuildInfoValues.kt").get().asFile
    output.parentFile.mkdirs()
    output.writeText(
      """
      package software.ralf.storymile.util

      internal object BuildInfoValues {
        const val VERSION_NAME: String = "${versionName.get().escapeKotlinString()}"
        const val VERSION_CODE: Int = ${versionCode.get()}
        const val GIT_SHA: String = "${gitSha.get().escapeKotlinString()}"
      }
      """
        .trimIndent() + "\n",
    )
  }

  private fun String.escapeKotlinString(): String =
    replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("$", "\\$")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
}
