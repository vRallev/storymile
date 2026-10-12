package software.ralf.storymile.player

import assertk.assertThat
import assertk.assertions.isTrue
import java.net.URI
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.ExperimentalResourceApi
import software.ralf.storymile.player.impl.generated.resources.Res

@OptIn(ExperimentalResourceApi::class)
class Mp3DecoderTest {
  @Test
  fun `all bundled episode URIs decode to audible PCM`() = runTest {
    for (episode in 1..3) {
      val source = Res.getUri("files/test-episodes/test-episode-$episode.mp3")
      val audio = Mp3Decoder.decode(source)
      assertThat(audio.durationMillis > 1_000).isTrue()
      assertThat(audio.samples.any { it != 0.toShort() }).isTrue()
      assertThat(audio.channels in 1..2).isTrue()
    }
  }

  @Test
  fun `episode URIs inside packaged JARs decode without extraction`() = runTest {
    val archive = Files.createTempFile("storymile-audio", ".jar")
    try {
      JarOutputStream(Files.newOutputStream(archive)).use { jar ->
        jar.putNextEntry(JarEntry("episode.mp3"))
        URI(Res.getUri("files/test-episodes/test-episode-1.mp3")).toURL().openStream().use {
          it.copyTo(jar)
        }
        jar.closeEntry()
      }
      val audio = Mp3Decoder.decode("jar:${archive.toUri()}!/episode.mp3")
      assertThat(audio.durationMillis > 1_000).isTrue()
      assertThat(audio.samples.any { it != 0.toShort() }).isTrue()
    } finally {
      Files.deleteIfExists(archive)
    }
  }
}
