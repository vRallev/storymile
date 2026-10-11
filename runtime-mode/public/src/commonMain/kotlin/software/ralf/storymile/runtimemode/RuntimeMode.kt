package software.ralf.storymile.runtimemode

/** Selects real or fake implementations when a feature supports them. */
enum class RuntimeMode {
  /** Uses real implementations. */
  Real,
  /** Uses fake implementations. */
  Fake;

  /** Defaults for applications without a saved mode. */
  companion object {
    /** Mode used when no value is saved. */
    val DEFAULT: RuntimeMode = Real
  }
}
