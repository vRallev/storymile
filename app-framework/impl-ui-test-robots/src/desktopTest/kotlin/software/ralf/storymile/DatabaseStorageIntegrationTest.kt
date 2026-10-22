package software.ralf.storymile

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import java.nio.file.Files
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runCurrent
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.coroutine.CoroutineScopeScoped
import software.ralf.app.platform.scope.coroutine.addCoroutineScopeScoped
import software.ralf.app.platform.scope.register
import software.ralf.app.platform.scope.runTestWithScope
import software.ralf.storymile.database.DatabasePlatform
import software.ralf.storymile.database.PlatformDatabase
import software.ralf.storymile.database.PodcastDatabase
import software.ralf.storymile.database.RoomPodcastDatabase
import software.ralf.storymile.database.UserPodcastDatabase
import software.ralf.storymile.podcast.PodcastFeed
import software.ralf.storymile.storage.DefaultStorage
import software.ralf.storymile.storage.OkioStoragePlatform
import software.ralf.storymile.storage.Storage

class DatabaseStorageIntegrationTest {
  @Test
  fun `reopening scoped storage waits for database close and preserves saved podcasts`() =
    reopenDatabase(cancelWaitingOwner = false)

  @Test
  fun `canceling a waiting database owner preserves the close barrier for its replacement`() =
    reopenDatabase(cancelWaitingOwner = true)

  private fun reopenDatabase(cancelWaitingOwner: Boolean): TestResult = runTestWithScope { scope ->
    val root = Files.createTempDirectory("storymile-database-storage").toString().toPath()
    val storagePlatform =
      object : OkioStoragePlatform(FileSystem.SYSTEM, Dispatchers.IO) {
        override fun rootDirectory(): Path = root

        override fun cacheDirectory(): Path = root / "cache"
      }
    val factory: Storage.Factory =
      object : Storage.Factory {
        override fun create(
          namespace: String,
          coroutineScope: CoroutineScope,
          mustBePersistent: Boolean,
        ): Storage = DefaultStorage(namespace, coroutineScope, storagePlatform)
      }
    val platform = DelayedClosePlatform()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val fixtures = mutableListOf<DatabaseFixture>()
    fun createFixture(): DatabaseFixture =
      DatabaseFixture(
          parentScope = scope,
          storageFactory = factory,
          platform = platform,
          dispatcher = dispatcher,
        )
        .also {
          fixtures += it
        }
    val original = createFixture()
    try {
      val podcast =
        PodcastFeed(
          feedUrl = "https://feeds.test/show.xml",
          title = "Saved show",
          description = "Show description",
          author = "Publisher",
          artworkUrl = null,
          link = null,
          episodes = emptyList(),
        )
      original.database.insert(podcast)

      original.destroy()
      platform.closeStarted.await()
      val waiting = createFixture()
      val waitingRead = async { waiting.database.podcasts.first() }
      runCurrent()

      assertThat(platform.openedWhileClosing).isFalse()
      assertThat(waitingRead.isCompleted).isFalse()

      val (reopened, reading) =
        if (cancelWaitingOwner) {
          waiting.destroy()
          waitingRead.cancelAndJoin()
          waiting.close()
          val replacement = createFixture()
          val replacementRead = async { replacement.database.podcasts.first() }
          runCurrent()
          assertThat(platform.openedWhileClosing).isFalse()
          assertThat(replacementRead.isCompleted).isFalse()
          replacement to replacementRead
        } else {
          waiting to waitingRead
        }

      platform.finishClose.complete(Unit)
      original.close()
      assertThat(reading.await()).isEqualTo(listOf(podcast))
      val updated = podcast.copy(title = "Updated after reopening")
      reopened.database.insert(updated)
      assertThat(reopened.database.podcasts.first()).isEqualTo(listOf(updated))
    } finally {
      platform.finishClose.complete(Unit)
      fixtures.asReversed().forEach { it.close() }
      FileSystem.SYSTEM.deleteRecursively(root)
    }
  }

  private class DatabaseFixture(
    parentScope: Scope,
    storageFactory: Storage.Factory,
    platform: DatabasePlatform,
    dispatcher: CoroutineDispatcher,
  ) {
    private val owner =
      CoroutineScopeScoped(dispatcher + SupervisorJob() + CoroutineName("database-test"))
    private val scope = parentScope.buildChild("database-test") { addCoroutineScopeScoped(owner) }

    val database: PodcastDatabase =
      UserPodcastDatabase(
          storage = storageFactory.create("user-alice", owner.createChild()),
          coroutineScope = owner.createChild(),
          platform = platform,
        )
        .also { scope.register(it) }

    fun destroy() {
      if (!scope.isDestroyed()) {
        scope.destroy()
      }
    }

    suspend fun close() {
      destroy()
      owner.coroutineContext.job.join()
    }
  }

  private class DelayedClosePlatform : DatabasePlatform {
    private val delegate: DatabasePlatform = PlatformDatabase()
    private var closing = false

    val closeStarted = CompletableDeferred<Unit>()
    val finishClose = CompletableDeferred<Unit>()
    var openedWhileClosing = false
      private set

    override suspend fun open(
      path: String?,
      coroutineContext: CoroutineContext,
    ): RoomPodcastDatabase {
      openedWhileClosing = openedWhileClosing || closing
      // SQLite connection timeouts must use real time while filesystem work is pending.
      return delegate.open(path, coroutineContext + Dispatchers.Default)
    }

    override suspend fun close(database: RoomPodcastDatabase) {
      closing = true
      closeStarted.complete(Unit)
      finishClose.await()
      delegate.close(database)
      closing = false
    }
  }
}
