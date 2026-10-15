package software.ralf.storymile.http

/** Makes HTTP requests and reports expected failures as [HttpResponse] values. */
interface HttpClient {
  /**
   * Sends a GET with encoded query [parameters]. Calls [decode] only for a 2xx response.
   *
   * [decode] must throw [IllegalArgumentException] for invalid response data. These failures become
   * [HttpResponse.Error.Decoding]. Cancellation and other programming errors propagate. Redirects
   * are not followed. Browsers can hide their status and headers; these responses become
   * [HttpResponse.Error.UnexpectedStatus] with status 0.
   */
  suspend fun <T : Any> get(
    url: String,
    parameters: Map<String, String> = emptyMap(),
    decode: (String) -> T,
  ): HttpResponse<T>
}
