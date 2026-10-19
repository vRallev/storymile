package software.ralf.storymile.podcast

import software.ralf.storymile.http.HttpClient
import software.ralf.storymile.http.HttpResponse

internal class LocalHttpClient(
  private val body: String =
    """
    <rss version="2.0"><channel>
      <title>Feed Result</title><description>A show about science.</description>
      <item><title>First episode</title><enclosure url="https://media.test/first.mp3" type="audio/mpeg" /></item>
    </channel></rss>
    """
      .trimIndent(),
  private val responses: Map<String, HttpResponse<Nothing>> = emptyMap(),
  private val failure: Throwable? = null,
) : HttpClient {
  val requestedUrls = mutableListOf<String>()

  override suspend fun <T : Any> get(
    url: String,
    parameters: Map<String, String>,
    decode: (String) -> T,
  ): HttpResponse<T> {
    requestedUrls += url
    failure?.let { throw it }
    responses[url]?.let {
      return it
    }
    return try {
      HttpResponse.Success(decode(body), statusCode = 200)
    } catch (exception: IllegalArgumentException) {
      HttpResponse.Error.Decoding(exception)
    }
  }
}
