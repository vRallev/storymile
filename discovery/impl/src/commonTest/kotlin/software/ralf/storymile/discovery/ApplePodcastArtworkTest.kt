package software.ralf.storymile.discovery

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class ApplePodcastArtworkTest {
  @Test
  fun `larger artwork preserves the asset path format and URL parameters`() {
    val url =
      "https://is1-ssl.mzstatic.com/image/thumb/Podcasts/100x100/cover.jpg/100x100bb.png?token=100x100#artwork"

    assertThat(ApplePodcastArtwork.highResolutionUrl(url))
      .isEqualTo(
        "https://is1-ssl.mzstatic.com/image/thumb/Podcasts/100x100/cover.jpg/1200x1200bb.png?token=100x100#artwork",
      )
  }

  @Test
  fun `large artwork and unrecognized URLs are preserved`() {
    listOf(
        "https://is1-ssl.mzstatic.com/image/thumb/cover/1200x1200bb.jpg",
        "https://is1-ssl.mzstatic.com/image/thumb/cover/3000x3000bb.jpg",
        "https://example.invalid/100x100bb.png",
        "https://mzstatic.com.example.invalid/100x100bb.png",
        "https://notmzstatic.com/100x100bb.png",
        "https://is1-ssl.mzstatic.com/image/thumb/cover/100x200bb.jpg",
        "https://is1-ssl.mzstatic.com/image/thumb/cover/0x0bb.jpg",
        "https://is1-ssl.mzstatic.com/image/thumb/cover/9999999999x9999999999bb.jpg",
        "https://is1-ssl.mzstatic.com/image/thumb/cover/original.jpg",
        "https://is1-ssl.mzstatic.com/image/thumb/cover/100x100-unknown.jpg",
        "not a URL",
      )
      .forEach { url -> assertThat(ApplePodcastArtwork.highResolutionUrl(url)).isEqualTo(url) }
  }
}
