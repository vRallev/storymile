package software.ralf.storymile.playlist

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object PlaylistDocument {
  fun encode(playlists: List<Playlist>): ByteArray =
    Json.encodeToString(
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
      Json.parseToJsonElement(content.decodeToString(throwOnInvalidSequence = true)).jsonObject
    require(document.getValue("version").jsonPrimitive.int == 1) {
      "Unsupported playlist document version."
    }
    val playlists =
      document.getValue("playlists").jsonArray.map { element ->
        val playlist = element.jsonObject
        val id = PlaylistId(playlist.getValue("id").string())
        val name = PlaylistName(playlist.getValue("name").string())
        val episodes = playlist.getValue("episodeIds").jsonArray.map { EpisodeId(it.string()) }
        require(episodes.distinct().size == episodes.size) {
          "Saved playlist has duplicate episodes."
        }
        Playlist(id, name, episodes)
      }
    require(playlists.map { it.id }.distinct().size == playlists.size) {
      "Saved playlists have duplicate IDs."
    }
    return playlists
  }

  private fun JsonElement.string(): String {
    require(this is JsonPrimitive && isString) { "Expected a JSON string." }
    return content
  }

  @Serializable private data class Document(val version: Int, val playlists: List<Entry>)

  @Serializable
  private data class Entry(val id: String, val name: String, val episodeIds: List<String>)
}
