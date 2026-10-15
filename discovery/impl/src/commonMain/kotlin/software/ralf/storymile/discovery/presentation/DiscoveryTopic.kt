package software.ralf.storymile.discovery.presentation

enum class DiscoveryTopic(val searchTerm: String, val filters: List<Filter>) {
  NATURE("Nature", listOf(Filter.ALL_NATURE, Filter.OCEANS, Filter.OUTDOORS)),
  SCIENCE("Science", emptyList()),
  CULTURE("Culture", emptyList()),
  TECHNOLOGY("Technology", emptyList());

  enum class Filter(val searchTerm: String) {
    ALL_NATURE(""),
    OCEANS("Oceans"),
    OUTDOORS("Outdoors"),
  }
}
