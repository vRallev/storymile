package software.ralf.storymile.discovery

/** A browsable topic in the discovery catalog. */
enum class DiscoveryTopic(
  /** The catalog search term for this topic. */
  val searchTerm: String,
  /** Filters offered within the topic. */
  val filters: List<Filter>,
) {
  /** Nature and its subtopics. */
  NATURE("Nature", listOf(Filter.ALL_NATURE, Filter.OCEANS, Filter.OUTDOORS)),
  /** Science and discovery. */
  SCIENCE("Science", emptyList()),
  /** Arts and culture. */
  CULTURE("Culture", emptyList()),
  /** Technology and ideas. */
  TECHNOLOGY("Technology", emptyList());

  /** Refines the search within a topic. */
  enum class Filter(
    /** The term to append to the topic search. Empty for the unfiltered topic. */
    val searchTerm: String,
  ) {
    /** All nature results. */
    ALL_NATURE(""),
    /** Ocean results. */
    OCEANS("Oceans"),
    /** Outdoors results. */
    OUTDOORS("Outdoors"),
  }
}
