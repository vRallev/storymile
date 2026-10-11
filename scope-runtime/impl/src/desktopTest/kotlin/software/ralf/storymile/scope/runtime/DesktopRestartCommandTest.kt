package software.ralf.storymile.scope.runtime

import assertk.assertThat
import assertk.assertions.isEqualTo
import java.io.File
import java.nio.file.Files
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertTrue

class DesktopRestartCommandTest {
  @Test
  fun `restart preserves paths and app arguments without shell parsing`() {
    val command =
      DesktopRestartCommand(
          javaExecutable = "/Java Runtime/bin/java",
          jvmArguments = listOf("-Xmx1g", "-Dstorymile.path=/my files"),
          classPath = "/app classes:/app libraries/a.jar",
          mainClassName = "software.ralf.storymile.MainKt",
          arguments = listOf("--window-size=tablet", "a value with spaces", "\"quoted\""),
        )
        .command()

    assertThat(command)
      .isEqualTo(
        listOf(
          "/Java Runtime/bin/java",
          "-Xmx1g",
          "-Dstorymile.path=/my files",
          "-cp",
          "/app classes:/app libraries/a.jar",
          "software.ralf.storymile.MainKt",
          "--window-size=tablet",
          "a value with spaces",
          "\"quoted\"",
        ),
      )
  }

  @Test
  fun `restart drops hot reload and debugger agents with their old connections`() {
    val command =
      DesktopRestartCommand(
          javaExecutable = "java",
          jvmArguments =
            listOf(
              "-javaagent:/hot-reload-agent.jar",
              "-agentlib:jdwp=transport=dt_socket,address=5005,server=y",
              "-agentpath:/profiler.so",
              "-Xrunjdwp:transport=dt_socket,address=5006",
              "-Dcompose.reload.isActive=true",
              "-Dcompose.reload.orchestration.port=1234",
              "--enable-native-access=ALL-UNNAMED",
            ),
          classPath = "classes",
          mainClassName = "MainKt",
          arguments = emptyList(),
        )
        .command()

    assertThat(command)
      .isEqualTo(listOf("java", "--enable-native-access=ALL-UNNAMED", "-cp", "classes", "MainKt"))
  }

  @Test
  fun `argument file launches a fresh JVM and preserves spaces quotes and backslashes`() {
    val directory = Files.createTempDirectory("storymile restart ").toFile()
    try {
      val source = File(directory, "RestartProbe.java")
      source.writeText(
        """
        class RestartProbe {
          public static void main(String[] args) {
            System.out.println(System.getProperty("storymile.probe"));
            for (String arg : args) {
              System.out.println(java.util.Base64.getEncoder().encodeToString(
                  arg.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            }
          }
        }
        """
          .trimIndent(),
      )
      val arguments =
        listOf(
          "--window-size=tablet",
          "argument with spaces",
          "C:\\my files\\",
          "\"quoted\"",
          "a\nb\tc",
        )
      val command =
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
            jvmArguments = listOf("-Dstorymile.probe=fresh JVM", "-javaagent:/missing-agent.jar"),
            classPath = directory.absolutePath,
            mainClassName = source.absolutePath,
            arguments = arguments,
          )
          .command()
      val fileCommand =
        DesktopRestartCommand.withArgumentsFile(command, File(directory, "restart arguments.args"))

      val process = ProcessBuilder(fileCommand).redirectErrorStream(true).start()
      try {
        assertTrue(process.waitFor(20, TimeUnit.SECONDS), "The restart probe did not exit.")
        val output = process.inputStream.bufferedReader().readText()
        assertThat(process.exitValue()).isEqualTo(0)
        assertThat(output.trim().lines())
          .isEqualTo(
            listOf("fresh JVM") +
              arguments.map { Base64.getEncoder().encodeToString(it.toByteArray()) },
          )
      } finally {
        process.destroyForcibly()
      }
    } finally {
      directory.deleteRecursively()
    }
  }
}
