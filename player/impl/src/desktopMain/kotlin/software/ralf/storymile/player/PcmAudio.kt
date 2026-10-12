package software.ralf.storymile.player

internal class PcmAudio(val samples: ShortArray, val sampleRate: Int, val channels: Int) {
  val frameCount: Int = samples.size / channels
  val durationMillis: Long = frameCount * 1_000L / sampleRate
}
