package software.ralf.storymile.runtimemode

/** Selects the data source used by services in the running application. */
enum class RuntimeMode {
  /** Uses the real service implementations. */
  Real,

  /** Uses local, deterministic service implementations. */
  Fake;

  /** Defaults used before a mode is selected. */
  companion object {
    /** Mode used when no saved selection is available. */
    val DEFAULT = Real
  }
}
