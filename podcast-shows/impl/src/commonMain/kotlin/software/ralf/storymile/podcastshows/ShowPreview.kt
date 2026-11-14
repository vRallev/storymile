package software.ralf.storymile.podcastshows

data class ShowPreview(
  val id: String,
  val title: String,
  val author: String?,
  val artworkUrl: String?,
  val description: String? = null,
)
