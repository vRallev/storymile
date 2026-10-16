package software.ralf.storymile.discovery

import software.ralf.storymile.http.HttpClient
import software.ralf.storymile.http.HttpResponse

internal class LocalHttpClient(
  private val body: String =
    """{"results":[{"collectionId":123,"collectionName":"Catalog Result"}]}""",
  private val error: HttpResponse.Error? = null,
) : HttpClient {
  var requestCount = 0
    private set

  var requestedUrl: String? = null
    private set

  var requestedParameters: Map<String, String> = emptyMap()
    private set

  override suspend fun <T : Any> get(
    url: String,
    parameters: Map<String, String>,
    decode: (String) -> T,
  ): HttpResponse<T> {
    requestCount++
    requestedUrl = url
    requestedParameters = parameters
    return error ?: HttpResponse.Success(decode(body), statusCode = 200)
  }
}
