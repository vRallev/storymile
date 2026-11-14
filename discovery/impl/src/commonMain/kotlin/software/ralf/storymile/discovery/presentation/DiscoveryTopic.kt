package software.ralf.storymile.discovery.presentation

import software.ralf.storymile.show.ShowOrigin

enum class DiscoveryTopic(val searchTerm: String, val filters: List<Filter>) {
  NATURE("Nature", listOf(Filter.ALL_NATURE, Filter.OCEANS, Filter.OUTDOORS)),
  SCIENCE("Science", emptyList()),
  CULTURE("Culture", emptyList()),
  TECHNOLOGY("Technology", emptyList());

  val showOrigin: ShowOrigin
    get() =
      when (this) {
        NATURE -> ShowOrigin.Nature
        SCIENCE -> ShowOrigin.Science
        CULTURE -> ShowOrigin.Culture
        TECHNOLOGY -> ShowOrigin.Technology
      }

  enum class Filter(val searchTerm: String) {
    ALL_NATURE(""),
    OCEANS("Oceans"),
    OUTDOORS("Outdoors"),
  }
}
