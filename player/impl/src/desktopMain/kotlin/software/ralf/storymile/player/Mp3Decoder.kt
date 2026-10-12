package software.ralf.storymile.player

import java.io.ByteArrayOutputStream
import java.net.URI
import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Decoder
import javazoom.jl.decoder.SampleBuffer
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal object Mp3Decoder {
  suspend fun decode(source: String): PcmAudio {
    URI(source).toURL().openStream().buffered().use { input ->
      val stream = Bitstream(input)
      try {
        val decoder = Decoder()
        val samples = ByteArrayOutputStream()
        var sampleRate = 0
        var channels = 0
        while (true) {
          currentCoroutineContext().ensureActive()
          val header = stream.readFrame() ?: break
          val decoded = decoder.decodeFrame(header, stream) as SampleBuffer
          if (sampleRate == 0) {
            sampleRate = decoded.sampleFrequency
            channels = decoded.channelCount
          }
          check(sampleRate == decoded.sampleFrequency && channels == decoded.channelCount) {
            "The audio format changed within the episode."
          }
          for (index in 0 until decoded.bufferLength) {
            val sample = decoded.buffer[index].toInt()
            samples.write(sample and 0xff)
            samples.write((sample ushr 8) and 0xff)
          }
          stream.closeFrame()
        }
        check(sampleRate > 0 && samples.size() > 0) { "The episode contains no MP3 audio." }
        val bytes = samples.toByteArray()
        return PcmAudio(
          samples =
            ShortArray(bytes.size / 2) { index ->
              ((bytes[index * 2].toInt() and 0xff) or (bytes[index * 2 + 1].toInt() shl 8))
                .toShort()
            },
          sampleRate = sampleRate,
          channels = channels,
        )
      } finally {
        stream.close()
      }
    }
  }
}
