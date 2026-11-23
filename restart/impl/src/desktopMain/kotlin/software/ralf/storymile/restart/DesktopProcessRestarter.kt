package software.ralf.storymile.restart

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import java.io.File
import java.lang.management.ManagementFactory
import java.nio.file.Files

@ContributesBinding(AppScope::class)
class DesktopProcessRestarter
internal constructor(
  private val launch: () -> Unit,
  private val terminate: () -> Unit,
) : ProcessRestarter {
  @Inject
  constructor(
    @Named("desktopMainClass") mainClassName: String,
    @Named("desktopArguments") arguments: Array<String>,
  ) : this(
    launch = launchAction(mainClassName = mainClassName, arguments = arguments),
    terminate = { Runtime.getRuntime().halt(0) },
  )

  override fun restart() {
    try {
      launch()
    } finally {
      // Scope cleanup has already had its deadline. Shutdown hooks must not extend it.
      terminate()
    }
  }

  private companion object {
    fun launchAction(mainClassName: String, arguments: Array<String>): () -> Unit {
      DesktopRestartCommand.deleteArgumentsFile()
      val packagedExecutable = System.getProperty("jpackage.app-path")
      val command =
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
      return { launchReplacement(command) }
    }

    private fun launchReplacement(command: List<String>) {
      var argumentsFile: File? = null
      var launched = false
      try {
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
  }
}
