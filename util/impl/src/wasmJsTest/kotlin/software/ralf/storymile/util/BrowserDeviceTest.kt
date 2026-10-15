package software.ralf.storymile.util

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test

class BrowserDeviceTest {
  @Test
  fun `explicit countries take priority over language inference`() {
    val device: Device = PlatformBindings.provideDevice()

    withBrowserLanguages("en,fr-CA") { assertThat(device.countryCode).isEqualTo("CA") }
    withBrowserLanguages("en-GB,fr-CA") { assertThat(device.countryCode).isEqualTo("GB") }
  }

  @Test
  fun `missing countries are inferred from the preferred language and script`() {
    val device: Device = PlatformBindings.provideDevice()

    withBrowserLanguages("de") { assertThat(device.countryCode).isEqualTo("DE") }
    withBrowserLanguages("zh-Hant") { assertThat(device.countryCode).isEqualTo("TW") }
    withBrowserLanguages("en") { assertThat(device.countryCode).isEqualTo("US") }
  }

  @Test
  fun `a region that spans countries remains unavailable`() {
    val device: Device = PlatformBindings.provideDevice()

    withBrowserLanguages("es-419") { assertThat(device.countryCode).isNull() }
  }
}
