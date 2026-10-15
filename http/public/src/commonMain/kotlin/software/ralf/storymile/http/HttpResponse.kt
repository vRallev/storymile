package software.ralf.storymile.http

/** An HTTP result or an expected failure while receiving or decoding it. */
sealed interface HttpResponse<out T : Any> {
  /** A decoded 2xx response. */
  data class Success<T : Any>(
    /** The value decoded from the response body. */
    val data: T,
    /** The received HTTP status. */
    val statusCode: Int,
  ) : HttpResponse<T>

  /** A 3xx response surfaced by the platform. */
  data class Redirect(
    /** The received HTTP status. */
    val statusCode: Int,
    /** The Location header, which can be absent or relative. */
    val location: String? = null,
  ) : HttpResponse<Nothing>

  /** A failure without decoded response data. */
  sealed interface Error : HttpResponse<Nothing> {
    /** An unsuccessful HTTP status. */
    sealed interface Http : Error {
      /** The received HTTP status. */
      val statusCode: Int
    }

    /** The server requires valid credentials. */
    data object Unauthenticated : Http {
      override val statusCode: Int = 401
    }

    /** A 4xx response other than 401, including forbidden and rate-limited requests. */
    data class Client(override val statusCode: Int) : Http

    /** A 5xx response. */
    data class Server(override val statusCode: Int) : Http

    /** A final status outside the supported 2xx through 5xx ranges. */
    data class UnexpectedStatus(override val statusCode: Int) : Http

    /** A failure while connecting or receiving the response. */
    data class Transport(
      /** The original transport failure. */
      val cause: Throwable,
    ) : Error

    /** A request, connection, or socket timeout. Caller cancellation is not a timeout result. */
    data class Timeout(
      /** The original timeout failure. */
      val cause: Throwable,
    ) : Error

    /** A successful response whose body does not match the expected data. */
    data class Decoding(
      /** The original decoder failure. */
      val cause: IllegalArgumentException,
    ) : Error
  }
}
