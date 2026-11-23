package software.ralf.storymile.podcast

import software.ralf.storymile.http.HttpResponse

/** Loads show details and episodes from a feed URL. */
interface PodcastFeedService {
  /**
   * Loads an RSS 2.0 feed from [feedUrl], following up to five HTTP redirects. Returns HTTP,
   * transport, and decoding failures; coroutine cancellation propagates. Redirects with no valid
   * HTTP(S) destination, HTTPS downgrades, loops, and redirects beyond the limit are returned to
   * the caller.
   *
   * Trims [feedUrl]. Throws [IllegalArgumentException] unless it is an absolute HTTP(S) URL without
   * credentials. Browser requests require the feed host to allow cross-origin access. Browsers can
   * hide redirects, which become [HttpResponse.Error.UnexpectedStatus] with status 0.
   */
  suspend fun load(feedUrl: String): HttpResponse<PodcastFeed>
}
