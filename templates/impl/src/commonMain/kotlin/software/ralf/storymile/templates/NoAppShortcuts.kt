package software.ralf.storymile.templates

import androidx.compose.ui.input.key.Key
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding

@ContributesBinding(AppScope::class)
class NoAppShortcuts : AppShortcuts {
  override val logoLongPress: (suspend () -> Unit)? = null

  override fun actionForKey(key: Key): (suspend () -> Unit)? = null
}
