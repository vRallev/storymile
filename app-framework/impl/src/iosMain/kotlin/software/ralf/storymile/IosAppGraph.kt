package software.ralf.storymile

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import platform.UIKit.UIApplication
import software.ralf.app.platform.scope.RootScopeProvider
import software.ralf.storymile.user.IosGoogleLoginPlatform

/** Final iOS graph, including dependencies supplied by UIKit. */
@DependencyGraph(AppScope::class)
internal interface IosAppGraph {
  /** Factory for the iOS application graph. */
  @DependencyGraph.Factory
  fun interface Factory {
    /** Creates a graph with UIKit and root-scope dependencies. */
    fun create(
      @Provides uiApplication: UIApplication,
      @Provides rootScopeProvider: RootScopeProvider,
      @Provides googleSignIn: IosGoogleLoginPlatform.Host,
    ): IosAppGraph
  }
}

/** Called from Swift to retain the application before creating the production iOS graph. */
@Suppress("unused")
fun createIosApplication(
  application: UIApplication,
  googleSignIn: IosGoogleSignIn,
): KmpApplication {
  val host = googleSignIn.asHost()
  return KmpApplication {
    createGraphFactory<IosAppGraph.Factory>()
      .create(
        uiApplication = application,
        rootScopeProvider = it,
        googleSignIn = host,
      )
  }
}
