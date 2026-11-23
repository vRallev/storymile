package software.ralf.storymile.player

import java.nio.ByteBuffer

internal abstract class PcmAudio(
  val sampleRate: Int,
  val channels: Int,
  val frameCount: Int,
) : AutoCloseable {
  val durationMillis: Long = frameCount * 1_000L / sampleRate

  /** Fills [destination] with interleaved 16-bit little-endian PCM from [startFrame]. */
  abstract fun readFrames(startFrame: Int, destination: ByteBuffer)
}
