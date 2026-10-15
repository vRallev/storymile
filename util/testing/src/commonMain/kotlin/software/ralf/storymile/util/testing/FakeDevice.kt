package software.ralf.storymile.util.testing

import software.ralf.storymile.util.Device

class FakeDevice(override var countryCode: String? = "US") : Device
