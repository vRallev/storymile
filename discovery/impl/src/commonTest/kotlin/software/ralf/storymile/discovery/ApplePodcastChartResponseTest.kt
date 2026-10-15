package software.ralf.storymile.discovery

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import kotlin.test.Test

class ApplePodcastChartResponseTest {
  @Test
  fun `chart fields map to podcasts in ranking order and unknown fields are ignored`() {
    val body =
      """
      {"extra":true,"feed":{"title":"Top Shows","results":[
        {"id":"123","name":"Science Weekly","artistName":"A Publisher",
         "artworkUrl100":"https://example.invalid/artwork.jpg",
         "url":"https://example.invalid/podcast","genres":[{"name":"Science"}]},
        {"id":"12","name":"History Weekly"}
      ]}}
      """
        .trimIndent()

    assertThat(ApplePodcastChartResponse().decode(body))
      .isEqualTo(
        listOf(
          DiscoveredPodcast(
            "123",
            "Science Weekly",
            "A Publisher",
            null,
            "https://example.invalid/artwork.jpg",
            "https://example.invalid/podcast",
          ),
          DiscoveredPodcast("12", "History Weekly", null, null, null, null),
        ),
      )
  }

  @Test
  fun `optional chart fields may be absent null or blank`() {
    val body =
      """
      {"feed":{"results":[
        {"id":"1","name":"Absent"},
        {"id":"2","name":"Null","artistName":null,"artworkUrl100":null,"url":null},
        {"id":"3","name":"Blank","artistName":" ","artworkUrl100":"","url":" "}
      ]}}
      """
        .trimIndent()

    assertThat(ApplePodcastChartResponse().decode(body))
      .isEqualTo(
        listOf(
          DiscoveredPodcast("1", "Absent", null, null, null, null),
          DiscoveredPodcast("2", "Null", null, null, null, null),
          DiscoveredPodcast("3", "Blank", null, null, null, null),
        ),
      )
    assertThat(ApplePodcastChartResponse().decode("""{"feed":{"results":[]}}"""))
      .isEqualTo(emptyList())
  }

  @Test
  fun `malformed chart documents and incomplete podcasts fail decoding`() {
    val bodies =
      listOf(
        "not json",
        "{}",
        """{"feed":null}""",
        """{"feed":{}}""",
        """{"feed":{"results":null}}""",
        """{"feed":{"results":{}}}""",
        """{"feed":{"results":[{"name":"Missing ID"}]}}""",
        """{"feed":{"results":[{"id":null,"name":"Null ID"}]}}""",
        """{"feed":{"results":[{"id":"0","name":"Invalid ID"}]}}""",
        """{"feed":{"results":[{"id":"-1","name":"Negative ID"}]}}""",
        """{"feed":{"results":[{"id":"abc","name":"Non-numeric ID"}]}}""",
        """{"feed":{"results":[{"id":"1"}]}}""",
        """{"feed":{"results":[{"id":"1","name":null}]}}""",
        """{"feed":{"results":[{"id":"1","name":" "}]}}""",
        """{"feed":{"results":[{"id":"1","name":"Title","url":42}]}}""",
      )

    bodies.forEach { body ->
      assertFailure { ApplePodcastChartResponse().decode(body) }
        .isInstanceOf<IllegalArgumentException>()
    }
  }
}
