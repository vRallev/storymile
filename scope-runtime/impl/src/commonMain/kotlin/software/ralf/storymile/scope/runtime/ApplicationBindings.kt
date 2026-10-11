package software.ralf.storymile.scope.runtime

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import software.ralf.app.platform.scope.RootScopeProvider

/** Makes the application owner available to App Platform services. */
@BindingContainer
@ContributesTo(AppScope::class)
object ApplicationBindings {
  /** Uses the same scope owner across application restarts. */
  @Provides fun rootScopeProvider(application: Application): RootScopeProvider = application
}
