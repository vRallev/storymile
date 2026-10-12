package software.ralf.storymile.player

internal interface AudioOutput {
  val playedFrames: Long
  val availableBytes: Int

  fun open(audio: PcmAudio)

  fun start()

  fun stop()

  fun flush()

  fun write(bytes: ByteArray, offset: Int, count: Int): Int

  fun close()
}
