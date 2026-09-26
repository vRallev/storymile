package software.ralf.storymile.util

import android.content.Context
import software.ralf.app.platform.scope.RootScopeProvider

/**
 * Returns the application's root scope provider. The application must implement
 * [RootScopeProvider].
 */
fun Context.rootScopeProvider(): RootScopeProvider = applicationContext as RootScopeProvider
