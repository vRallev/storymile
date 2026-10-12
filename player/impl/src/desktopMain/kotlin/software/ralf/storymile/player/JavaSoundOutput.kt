package software.ralf.storymile.player

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

internal class JavaSoundOutput : AudioOutput {
  private var line: SourceDataLine? = null

  override val playedFrames: Long
    get() = checkNotNull(line).longFramePosition

  override val availableBytes: Int
    get() = checkNotNull(line).available()

  override fun open(audio: PcmAudio) {
    val format = AudioFormat(audio.sampleRate.toFloat(), 16, audio.channels, true, false)
    val newLine = AudioSystem.getSourceDataLine(format)
    line = newLine
    newLine.open(format, audio.sampleRate / 5 * audio.channels * 2)
  }

  override fun start() {
    checkNotNull(line).start()
  }

  override fun stop() {
    line?.stop()
  }

  override fun flush() {
    line?.flush()
  }

  override fun write(bytes: ByteArray, offset: Int, count: Int): Int =
    checkNotNull(line).write(bytes, offset, count)

  override fun close() {
    line?.close()
    line = null
  }
}
