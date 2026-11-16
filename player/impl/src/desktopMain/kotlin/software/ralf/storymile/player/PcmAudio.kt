package software.ralf.storymile.player

internal abstract class PcmAudio(
  val sampleRate: Int,
  val channels: Int,
  val frameCount: Int,
) : AutoCloseable {
  val durationMillis: Long = frameCount * 1_000L / sampleRate

  abstract fun readSamples(startFrame: Int, endFrame: Int): ShortArray
}
