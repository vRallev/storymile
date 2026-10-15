package software.ralf.storymile.discovery

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import kotlin.test.Test

class ApplePodcastSearchResponseTest {
  @Test
  fun `catalog fields map to podcasts and unknown fields are ignored`() {
    val body =
      """
      {"resultCount":1,"extra":true,"results":[{
        "collectionId":123,"collectionName":"Science Weekly","artistName":"A Publisher",
        "feedUrl":"https://example.invalid/feed.xml",
        "artworkUrl600":"https://example.invalid/large.jpg",
        "artworkUrl100":"https://example.invalid/small.jpg",
        "collectionViewUrl":"https://example.invalid/podcast","unknown":[1,2]
      }]}
      """
        .trimIndent()

    assertThat(ApplePodcastSearchResponse().decode(body))
      .isEqualTo(
        listOf(
          DiscoveredPodcast(
            "123",
            "Science Weekly",
            "A Publisher",
            "https://example.invalid/feed.xml",
            "https://example.invalid/large.jpg",
            "https://example.invalid/podcast",
          ),
        ),
      )
  }

  @Test
  fun `optional fields may be absent null or blank and small artwork is a fallback`() {
    val body =
      """
      {"results":[
        {"collectionId":1,"collectionName":"Minimal"},
        {"collectionId":2,"collectionName":"Fallback","artistName":null,"feedUrl":" ",
         "artworkUrl600":"","artworkUrl100":"https://example.invalid/small.jpg"}
      ]}
      """
        .trimIndent()

    assertThat(ApplePodcastSearchResponse().decode(body))
      .isEqualTo(
        listOf(
          DiscoveredPodcast("1", "Minimal", null, null, null, null),
          DiscoveredPodcast("2", "Fallback", null, null, "https://example.invalid/small.jpg", null),
        ),
      )
    assertThat(ApplePodcastSearchResponse().decode("""{"results":[]}""")).isEqualTo(emptyList())
  }

  @Test
  fun `malformed documents and incomplete podcasts fail decoding`() {
    val bodies =
      listOf(
        "not json",
        "{}",
        """{"results":null}""",
        """{"results":{}}""",
        """{"results":[{"collectionName":"Missing ID"}]}""",
        """{"results":[{"collectionId":null,"collectionName":"Null ID"}]}""",
        """{"results":[{"collectionId":0,"collectionName":"Invalid ID"}]}""",
        """{"results":[{"collectionId":-1,"collectionName":"Negative ID"}]}""",
        """{"results":[{"collectionId":1}]}""",
        """{"results":[{"collectionId":1,"collectionName":null}]}""",
        """{"results":[{"collectionId":1,"collectionName":" "}]}""",
        """{"results":[{"collectionId":1,"collectionName":"Title","feedUrl":42}]}""",
      )

    bodies.forEach { body ->
      assertFailure { ApplePodcastSearchResponse().decode(body) }
        .isInstanceOf<IllegalArgumentException>()
    }
  }
}
