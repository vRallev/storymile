package software.ralf.storymile.player

import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption.DELETE_ON_CLOSE
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Decoder
import javazoom.jl.decoder.SampleBuffer
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal object Mp3Decoder {
  suspend fun decode(source: String): PcmAudio {
    val path = Files.createTempFile("storymile-audio", ".pcm")
    val file =
      try {
        FileChannel.open(path, READ, WRITE, DELETE_ON_CLOSE)
      } catch (exception: Exception) {
        Files.deleteIfExists(path)
        throw exception
      }
    try {
      return decodeToFile(source = source, file = file)
    } catch (failure: Throwable) {
      file.close()
      throw failure
    }
  }

  private suspend fun decodeToFile(source: String, file: FileChannel): PcmAudio {
    val coroutineContext = currentCoroutineContext()
    val connection = URI(source).toURL().openConnection()
    connection.connectTimeout = 10_000
    connection.readTimeout = 10_000
    connection.getInputStream().buffered().use { input ->
      val stream = Bitstream(input)
      try {
        val decoder = Decoder()
        val bytes = ByteBuffer.allocateDirect(64 * 1024).order(ByteOrder.LITTLE_ENDIAN)
        val samples = bytes.asShortBuffer()
        fun writeSamples() {
          bytes.limit(samples.position() * Short.SIZE_BYTES)
          while (bytes.hasRemaining()) {
            file.write(bytes)
          }
          bytes.clear()
          samples.clear()
        }
        var sampleRate = 0
        var channels = 0
        while (true) {
          coroutineContext.ensureActive()
          val header = stream.readFrame() ?: break
          val decoded = decoder.decodeFrame(header, stream) as SampleBuffer
          if (sampleRate == 0) {
            sampleRate = decoded.sampleFrequency
            channels = decoded.channelCount
          }
          check(sampleRate == decoded.sampleFrequency && channels == decoded.channelCount) {
            "The audio format changed within the episode."
          }
          if (samples.remaining() < decoded.bufferLength) {
            writeSamples()
          }
          samples.put(decoded.buffer, 0, decoded.bufferLength)
          stream.closeFrame()
        }
        writeSamples()
        check(sampleRate > 0 && file.position() > 0) { "The episode contains no MP3 audio." }
        return FilePcmAudio(
          sampleRate = sampleRate,
          channels = channels,
          frameCount = Math.toIntExact(file.position() / Short.SIZE_BYTES / channels),
          file = file,
        )
      } finally {
        stream.close()
      }
    }
  }

  private class FilePcmAudio(
    sampleRate: Int,
    channels: Int,
    frameCount: Int,
    private val file: FileChannel,
  ) : PcmAudio(sampleRate = sampleRate, channels = channels, frameCount = frameCount) {
    override fun readFrames(startFrame: Int, destination: ByteBuffer) {
      var offset = startFrame.toLong() * channels * Short.SIZE_BYTES
      while (destination.hasRemaining()) {
        val count = file.read(destination, offset)
        check(count > 0) { "Could not read the decoded audio." }
        offset += count
      }
    }

    override fun close() {
      file.close()
    }
  }
}
