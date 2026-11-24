package software.ralf.storymile.user

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.SingleIn
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import software.ralf.app.platform.inject.metro.ContributesScoped
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.Scoped
import software.ralf.app.platform.scope.coroutine.addCoroutineScopeScoped
import software.ralf.app.platform.scope.di.metro.addMetroDependencyGraph
import software.ralf.app.platform.scope.register
import software.ralf.storymile.logging.Logger.Companion.logger
import software.ralf.storymile.storage.Storage
import software.ralf.storymile.util.mapStateFlow

@SingleIn(AppScope::class)
@ContributesScoped(AppScope::class)
class UserManagerImpl(
  private val userGraphFactory: UserGraph.Factory,
  private val storageFactory: Storage.Factory,
  @ForScope(AppScope::class) appStorage: Storage,
  @ForScope(AppScope::class) private val coroutineScope: CoroutineScope,
) : UserManager, Scoped {
  private val preferences = appStorage.preferences("user-session")
  private val transitions = Mutex()

  @Volatile private var startup: Deferred<Unit>? = null
  @Volatile private var sessionState: SessionState? = null

  override val user: StateFlow<User>
    get() = requireSessionState().user

  override val userScope: StateFlow<Scope?>
    get() = requireSessionState().scope

  override fun onEnterScope(scope: Scope) {
    startup = coroutineScope.async {
      val saved = preferences.data.first()
      val user = saved.readUser() ?: createInitialUser()
      val active = startSession(parent = scope, user = user)
      sessionState = SessionState(scope, active)
      for (namespace in saved[pendingDeletionKey].orEmpty() - user.namespace) {
        deleteInBackground(namespace)
      }
    }
  }

  override fun onExitScope() {
    startup = null
    sessionState = null
  }

  override suspend fun awaitReady() {
    checkNotNull(startup) { "The user manager is not active." }.await()
  }

  override suspend fun signIn() {
    changeSession { state ->
      val current = state.active.value
      if (current.user.isGuest) {
        updateIdentity(state = state, current = current, isGuest = false)
      }
    }
  }

  override suspend fun signOut(keepData: Boolean) {
    changeSession { state ->
      val current = state.active.value
      if (current.user.isGuest) {
        return@changeSession
      }
      if (keepData) {
        updateIdentity(state = state, current = current, isGuest = true)
      } else {
        wipeStorage(state, current)
      }
    }
  }

  private fun requireSessionState(): SessionState {
    check(coroutineScope.isActive) { "The user scope is not active." }
    return checkNotNull(sessionState) { "The user scope is not active." }
  }

  private suspend fun createInitialUser(): LocalUser {
    val storageId = Uuid.random().toHexString()
    val namespace =
      if (storageFactory.hasNamespace(legacyGuestNamespace)) {
        legacyGuestNamespace
      } else {
        "user-$storageId"
      }
    val initial = LocalUser(storageId = storageId, namespace = namespace, isGuest = true)
    return checkNotNull(
      preferences
        .edit {
          if (it.readUser() == null) {
            it.writeUser(initial)
          }
        }
        .readUser(),
    )
  }

  private suspend fun startSession(parent: Scope, user: LocalUser): ActiveSession {
    val context = currentCoroutineContext()
    context.ensureActive()
    val scopedUser = ScopedUser(user)
    val graph =
      userGraphFactory.createUserGraph(user = scopedUser, storageNamespace = user.namespace)
    val scope =
      parent.buildChild("user-${user.storageId}") {
        addMetroDependencyGraph(graph)
        addCoroutineScopeScoped(graph.coroutineOwner)
      }
    val session = ActiveSession(user = user, scopedUser = scopedUser, scope = scope, graph = graph)
    try {
      context.ensureActive()
      scope.register(graph.userScopedInstances)
      context.ensureActive()
    } catch (exception: Exception) {
      scope.destroy()
      session.ownerJob.join()
      throw exception
    }
    return session
  }

  private suspend fun changeSession(action: suspend (SessionState) -> Unit) {
    awaitReady()
    // A presenter can disappear while a storage change is in progress. The app owns the change.
    coroutineScope
      .async {
        try {
          transitions.withLock { action(requireSessionState()) }
          Result.success(Unit)
        } catch (exception: CancellationException) {
          throw exception
        } catch (exception: Exception) {
          Result.failure(exception)
        }
      }
      .await()
      .getOrThrow()
  }

  private suspend fun updateIdentity(
    state: SessionState,
    current: ActiveSession,
    isGuest: Boolean,
  ) {
    val user = current.user.copy(isGuest = isGuest)
    preferences.edit { it.writeUser(user) }
    current.scopedUser.snapshot = user
    state.active.value = current.copy(user = user)
  }

  private suspend fun wipeStorage(state: SessionState, current: ActiveSession) {
    val storageId = Uuid.random().toHexString()
    val guest = LocalUser(storageId = storageId, namespace = "user-$storageId", isGuest = true)
    // A restart during the copy can remove the unfinished destination.
    preferences.edit {
      it[pendingDeletionKey] = it[pendingDeletionKey].orEmpty() + guest.namespace
    }

    state.active.value = current.copy(isReplacingStorage = true)
    current.scope.destroy()
    // Wait for the old Room database and DataStore files to close.
    current.ownerJob.join()
    var prepared: ActiveSession? = null
    try {
      storageFactory.copyPreferences(
        sourceNamespace = current.user.namespace,
        targetNamespace = guest.namespace,
      )
      prepared = startSession(parent = state.parent, user = guest)
      preferences.edit {
        it.writeUser(guest)
        it[pendingDeletionKey] =
          it[pendingDeletionKey].orEmpty() - guest.namespace + current.user.namespace
      }
    } catch (exception: CancellationException) {
      prepared?.scope?.destroy()
      throw exception
    } catch (exception: Exception) {
      prepared?.scope?.destroy()
      prepared?.ownerJob?.join()
      deleteInBackground(guest.namespace)
      state.active.value = startSession(parent = state.parent, user = current.user)
      this.logger.e(exception) { "Could not replace user storage." }
      throw exception
    }

    state.active.value = checkNotNull(prepared)
    deleteInBackground(current.user.namespace)
  }

  private fun deleteInBackground(namespace: String) {
    coroutineScope.launch {
      while (true) {
        try {
          if (preferences.data.first()[namespaceKey] == namespace) {
            return@launch
          }
          storageFactory.deleteNamespace(namespace)
          preferences.edit {
            it[pendingDeletionKey] = it[pendingDeletionKey].orEmpty() - namespace
          }
          return@launch
        } catch (exception: CancellationException) {
          throw exception
        } catch (exception: Exception) {
          this@UserManagerImpl.logger.e(exception) { "Could not delete retired user storage." }
          delay(30.seconds)
        }
      }
    }
  }

  private fun Preferences.readUser(): LocalUser? {
    val storageId = this[storageIdKey] ?: return null
    return LocalUser(
      storageId = storageId,
      namespace = this[namespaceKey] ?: "user-$storageId",
      isGuest = this[isGuestKey] ?: true,
    )
  }

  private fun MutablePreferences.writeUser(user: LocalUser) {
    this[storageIdKey] = user.storageId
    this[namespaceKey] = user.namespace
    this[isGuestKey] = user.isGuest
  }

  private companion object {
    const val legacyGuestNamespace = "user-guest"
    val storageIdKey = stringPreferencesKey("storage-id")
    val namespaceKey = stringPreferencesKey("namespace")
    val isGuestKey = booleanPreferencesKey("is-guest")
    val pendingDeletionKey = stringSetPreferencesKey("pending-deletion")
  }

  private data class LocalUser(
    override val storageId: String,
    val namespace: String,
    override val isGuest: Boolean,
  ) : User {
    override val subscriptionTier = SubscriptionTier.BASIC
  }

  private class ScopedUser(initial: LocalUser) : User {
    @Volatile var snapshot: LocalUser = initial

    override val storageId: String = initial.storageId

    override val isGuest: Boolean
      get() = snapshot.isGuest

    override val subscriptionTier: SubscriptionTier
      get() = snapshot.subscriptionTier
  }

  private data class ActiveSession(
    val user: LocalUser,
    val scopedUser: ScopedUser,
    val scope: Scope,
    val graph: UserGraph,
    val isReplacingStorage: Boolean = false,
  ) {
    val ownerJob: Job
      get() = graph.coroutineOwner.coroutineContext.job
  }

  private class SessionState(val parent: Scope, initial: ActiveSession) {
    val active = MutableStateFlow(initial)
    val user: StateFlow<User> = active.mapStateFlow { it.user }
    val scope: StateFlow<Scope?> = active.mapStateFlow { session ->
      session.scope.takeUnless { session.isReplacingStorage }
    }
  }
}
