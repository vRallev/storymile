package software.ralf.storymile.discovery

internal object ApplePodcastArtwork {
  private val resizedArtwork =
    Regex(
      """^(https?://(?:[a-z0-9-]+\.)*mzstatic\.com/[^?#]*/)(\d+)x(\d+)(bb\.(?:jpg|jpeg|png|webp))([?#].*)?$""",
      RegexOption.IGNORE_CASE,
    )

  fun highResolutionUrl(url: String): String {
    val match = resizedArtwork.matchEntire(url) ?: return url
    val width = match.groupValues[2].toIntOrNull() ?: return url
    val height = match.groupValues[3].toIntOrNull() ?: return url
    if (width != height || width <= 0 || width >= 1200) {
      return url
    }

    // Request enough detail for cards and high-density previews.
    return "${match.groupValues[1]}1200x1200${match.groupValues[4]}${match.groupValues[5]}"
  }
}
