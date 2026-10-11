package software.ralf.storymile.scope.runtime

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import java.io.File
import java.lang.management.ManagementFactory
import java.nio.file.Files

@ContributesBinding(AppScope::class)
class DesktopProcessRestarter() : ProcessRestarter {
  private var launch: () -> Unit = { launchReplacement() }
  private var terminate: () -> Unit = { Runtime.getRuntime().halt(0) }

  internal constructor(launch: () -> Unit, terminate: () -> Unit) : this() {
    this.launch = launch
    this.terminate = terminate
  }

  override fun restart() {
    try {
      launch()
    } finally {
      // Scope cleanup has already had its deadline. Shutdown hooks must not extend it.
      terminate()
    }
  }

  private fun launchReplacement() {
    var argumentsFile: File? = null
    var launched = false
    try {
      val command = checkNotNull(command) { "Desktop restart is not configured." }
      argumentsFile =
        if (System.getProperty("jpackage.app-path") == null) {
          Files.createTempFile("storymile-restart-", ".args").toFile()
        } else {
          null
        }
      val builder =
        ProcessBuilder(
          if (argumentsFile != null) {
            DesktopRestartCommand.withArgumentsFile(command, argumentsFile)
          } else {
            command
          },
        )
      // VM options are already captured below. Do not attach inherited agents a second time.
      builder
        .environment()
        .keys
        .removeAll(listOf("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS"))
      builder.inheritIO().start()
      launched = true
    } finally {
      if (!launched) {
        argumentsFile?.delete()
      }
    }
  }

  companion object {
    private var command: List<String>? = null

    /**
     * Captures the launcher and arguments before the app starts. Development restarts omit agents.
     */
    fun configure(mainClassName: String, arguments: Array<String>) {
      DesktopRestartCommand.deleteArgumentsFile()
      val packagedExecutable = System.getProperty("jpackage.app-path")
      command =
        if (packagedExecutable != null) {
          listOf(packagedExecutable) + arguments
        } else {
          DesktopRestartCommand(
              javaExecutable =
                File(
                    System.getProperty("java.home"),
                    if (System.getProperty("os.name").startsWith("Windows")) {
                      "bin/java.exe"
                    } else {
                      "bin/java"
                    },
                  )
                  .absolutePath,
              jvmArguments = ManagementFactory.getRuntimeMXBean().inputArguments,
              classPath = System.getProperty("java.class.path"),
              mainClassName = mainClassName,
              arguments = arguments.toList(),
            )
            .command()
        }
    }
  }
}
