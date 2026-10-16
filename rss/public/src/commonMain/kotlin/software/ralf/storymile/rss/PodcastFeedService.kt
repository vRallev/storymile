package software.ralf.storymile.rss

import software.ralf.storymile.http.HttpResponse

/** Loads show details and episodes from RSS, or local samples in fake mode. */
interface PodcastFeedService {
  /**
   * Loads an RSS 2.0 feed from [feedUrl], following up to five HTTP redirects. Returns HTTP,
   * transport, and decoding failures; coroutine cancellation propagates. Redirects with no valid
   * HTTP(S) destination, HTTPS downgrades, loops, and redirects beyond the limit are returned to
   * the caller.
   *
   * Trims [feedUrl]. Throws [IllegalArgumentException] unless it is an absolute HTTP(S) URL without
   * credentials. Browser requests require the feed host to allow cross-origin access. Browsers can
   * hide redirects, which become [HttpResponse.Error.UnexpectedStatus] with status 0. Fake mode
   * serves the discovery sample feeds and returns HTTP 404 for other valid URLs.
   */
  suspend fun load(feedUrl: String): HttpResponse<PodcastFeed>
}
