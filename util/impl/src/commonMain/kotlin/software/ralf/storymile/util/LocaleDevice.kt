package software.ralf.storymile.util

internal class LocaleDevice(private val readCountryCode: () -> String?) : Device {
  override val countryCode: String?
    get() =
      readCountryCode()?.uppercase()?.takeIf { code ->
        code.length == 2 && code.all { it in 'A'..'Z' }
      }
}
