package software.ralf.storymile.restart

import assertk.assertThat
import assertk.assertions.isEqualTo
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class DesktopProcessRestarterTest {
  @Test
  fun `restart terminates the old JVM after launching the replacement`() {
    val events = mutableListOf<String>()
    val restarter: ProcessRestarter =
      DesktopProcessRestarter(
        launch = { events += "launch" },
        terminate = { events += "terminate" },
      )

    restarter.restart()

    assertThat(events).isEqualTo(listOf("launch", "terminate"))
  }

  @Test
  fun `restart still terminates if launch preparation or process creation fails`() {
    for (failure in
      listOf(IllegalStateException("Missing configuration"), IOException("Cannot launch"))) {
      val events = mutableListOf<String>()
      val restarter: ProcessRestarter =
        DesktopProcessRestarter(
          launch = {
            events += "launch"
            throw failure
          },
          terminate = { events += "terminate" },
        )

      assertSame(failure, assertFailsWith<Exception> { restarter.restart() })
      assertThat(events).isEqualTo(listOf("launch", "terminate"))
    }
  }
}
