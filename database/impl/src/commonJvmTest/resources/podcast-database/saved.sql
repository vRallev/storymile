-- Room schema exported at 1eea1a6b.
PRAGMA user_version = 1;
CREATE TABLE IF NOT EXISTS `shows` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `feedUrl` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT, `author` TEXT, `artworkUrl` TEXT, `link` TEXT, `isFollowed` INTEGER NOT NULL DEFAULT 0);
CREATE UNIQUE INDEX IF NOT EXISTS `index_shows_feedUrl` ON `shows` (`feedUrl`);
CREATE TABLE IF NOT EXISTS `episodes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `showId` INTEGER NOT NULL, `sourceEpisodeId` TEXT NOT NULL, `feedPosition` INTEGER, `title` TEXT NOT NULL, `description` TEXT, `author` TEXT, `artworkUrl` TEXT, `publishedAt` TEXT, `durationMillis` INTEGER, `url` TEXT NOT NULL, `type` TEXT, `length` INTEGER, FOREIGN KEY(`showId`) REFERENCES `shows`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );
CREATE INDEX IF NOT EXISTS `index_episodes_showId_sourceEpisodeId` ON `episodes` (`showId`, `sourceEpisodeId`);
CREATE TABLE IF NOT EXISTS `queued_episodes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `episodeId` INTEGER NOT NULL, `position` INTEGER NOT NULL, FOREIGN KEY(`episodeId`) REFERENCES `episodes`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT );
CREATE UNIQUE INDEX IF NOT EXISTS `index_queued_episodes_episodeId` ON `queued_episodes` (`episodeId`);
CREATE INDEX IF NOT EXISTS `index_queued_episodes_position` ON `queued_episodes` (`position`);
CREATE TABLE IF NOT EXISTS `saved_episodes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `episodeId` INTEGER NOT NULL, FOREIGN KEY(`episodeId`) REFERENCES `episodes`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT );
CREATE UNIQUE INDEX IF NOT EXISTS `index_saved_episodes_episodeId` ON `saved_episodes` (`episodeId`);
CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)
INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '2812528fc5a8da29d30fb76e659d7ff8')
INSERT INTO shows (id, feedUrl, title, description, author, artworkUrl, link, isFollowed) VALUES (42, 'https://feeds.test/show.xml', 'A show', '<p>Show metadata</p>', 'Publisher', 'https://images.test/show.png', 'https://shows.test/show', 1);
INSERT INTO episodes (id, showId, sourceEpisodeId, feedPosition, title, description, author, artworkUrl, publishedAt, durationMillis, url, type, length) VALUES (48, 42, 'second', 0, 'Latest episode', '<p>Episode metadata</p>', 'Episode author', 'https://images.test/episode.png', 'Tue, 20 Oct 2026 12:00:00 GMT', 123456, 'https://media.test/second.mp3', 'audio/mpeg', 6000000000);
INSERT INTO episodes (id, showId, sourceEpisodeId, feedPosition, title, description, author, artworkUrl, publishedAt, durationMillis, url, type, length) VALUES (51, 42, 'first', 1, 'First episode', NULL, NULL, NULL, NULL, NULL, 'https://media.test/first.mp3', NULL, NULL);
INSERT INTO shows (id, feedUrl, title, artworkUrl) VALUES (44, 'https://feeds.test/queue.xml', 'Queue-only show', 'https://images.test/queue.png');
INSERT INTO episodes (id, showId, sourceEpisodeId, title, url) VALUES (52, 44, 'first', 'First episode', 'https://media.test/first.mp3');
INSERT INTO queued_episodes (id, episodeId, position) VALUES (55, 48, 9), (56, 52, 4);
INSERT INTO saved_episodes (id, episodeId) VALUES (57, 52);
