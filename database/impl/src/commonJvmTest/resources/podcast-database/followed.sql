-- Room schema exported at 8a9aa436.
PRAGMA user_version = 1;
CREATE TABLE IF NOT EXISTS `shows` (`feedUrl` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT, `author` TEXT, `artworkUrl` TEXT, `link` TEXT, PRIMARY KEY(`feedUrl`));
CREATE TABLE IF NOT EXISTS `episodes` (`feedUrl` TEXT NOT NULL, `id` TEXT NOT NULL, `position` INTEGER NOT NULL, `title` TEXT NOT NULL, `description` TEXT, `author` TEXT, `artworkUrl` TEXT, `publishedAt` TEXT, `durationMillis` INTEGER, `url` TEXT NOT NULL, `type` TEXT, `length` INTEGER, PRIMARY KEY(`feedUrl`, `id`), FOREIGN KEY(`feedUrl`) REFERENCES `shows`(`feedUrl`) ON UPDATE NO ACTION ON DELETE CASCADE );
CREATE INDEX IF NOT EXISTS `index_episodes_feedUrl` ON `episodes` (`feedUrl`);
CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)
INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'af8d1ed076aeac6ed69c02cc6f837b26')
INSERT INTO shows (feedUrl, title, description, author, artworkUrl, link) VALUES ('https://feeds.test/show.xml', 'A show', '<p>Show metadata</p>', 'Publisher', 'https://images.test/show.png', 'https://shows.test/show');
INSERT INTO episodes (feedUrl, id, position, title, description, author, artworkUrl, publishedAt, durationMillis, url, type, length) VALUES ('https://feeds.test/show.xml', 'second', 0, 'Latest episode', '<p>Episode metadata</p>', 'Episode author', 'https://images.test/episode.png', 'Tue, 20 Oct 2026 12:00:00 GMT', 123456, 'https://media.test/second.mp3', 'audio/mpeg', 6000000000);
INSERT INTO episodes (feedUrl, id, position, title, description, author, artworkUrl, publishedAt, durationMillis, url, type, length) VALUES ('https://feeds.test/show.xml', 'first', 1, 'First episode', NULL, NULL, NULL, NULL, NULL, 'https://media.test/first.mp3', NULL, NULL);
