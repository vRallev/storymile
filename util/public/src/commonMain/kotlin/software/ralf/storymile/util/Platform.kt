package software.ralf.storymile.util

/** Runtime platform for platform-specific application behavior. */
sealed interface Platform {
  /** Android phones, tablets, and other Android devices. */
  data object Android : Platform

  /** iOS devices and simulators. */
  data object Ios : Platform

  /** WebAssembly running in a browser. */
  data object Wasm : Platform

  /** A desktop JVM runtime, identified by its operating system. */
  sealed interface Desktop : Platform {
    /** Windows desktop runtime. */
    data object Windows : Desktop

    /** macOS desktop runtime. */
    data object Mac : Desktop

    /** Linux desktop runtime. */
    data object Linux : Desktop
  }
}
