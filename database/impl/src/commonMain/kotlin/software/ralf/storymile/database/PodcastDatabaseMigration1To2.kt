package software.ralf.storymile.database

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.async.executeSQL
import androidx.sqlite.async.prepare
import androidx.sqlite.async.step

internal object PodcastDatabaseMigration1To2 : Migration(startVersion = 1, endVersion = 2) {
  override suspend fun migrate(connection: SQLiteConnection) {
    // Several releases used version 1. Only the earliest ones used feed URLs as primary keys.
    val hasLocalIds =
      connection.prepare("SELECT 1 FROM pragma_table_info('shows') WHERE name = 'id'").use {
        it.step()
      }
    if (!hasLocalIds) {
      connection.migrateFeedUrlKeys()
    }

    connection.executeSQL(
      """
      CREATE TABLE IF NOT EXISTS saved_episodes (
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        episodeId INTEGER NOT NULL REFERENCES episodes(id) ON DELETE RESTRICT
      )
      """
        .trimIndent(),
    )
    connection.executeSQL(
      "CREATE UNIQUE INDEX IF NOT EXISTS index_saved_episodes_episodeId ON saved_episodes(episodeId)",
    )
    connection.executeSQL(
      "CREATE INDEX IF NOT EXISTS index_episodes_feedPosition_showId ON episodes(feedPosition, showId)",
    )
  }

  private suspend fun SQLiteConnection.migrateFeedUrlKeys() {
    val hasQueue =
      prepare("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'queued_episodes'").use {
        it.step()
      }
    executeSQL("ALTER TABLE shows RENAME TO legacy_shows")
    executeSQL("ALTER TABLE episodes RENAME TO legacy_episodes")
    executeSQL(
      """
      CREATE TABLE shows (
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        feedUrl TEXT NOT NULL,
        title TEXT NOT NULL,
        description TEXT,
        author TEXT,
        artworkUrl TEXT,
        link TEXT,
        isFollowed INTEGER NOT NULL DEFAULT 0
      )
      """
        .trimIndent(),
    )
    executeSQL("CREATE UNIQUE INDEX index_shows_feedUrl ON shows(feedUrl)")
    executeSQL(
      """
      CREATE TABLE episodes (
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        showId INTEGER NOT NULL REFERENCES shows(id) ON DELETE CASCADE,
        sourceEpisodeId TEXT NOT NULL,
        feedPosition INTEGER,
        title TEXT NOT NULL,
        description TEXT,
        author TEXT,
        artworkUrl TEXT,
        publishedAt TEXT,
        durationMillis INTEGER,
        url TEXT NOT NULL,
        type TEXT,
        length INTEGER
      )
      """
        .trimIndent(),
    )
    executeSQL(
      "CREATE INDEX index_episodes_showId_sourceEpisodeId ON episodes(showId, sourceEpisodeId)",
    )
    executeSQL(
      """
      INSERT INTO shows (feedUrl, title, description, author, artworkUrl, link, isFollowed)
      SELECT feedUrl, title, description, author, artworkUrl, link, 1 FROM legacy_shows
      """
        .trimIndent(),
    )
    executeSQL(
      """
      INSERT INTO episodes (
        showId, sourceEpisodeId, feedPosition, title, description, author, artworkUrl,
        publishedAt, durationMillis, url, type, length
      )
      SELECT shows.id, old.id, old.position, old.title, old.description, old.author, old.artworkUrl,
        old.publishedAt, old.durationMillis, old.url, old.type, old.length
      FROM legacy_episodes AS old JOIN shows ON shows.feedUrl = old.feedUrl
      """
        .trimIndent(),
    )

    if (hasQueue) {
      executeSQL("ALTER TABLE queued_episodes RENAME TO legacy_queue")
    }
    executeSQL(
      """
      CREATE TABLE queued_episodes (
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        episodeId INTEGER NOT NULL REFERENCES episodes(id) ON DELETE RESTRICT,
        position INTEGER NOT NULL
      )
      """
        .trimIndent(),
    )
    executeSQL("CREATE UNIQUE INDEX index_queued_episodes_episodeId ON queued_episodes(episodeId)")
    executeSQL("CREATE INDEX index_queued_episodes_position ON queued_episodes(position)")

    if (hasQueue) {
      migrateQueue()
      executeSQL("DROP TABLE legacy_queue")
    }
    executeSQL("DROP TABLE legacy_episodes")
    executeSQL("DROP TABLE legacy_shows")
  }

  private suspend fun SQLiteConnection.migrateQueue() {
    executeSQL(
      """
      INSERT OR IGNORE INTO shows (feedUrl, title, artworkUrl)
      SELECT queue.feedUrl, queue.podcastTitle, (
        SELECT fallback.artworkUrl FROM legacy_queue AS fallback
        WHERE fallback.feedUrl = queue.feedUrl
          AND fallback.episodeArtworkUrl IS NULL AND fallback.artworkUrl IS NOT NULL
        ORDER BY fallback.position LIMIT 1
      )
      FROM legacy_queue AS queue ORDER BY queue.position
      """
        .trimIndent(),
    )
    executeSQL(
      """
      INSERT INTO episodes (
        showId, sourceEpisodeId, title, description, author, artworkUrl,
        publishedAt, durationMillis, url, type, length
      )
      SELECT shows.id, queue.episodeId, queue.title, queue.description, queue.author,
        queue.episodeArtworkUrl, queue.publishedAt, queue.durationMillis, queue.url,
        queue.type, queue.length
      FROM legacy_queue AS queue JOIN shows ON shows.feedUrl = queue.feedUrl
      WHERE NOT EXISTS (
        SELECT 1 FROM episodes WHERE showId = shows.id AND sourceEpisodeId = queue.episodeId
      )
      """
        .trimIndent(),
    )
    executeSQL(
      """
      INSERT INTO queued_episodes (episodeId, position)
      SELECT episodes.id, queue.position
      FROM legacy_queue AS queue
      JOIN shows ON shows.feedUrl = queue.feedUrl
      JOIN episodes ON episodes.showId = shows.id AND episodes.sourceEpisodeId = queue.episodeId
      """
        .trimIndent(),
    )
  }
}
