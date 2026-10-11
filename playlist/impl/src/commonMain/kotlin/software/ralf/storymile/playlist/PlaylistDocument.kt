package software.ralf.storymile.playlist

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

internal class PlaylistDocument {
  fun encode(playlists: List<Playlist>): ByteArray = buildJsonObject {
    put("version", 1)
    putJsonArray("playlists") {
      playlists.forEach { playlist ->
        add(
          buildJsonObject {
            put("id", playlist.id.value)
            put("name", playlist.name)
            putJsonArray("episodeIds") {
              playlist.episodeIds.forEach { add(it.value) }
            }
          },
        )
      }
    }
  }
    .toString()
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
        val name = playlist.getValue("name").string()
        require(name.isNotBlank() && name == name.trim()) {
          "Saved playlist name must be trimmed and contain text."
        }
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
}
