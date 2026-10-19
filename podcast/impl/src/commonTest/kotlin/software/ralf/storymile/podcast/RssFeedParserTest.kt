package software.ralf.storymile.podcast

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class RssFeedParserTest {
  private val parser = RssFeedParser()
  private val feedUrl = "https://example.invalid/podcasts/feed.xml"

  @Test
  fun `RSS and iTunes metadata decode with namespace aliases CDATA and entities`() {
    val xml =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <rss version="2.0" xmlns:apple="http://www.itunes.com/dtds/podcast-1.0.dtd"
           xmlns:full="http://purl.org/rss/1.0/modules/content/">
        <channel>
          <title>Science &amp; Stories &#x1F680;</title>
          <description><![CDATA[<p>A show about <b>science</b> & discovery.</p>]]></description>
          <apple:author>The Studio</apple:author>
          <apple:image href="../art/show.jpg"/>
          <link>https://example.invalid/show?one=1&amp;two=2</link>
          <item>
            <title> First &quot;episode&quot; </title>
            <guid isPermaLink="false"> episode-1 </guid>
            <full:encoded><![CDATA[<p>The <a href="https://example.invalid">first</a> story.</p>]]></full:encoded>
            <apple:author>Guest</apple:author>
            <apple:image href="https://example.invalid/episode.jpg"/>
            <pubDate>Tue, 13 Oct 2026 08:30:00 -0700</pubDate>
            <apple:duration>1:02:03</apple:duration>
            <enclosure url="audio/one.mp3?token=a&amp;part=b" type="audio/mpeg" length="12345"/>
          </item>
        </channel>
      </rss>
      """
        .trimIndent()

    assertThat(parser.decode(xml, feedUrl))
      .isEqualTo(
        PodcastFeed(
          feedUrl = feedUrl,
          title = "Science & Stories 🚀",
          description = "<p>A show about <b>science</b> & discovery.</p>",
          author = "The Studio",
          artworkUrl = "https://example.invalid/art/show.jpg",
          link = "https://example.invalid/show?one=1&two=2",
          episodes =
            listOf(
              PodcastEpisode(
                id = "episode-1",
                title = "First \"episode\"",
                description = "<p>The <a href=\"https://example.invalid\">first</a> story.</p>",
                author = "Guest",
                artworkUrl = "https://example.invalid/episode.jpg",
                publishedAt = "Tue, 13 Oct 2026 08:30:00 -0700",
                duration = 3723.seconds,
                enclosure =
                  PodcastEnclosure(
                    "https://example.invalid/podcasts/audio/one.mp3?token=a&part=b",
                    "audio/mpeg",
                    12345,
                  ),
              ),
            ),
        ),
      )
  }

  @Test
  fun `show metadata after episodes supplies fallbacks and feed order is preserved`() {
    val xml =
      """
      <rss version="2.0" xmlns:i="http://www.itunes.com/dtds/podcast-1.0.dtd">
        <channel>
          <item><title>Older first</title><enclosure url="one.mp3"/></item>
          <item><title>Newer second</title><enclosure url="two.mp3"/></item>
          <title>Show</title>
          <i:summary>Show summary</i:summary>
          <managingEditor>editor@example.invalid</managingEditor>
          <image><title>Artwork title</title><url>/show.png</url></image>
        </channel>
      </rss>
      """
        .trimIndent()

    val feed = parser.decode(xml, feedUrl)

    assertThat(feed.title).isEqualTo("Show")
    assertThat(feed.description).isEqualTo("Show summary")
    assertThat(feed.episodes.map { it.title }).isEqualTo(listOf("Older first", "Newer second"))
    assertThat(feed.episodes.map { it.author })
      .isEqualTo(listOf("editor@example.invalid", "editor@example.invalid"))
    assertThat(feed.episodes.map { it.artworkUrl })
      .isEqualTo(listOf("https://example.invalid/show.png", "https://example.invalid/show.png"))
    assertThat(feed.episodes.map { it.id })
      .isEqualTo(
        listOf(
          "https://example.invalid/podcasts/one.mp3",
          "https://example.invalid/podcasts/two.mp3",
        ),
      )
  }

  @Test
  fun `repeated links and enclosures are consumed before reading later episodes`() {
    val xml =
      """
      <rss version="2.0"><channel><title>Show</title>
        <link>https://example.invalid/first</link>
        <link>https://example.invalid/second</link>
        <item><title>One</title><enclosure url="one.mp3"/><enclosure url="ignored.mp3"/></item>
        <item><title>Two</title><enclosure url="two.mp3"/></item>
      </channel></rss>
      """
        .trimIndent()

    val feed = parser.decode(xml, feedUrl)

    assertThat(feed.link).isEqualTo("https://example.invalid/first")
    assertThat(feed.episodes.map { it.title }).isEqualTo(listOf("One", "Two"))
    assertThat(feed.episodes.map { it.enclosure.url })
      .isEqualTo(
        listOf(
          "https://example.invalid/podcasts/one.mp3",
          "https://example.invalid/podcasts/two.mp3",
        ),
      )
  }

  @Test
  fun `description content and summary are used in priority order`() {
    val xml =
      """
      <rss version="2.0" xmlns:i="http://www.itunes.com/dtds/podcast-1.0.dtd"
           xmlns:c="http://purl.org/rss/1.0/modules/content/">
        <channel>
          <title>Show</title><description> </description><c:encoded>Content</c:encoded>
          <i:summary>Summary</i:summary>
          <item><title>One</title><enclosure url="one.mp3"/>
            <i:summary>Summary one</i:summary><c:encoded>Content one</c:encoded>
            <description>&lt;p&gt;Description &amp;amp; details&lt;/p&gt;</description>
          </item>
          <item><title>Two</title><enclosure url="two.mp3"/>
            <description/><c:encoded/><i:summary>Summary two</i:summary>
          </item>
        </channel>
      </rss>
      """
        .trimIndent()

    val feed = parser.decode(xml, feedUrl)

    assertThat(feed.description).isEqualTo("Content")
    assertThat(feed.episodes.map { it.description })
      .isEqualTo(listOf("<p>Description &amp; details</p>", "Summary two"))
  }

  @Test
  fun `foreign and nested extension names cannot replace RSS or iTunes fields`() {
    val xml =
      """
      <rss version="2.0" xmlns:itunes="https://unrelated.invalid/namespace">
        <channel>
          <title>Show</title><itunes:title>Wrong show</itunes:title>
          <itunes:image href="https://example.invalid/wrong.jpg"/>
          <itunes:author>Wrong author</itunes:author>
          <itunes:item><title>Wrong item</title><enclosure url="wrong.mp3"/></itunes:item>
          <itunes:extension><title>Nested title</title><item><title>Nested item</title></item></itunes:extension>
          <item><title>Episode</title><enclosure url="one.mp3"/>
            <itunes:duration>123</itunes:duration><itunes:summary>Wrong description</itunes:summary>
          </item>
        </channel>
      </rss>
      """
        .trimIndent()

    val feed = parser.decode(xml, feedUrl)

    assertThat(feed.title).isEqualTo("Show")
    assertThat(feed.author).isEqualTo(null)
    assertThat(feed.artworkUrl).isEqualTo(null)
    assertThat(feed.episodes.map { it.title }).isEqualTo(listOf("Episode"))
    assertThat(feed.episodes.single().description).isEqualTo(null)
    assertThat(feed.episodes.single().duration).isEqualTo(null)
  }

  @Test
  fun `unplayable items are skipped and duplicate IDs keep the first valid item`() {
    val xml =
      """
      <rss version="2.0"><channel><title>Show</title>
        <item><title>Missing media</title></item>
        <item><enclosure url="untitled.mp3"/></item>
        <item><title> </title><enclosure url="blank-title.mp3"/></item>
        <item><title>Local</title><enclosure url="file:///tmp/audio.mp3"/></item>
        <item><title>Invalid</title><enclosure url="https://"/></item>
        <item><title>First valid</title><guid>same</guid>
          <enclosure url="javascript:alert(1)"/>
          <enclosure url="https://user:password@example.invalid/private.mp3"/>
          <enclosure url="first.mp3" length="-1"/>
          <enclosure url="ignored.mp3"/>
        </item>
        <item><title>Duplicate ID</title><guid>same</guid><enclosure url="second.mp3"/></item>
        <item><title>Fallback ID</title><enclosure url="third.mp3" length="not-a-number"/></item>
        <item><title>Duplicate fallback ID</title><enclosure url="third.mp3"/></item>
      </channel></rss>
      """
        .trimIndent()

    val episodes = parser.decode(xml, feedUrl).episodes

    assertThat(episodes.map { it.title }).isEqualTo(listOf("First valid", "Fallback ID"))
    assertThat(episodes.map { it.id })
      .isEqualTo(listOf("same", "https://example.invalid/podcasts/third.mp3"))
    assertThat(episodes.first().enclosure)
      .isEqualTo(PodcastEnclosure("https://example.invalid/podcasts/first.mp3", null, null))
    assertThat(episodes.last().enclosure.length).isEqualTo(null)
  }

  @Test
  fun `duration accepts seconds minutes and hours but ignores malformed values`() {
    val durations =
      listOf(
        "0" to 0.seconds,
        " 123 " to 123.seconds,
        "02:03" to 123.seconds,
        "120:03" to 7203.seconds,
        "1:02:03" to 3723.seconds,
        "" to null,
        "-1" to null,
        "1.5" to null,
        "1:60" to null,
        "1::2" to null,
        "1:00:00:00" to null,
        "abc" to null,
        "9223372036854775807:01" to null,
        "999999999999999999999" to null,
      )

    durations.forEach { (value, expected) ->
      val xml =
        """
        <rss version="2.0" xmlns:i="http://www.itunes.com/dtds/podcast-1.0.dtd">
          <channel><title>Show</title><item><title>Episode</title><enclosure url="one.mp3"/>
            <i:duration>$value</i:duration>
          </item></channel>
        </rss>
        """
          .trimIndent()

      assertThat(parser.decode(xml, feedUrl).episodes.single().duration, value).isEqualTo(expected)
    }
  }

  @Test
  fun `RSS image is a fallback for invalid iTunes artwork and metadata may be absent`() {
    val xml =
      """
      <rss version="2.0" xmlns:i="http://www.itunes.com/dtds/podcast-1.0.dtd">
        <channel><title>Show</title><link>file:///tmp/index.html</link>
          <image><url>/fallback.jpg</url></image><i:image href="data:image/png;base64,abc"/>
        </channel>
      </rss>
      """
        .trimIndent()

    assertThat(parser.decode(xml, feedUrl))
      .isEqualTo(
        PodcastFeed(
          feedUrl,
          "Show",
          null,
          null,
          "https://example.invalid/fallback.jpg",
          null,
          emptyList(),
        ),
      )
  }

  @Test
  fun `invalid documents including malformed suffixes fail decoding`() {
    val valid = """<rss version="2.0"><channel><title>Show</title></channel></rss>"""
    val documents =
      listOf(
        "",
        "not xml",
        "<html><title>Server error</title></html>",
        "<rss><channel><title>Show</title></channel></rss>",
        """<rss version="2.0"/>""",
        """<rss version="2.0"><channel/></rss>""",
        """<rss version="2.0"><channel><title> </title></channel></rss>""",
        """<rss version="2.0"><channel><title>Show</title></rss>""",
        """<rss version="2.0"><channel><title>Show</title>""",
        """<rss version="2.0"><channel><title>A & B</title></channel></rss>""",
        """<rss version="2.0"><channel><title>&unknown;</title></channel></rss>""",
        """<rss version="2.0"><channel><title>Show</title></channel><channel/></rss>""",
        """<rss version="2.0" xmlns="https://unrelated.invalid"><channel><title>Show</title></channel></rss>""",
        "$valid trailing text",
        "$valid<another/>",
        "$valid<!-- unclosed",
      )

    documents.forEach { xml ->
      assertFailure { parser.decode(xml, feedUrl) }
        .transform(name = xml) { it }
        .isInstanceOf<IllegalArgumentException>()
    }
  }

  @Test
  fun `DTDs and entity declarations are rejected before XML parsing`() {
    val declarations =
      listOf(
        """<!DOCTYPE rss SYSTEM "https://example.invalid/external.dtd">""",
        """<!DOCTYPE rss [<!ENTITY secret SYSTEM "file:///etc/passwd">]>""",
        """<!DOCTYPE rss [<!ENTITY % first "large"><!ENTITY % second "%first;%first;">]>""",
        """<!DOCTYPE rss [<!ENTITY x "&x;">]>""",
      )

    declarations.forEach { declaration ->
      val xml =
        """<?xml version="1.0"?>$declaration<rss version="2.0"><channel><title>Show</title></channel></rss>"""

      assertFailure { parser.decode(xml, feedUrl) }.isInstanceOf<IllegalArgumentException>()
    }
  }

  @Test
  fun `DOCTYPE text inside comments CDATA and processing instructions is harmless`() {
    val xml =
      """
      <?xml version="1.0"?>
      <!-- <!DOCTYPE rss> -->
      <?example <!DOCTYPE rss>?>
      <rss version="2.0"><channel><title>Show</title>
        <description><![CDATA[How <!DOCTYPE html> works]]></description>
      </channel></rss>
      """
        .trimIndent()

    assertThat(parser.decode(xml, feedUrl).description).isEqualTo("How <!DOCTYPE html> works")
  }

  @Test
  fun `deeply nested extensions fail before exhausting the call stack`() {
    val xml =
      """<rss version="2.0"><channel><title>Show</title>${"<extension>".repeat(65)}${"</extension>".repeat(65)}</channel></rss>"""

    assertFailure { parser.decode(xml, feedUrl) }.isInstanceOf<IllegalArgumentException>()
  }
}
