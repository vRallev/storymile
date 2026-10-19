package software.ralf.storymile.templates

import androidx.compose.runtime.compositionLocalOf

/** Identifies a rendered navigation entry and its source within one navigation host. */
data class NavigationEntryIdentity(
  /** Opaque identity of this entry, distinct from entries in other navigation hosts. */
  val current: Any,
  /** Identity of the preceding entry, retained during exit transitions. Null at the root. */
  val previous: Any?,
)

/** Navigation identities for UI state and transitions. Null outside a navigation entry. */
val LocalNavigationEntryIdentity = compositionLocalOf<NavigationEntryIdentity?> { null }
