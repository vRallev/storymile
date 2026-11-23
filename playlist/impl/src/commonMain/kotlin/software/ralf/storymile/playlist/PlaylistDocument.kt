package software.ralf.storymile.playlist

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal object PlaylistDocument {
  private val json = Json { ignoreUnknownKeys = true }

  fun encode(playlists: List<Playlist>): ByteArray =
    json
      .encodeToString(
        Document(
          version = 1,
          playlists =
            playlists.map { playlist ->
              Entry(
                id = playlist.id.value,
                name = playlist.name.value,
                episodeIds = playlist.episodeIds.map { it.value },
              )
            },
        ),
      )
      .encodeToByteArray()

  fun decode(content: ByteArray): List<Playlist> {
    val document =
      json.decodeFromString<Document>(content.decodeToString(throwOnInvalidSequence = true))
    require(document.version == 1) { "Unsupported playlist document version." }
    val ids = mutableSetOf<PlaylistId>()
    return document.playlists.map { entry ->
      val id = PlaylistId(entry.id)
      require(ids.add(id)) { "Saved playlists have duplicate IDs." }
      require(entry.episodeIds.toSet().size == entry.episodeIds.size) {
        "Saved playlist has duplicate episodes."
      }
      Playlist(
        id = id,
        name = PlaylistName(entry.name),
        episodeIds = entry.episodeIds.map { EpisodeId(it) },
      )
    }
  }

  @Serializable private data class Document(val version: Int, val playlists: List<Entry>)

  @Serializable
  private data class Entry(val id: String, val name: String, val episodeIds: List<String>)
}
