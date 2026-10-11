package software.ralf.storymile.runtimemode

/** Selects real or fake implementations when a feature supports them. */
enum class RuntimeMode {
  /** Uses real implementations. */
  Real,
  /** Uses fake implementations. */
  Fake,
}
