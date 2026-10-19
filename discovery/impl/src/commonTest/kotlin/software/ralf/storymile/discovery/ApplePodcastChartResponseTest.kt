package software.ralf.storymile.discovery

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import kotlin.test.Test

class ApplePodcastChartResponseTest {
  @Test
  fun `chart thumbnails use the largest nonblank image and high resolution artwork`() {
    val body =
      """
      {"feed":{"entry":[{
        "id":{"attributes":{"im:id":"123"}},"im:name":{"label":"Science Weekly"},
        "im:image":[
          {"label":"https://is1-ssl.mzstatic.com/image/thumb/cover.jpg/170x170bb.png",
           "attributes":{"height":"170"}},
          {"label":"https://example.invalid/small.jpg","attributes":{"height":"55"}},
          {"label":" ","attributes":{"height":"600"}}
        ]
      }]}}
      """
        .trimIndent()

    assertThat(ApplePodcastChartResponse().decode(body).single().artworkUrl)
      .isEqualTo("https://is1-ssl.mzstatic.com/image/thumb/cover.jpg/1200x1200bb.png")
  }

  @Test
  fun `chart fields map to podcasts in ranking order and unknown fields are ignored`() {
    val body =
      """
      {"extra":true,"feed":{"title":{"label":"Top Podcasts"},"entry":[
        {"id":{"label":"https://example.invalid/podcast","attributes":{"im:id":"123"}},
         "im:name":{"label":"Science Weekly"},"im:artist":{"label":"A Publisher"},
         "im:image":[{"label":"https://example.invalid/artwork.jpg"}],
         "summary":{"label":"<p>Stories &amp; science.</p><p>Each week.</p>"},
         "category":{"attributes":{"label":"Science"}}},
        {"id":{"attributes":{"im:id":"12"}},"im:name":{"label":"History Weekly"}}
      ]}}
      """
        .trimIndent()

    assertThat(ApplePodcastChartResponse().decode(body))
      .isEqualTo(
        listOf(
          DiscoveredPodcast(
            id = "123",
            title = "Science Weekly",
            author = "A Publisher",
            feedUrl = null,
            artworkUrl = "https://example.invalid/artwork.jpg",
            catalogUrl = "https://example.invalid/podcast",
            description = "Stories & science.\n\nEach week.",
          ),
          DiscoveredPodcast("12", "History Weekly", null, null, null, null),
        ),
      )
  }

  @Test
  fun `optional chart fields may be absent null or blank`() {
    val body =
      """
      {"feed":{"entry":[
        {"id":{"attributes":{"im:id":"1"}},"im:name":{"label":"Absent"}},
        {"id":{"attributes":{"im:id":"2"},"label":null},"im:name":{"label":"Null"},
         "im:artist":null,"im:image":null,"summary":null},
        {"id":{"attributes":{"im:id":"3"},"label":" "},"im:name":{"label":"Blank"},
         "im:artist":{"label":" "},"im:image":[{"label":""}],"summary":{"label":" "}},
        {"id":{"attributes":{"im:id":"4"}},"im:name":{"label":"Empty"},"im:image":[],
         "summary":{"label":"<p>&nbsp;</p>"}}
      ]}}
      """
        .trimIndent()

    assertThat(ApplePodcastChartResponse().decode(body))
      .isEqualTo(
        listOf(
          DiscoveredPodcast("1", "Absent", null, null, null, null),
          DiscoveredPodcast("2", "Null", null, null, null, null),
          DiscoveredPodcast("3", "Blank", null, null, null, null),
          DiscoveredPodcast("4", "Empty", null, null, null, null),
        ),
      )
    assertThat(ApplePodcastChartResponse().decode("""{"feed":{"entry":[]}}"""))
      .isEqualTo(emptyList())
  }

  @Test
  fun `malformed chart documents fail decoding`() {
    val bodies =
      listOf(
        "not json",
        "{}",
        """{"feed":null}""",
        """{"feed":{}}""",
        """{"feed":{"entry":null}}""",
        """{"feed":{"entry":{}}}""",
      )

    bodies.forEach { body ->
      assertFailure { ApplePodcastChartResponse().decode(body) }
        .isInstanceOf<IllegalArgumentException>()
    }
  }

  @Test
  fun `incomplete or invalid chart entries fail decoding`() {
    val entries =
      listOf(
        """{"im:name":{"label":"Missing ID"}}""",
        """{"id":null,"im:name":{"label":"Null ID"}}""",
        """{"id":{"attributes":{}},"im:name":{"label":"Missing numeric ID"}}""",
        """{"id":{"attributes":{"im:id":"0"}},"im:name":{"label":"Invalid ID"}}""",
        """{"id":{"attributes":{"im:id":"-1"}},"im:name":{"label":"Negative ID"}}""",
        """{"id":{"attributes":{"im:id":"abc"}},"im:name":{"label":"Non-numeric ID"}}""",
        """{"id":{"attributes":{"im:id":"1"}}}""",
        """{"id":{"attributes":{"im:id":"1"}},"im:name":null}""",
        """{"id":{"attributes":{"im:id":"1"}},"im:name":{"label":" "}}""",
        """{"id":{"attributes":{"im:id":"1"}},"im:name":{"label":null}}""",
        """{"id":{"attributes":{"im:id":"1"}},"im:name":{"label":"Title"},"im:image":42}""",
      )

    entries.forEach { entry ->
      assertFailure { ApplePodcastChartResponse().decode("""{"feed":{"entry":[$entry]}}""") }
        .isInstanceOf<IllegalArgumentException>()
    }
  }
}
