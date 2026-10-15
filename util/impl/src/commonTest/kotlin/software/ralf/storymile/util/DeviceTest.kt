package software.ralf.storymile.util

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test

class DeviceTest {
  @Test
  fun `country codes are uppercase and reflect locale changes`() {
    var countryCode = "de"
    val device: Device = LocaleDevice { countryCode }

    assertThat(device.countryCode).isEqualTo("DE")

    countryCode = "GB"
    assertThat(device.countryCode).isEqualTo("GB")
  }

  @Test
  fun `missing countries and non-country regions are unavailable`() {
    for (countryCode in listOf(null, "", "419", "001")) {
      val device: Device = LocaleDevice { countryCode }

      assertThat(device.countryCode).isNull()
    }
  }
}
