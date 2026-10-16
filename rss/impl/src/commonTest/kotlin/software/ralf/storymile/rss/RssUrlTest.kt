package software.ralf.storymile.rss

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test

class RssUrlTest {
  @Test
  fun `URLs require an explicit host and cannot contain credentials`() {
    listOf(
        "https://",
        "https://?x=1",
        "https://#x",
        "http://",
        "http://?x=1",
        "https:///a",
        "https:feed.xml",
        "https://user@host.test/",
        "https://:pass@host.test/",
      )
      .forEach { url -> assertThat(RssUrl.normalize(url), name = url).isNull() }
  }

  @Test
  fun `relative URLs follow path and query reference rules`() {
    val base = "https://feeds.test/a/show.xml?old=1#old"
    mapOf(
        "next.xml" to "https://feeds.test/a/next.xml",
        "../next.xml" to "https://feeds.test/next.xml",
        "/next.xml" to "https://feeds.test/next.xml",
        "?new=2" to "https://feeds.test/a/show.xml?new=2",
        "?" to "https://feeds.test/a/show.xml?",
        "#new" to "https://feeds.test/a/show.xml?old=1",
        "//cdn.test" to "https://cdn.test",
        "//cdn.test/next.xml" to "https://cdn.test/next.xml",
        "https://cdn.test/next.xml" to "https://cdn.test/next.xml",
        "https://cdn.test/next.xml?new=2" to "https://cdn.test/next.xml?new=2",
      )
      .forEach { (reference, expected) ->
        assertThat(RssUrl.resolve(base, reference), name = reference).isEqualTo(expected)
      }
  }

  @Test
  fun `dot paths remove one preceding segment including empty segments`() {
    mapOf(
        "/a/./b" to "/a/b",
        "/a/../b" to "/b",
        "/a//../b" to "/a/b",
        "/a/../../b" to "/b",
        "/a/." to "/a/",
        "/a/b/.." to "/a/",
      )
      .forEach { (path, expected) ->
        assertThat(RssUrl.normalize("https://feeds.test$path"), name = path)
          .isEqualTo("https://feeds.test$expected")
      }
  }

  @Test
  fun `encoded paths and query values stay encoded`() {
    assertThat(
        RssUrl.resolve("https://feeds.test/a/show.xml?old=1", "../a%2Fb.xml?value=a%26b&value=c"),
      )
      .isEqualTo("https://feeds.test/a%2Fb.xml?value=a%26b&value=c")
  }
}
