package software.ralf.storymile

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.ForScope
import software.ralf.storymile.application.Application
import software.ralf.storymile.application.ApplicationGraph
import software.ralf.storymile.storage.Storage
import software.ralf.storymile.util.Platform

/** Shared dependencies exposed by each platform's final application graph. */
@ContributesTo(AppScope::class)
interface AppGraph : ApplicationGraph {
  /** Application lifecycle shared across graph replacements. */
  val application: Application

  /** Platform used by the running application. */
  val platform: Platform

  /** Preferences, files, and caches owned by the app scope. */
  @ForScope(AppScope::class) val storage: Storage

  /** Factory for a host's independently cancellable presenter tree. */
  val templateProviderFactory: TemplateProvider.Factory
}
