package software.ralf.storymile.scope.runtime

import java.io.File

internal class DesktopRestartCommand(
  private val javaExecutable: String,
  private val jvmArguments: List<String>,
  private val classPath: String,
  private val mainClassName: String,
  private val arguments: List<String>,
) {
  fun command(): List<String> = buildList {
    add(javaExecutable)
    addAll(
      jvmArguments.filterNot {
        it.startsWith("-javaagent:") ||
          it.startsWith("-agentlib:") ||
          it.startsWith("-agentpath:") ||
          it.startsWith("-Xrunjdwp:") ||
          it.startsWith("-Dcompose.reload.") ||
          it.startsWith("-D$ARGUMENTS_FILE_PROPERTY=")
      },
    )
    add("-cp")
    add(classPath)
    add(mainClassName)
    addAll(arguments)
  }

  companion object {
    private const val ARGUMENTS_FILE_PROPERTY = "storymile.restartArgumentsFile"

    fun withArgumentsFile(command: List<String>, file: File): List<String> {
      val arguments = listOf("-D$ARGUMENTS_FILE_PROPERTY=${file.absolutePath}") + command.drop(1)
      file.writeText(arguments.joinToString("\n") { it.quoteArgument() })
      return listOf(command.first(), "@${file.absolutePath}")
    }

    fun deleteArgumentsFile() {
      System.clearProperty(ARGUMENTS_FILE_PROPERTY)?.let { File(it).delete() }
    }

    private fun String.quoteArgument(): String =
      "\"" +
        replace("\\", "\\\\")
          .replace("\"", "\\\"")
          .replace("\n", "\\n")
          .replace("\r", "\\r")
          .replace("\t", "\\t") +
        "\""
  }
}
