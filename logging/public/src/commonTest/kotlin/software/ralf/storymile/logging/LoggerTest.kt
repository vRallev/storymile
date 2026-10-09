package software.ralf.storymile.logging

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import software.ralf.storymile.logging.Logger.Companion.logger

class LoggerTest {
  @Test
  fun `logger property tags named receivers with their simple class name`() {
    val logger = NamedReceiver().logger

    assertThat(logger).isEqualTo(Logger.withTag("NamedReceiver"))
  }

  @Test
  fun `logger property falls back to anonymous when the receiver has no simple class name`() {
    val logger = object {}.logger

    assertThat(logger).isEqualTo(Logger.withTag("Anonymous"))
  }

  private class NamedReceiver
}
