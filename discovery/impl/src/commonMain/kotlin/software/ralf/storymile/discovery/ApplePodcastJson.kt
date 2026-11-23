package software.ralf.storymile.discovery

import kotlinx.serialization.json.Json

internal val applePodcastJson = Json { ignoreUnknownKeys = true }

internal fun String?.nonBlank(): String? = this?.takeIf { it.isNotBlank() }
