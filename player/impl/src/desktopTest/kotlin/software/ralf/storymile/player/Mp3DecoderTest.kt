package software.ralf.storymile.player

import assertk.assertThat
import assertk.assertions.isTrue
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class Mp3DecoderTest {
  private val fixtureUrl = checkNotNull(javaClass.getResource("/test-episode.mp3"))

  @Test
  fun `bundled MP3 decodes to audible PCM`() = runTest {
    Mp3Decoder.decode(fixtureUrl.toString()).use { assertAudible(it) }
  }

  @Test
  fun `episode URIs inside packaged JARs decode without extraction`() = runTest {
    val archive = Files.createTempFile("storymile-audio", ".jar")
    try {
      JarOutputStream(Files.newOutputStream(archive)).use { jar ->
        jar.putNextEntry(JarEntry("episode.mp3"))
        fixtureUrl.openStream().use {
          it.copyTo(jar)
        }
        jar.closeEntry()
      }
      Mp3Decoder.decode("jar:${archive.toUri()}!/episode.mp3").use { assertAudible(it) }
    } finally {
      Files.deleteIfExists(archive)
    }
  }

  @Test
  fun `download URLs can redirect to an MP3 enclosure`() = runTest {
    val fixture = fixtureUrl.openStream().use { it.readBytes() }
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/download") { request ->
      request.use {
        it.responseHeaders.add("Location", "/audio")
        it.sendResponseHeaders(302, -1)
      }
    }
    server.createContext("/audio") { request ->
      request.use {
        it.responseHeaders.add("Content-Type", "audio/mpeg")
        it.sendResponseHeaders(200, fixture.size.toLong())
        it.responseBody.write(fixture)
      }
    }
    server.start()
    try {
      Mp3Decoder.decode("http://127.0.0.1:${server.address.port}/download").use {
        assertAudible(it)
      }
    } finally {
      server.stop(0)
    }
  }

  private fun assertAudible(audio: PcmAudio) {
    assertThat(audio.durationMillis > 1_000).isTrue()
    assertThat(audio.channels in 1..2).isTrue()
    val samples = ByteBuffer.allocate(audio.sampleRate * audio.channels * Short.SIZE_BYTES)
    audio.readFrames(startFrame = 0, destination = samples)
    assertThat(samples.array().any { it != 0.toByte() }).isTrue()
  }
}
