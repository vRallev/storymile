package software.ralf.storymile.http

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import io.ktor.client.HttpClient as KtorClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.io.IOException
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.runTestWithScope

class KtorHttpClientTest {
  @Test
  fun `requests initialize the client on first use and reuse it`() = runTestWithScope { scope ->
    val factory =
      TestClientFactory(
        dispatcher = StandardTestDispatcher(testScheduler),
        handler = { respond("podcast") },
      )
    val client: HttpClient = KtorHttpClient(factory).also { scope.register(it) }

    assertThat(factory.createCount).isEqualTo(0)

    repeat(2) {
      assertThat(client.get("https://example.test/search") { it })
        .isEqualTo(HttpResponse.Success("podcast", statusCode = 200))
      assertThat(factory.createCount).isEqualTo(1)
    }
  }

  @Test
  fun `scope exit leaves an unused client uninitialized`() = runTestWithScope { scope ->
    val factory =
      TestClientFactory(
        dispatcher = StandardTestDispatcher(testScheduler),
        handler = { error("A closed client must not send requests.") },
      )
    val client: HttpClient = KtorHttpClient(factory).also { scope.register(it) }

    scope.destroy()

    assertThat(factory.createCount).isEqualTo(0)
    assertFailure { client.get("https://example.test/search") { it } }
      .isInstanceOf<IllegalStateException>()
    assertThat(factory.createCount).isEqualTo(0)
  }

  @Test
  fun `scope exit during initialization closes the new client`() = runTestWithScope { scope ->
    var clientJob: Job? = null
    val factory =
      TestClientFactory(
        dispatcher = StandardTestDispatcher(testScheduler),
        handler = { error("A closed client must not send requests.") },
        onCreate = { created ->
          clientJob = created.coroutineContext.job
          scope.destroy()
        },
      )
    val client: HttpClient = KtorHttpClient(factory).also { scope.register(it) }

    assertFailure { client.get("https://example.test/search") { it } }
      .isInstanceOf<CancellationException>()
    assertThat(factory.createCount).isEqualTo(1)
    assertThat(checkNotNull(clientJob).isCancelled).isTrue()
  }

  @Test
  fun `successful responses preserve status and decode even an empty body`() =
    runTestWithScope { scope ->
      val client: HttpClient =
        client(scope) { request ->
          val status = request.url.parameters["status"]!!.toInt()
          respond(
            content = if (status == 204) "" else "podcast",
            status = HttpStatusCode.fromValue(status),
          )
        }

      listOf(200, 201, 204, 299).forEach { status ->
        val response =
          client.get("https://example.test/search", mapOf("status" to "$status")) {
            "decoded:$it"
          }

        assertThat(response)
          .isEqualTo(
            HttpResponse.Success(
              data = if (status == 204) "decoded:" else "decoded:podcast",
              statusCode = status,
            ),
          )
      }
    }

  @Test
  fun `query parameters preserve reserved characters and unicode`() = runTestWithScope { scope ->
    val query = "science & news+space/宇宙?"
    val client: HttpClient =
      client(scope) { request ->
        assertThat(request.url.parameters["term"]).isEqualTo(query)
        assertThat(request.url.parameters["existing"]).isEqualTo("yes")
        respond("results")
      }

    val response =
      client.get("https://example.test/search?existing=yes", mapOf("term" to query)) {
        it
      }

    assertThat(response).isEqualTo(HttpResponse.Success("results", statusCode = 200))
  }

  @Test
  fun `redirect and error statuses do not decode or follow redirects`() =
    runTestWithScope { scope ->
      val expected =
        mapOf(
          0 to HttpResponse.Error.UnexpectedStatus(0),
          300 to HttpResponse.Redirect(300, "/moved"),
          302 to HttpResponse.Redirect(302, "/moved"),
          304 to HttpResponse.Redirect(304, "/moved"),
          399 to HttpResponse.Redirect(399, "/moved"),
          400 to HttpResponse.Error.Client(400),
          401 to HttpResponse.Error.Unauthenticated,
          403 to HttpResponse.Error.Client(403),
          429 to HttpResponse.Error.Client(429),
          499 to HttpResponse.Error.Client(499),
          500 to HttpResponse.Error.Server(500),
          503 to HttpResponse.Error.Server(503),
          599 to HttpResponse.Error.Server(599),
          199 to HttpResponse.Error.UnexpectedStatus(199),
          600 to HttpResponse.Error.UnexpectedStatus(600),
        )
      var requests = 0
      val client: HttpClient =
        client(scope) { request ->
          requests++
          respond(
            content = "must not decode",
            status = HttpStatusCode.fromValue(request.url.parameters["status"]!!.toInt()),
            headers = headersOf(HttpHeaders.Location, "/moved"),
          )
        }

      expected.forEach { (status, result) ->
        val response =
          client.get("https://example.test/search", mapOf("status" to "$status")) {
            error("Non-success response reached the decoder.")
          }

        assertThat(response).isEqualTo(result)
      }

      assertThat(requests).isEqualTo(expected.size)
      assertThat(HttpResponse.Error.Unauthenticated.statusCode).isEqualTo(401)
    }

  @Test
  fun `redirects can omit the location header`() = runTestWithScope { scope ->
    val client: HttpClient = client(scope) { respond("", HttpStatusCode.NotModified) }

    assertThat(client.get("https://example.test/search") { it })
      .isEqualTo(HttpResponse.Redirect(304))
  }

  @Test
  fun `invalid response data produces a decoding error`() = runTestWithScope { scope ->
    val client: HttpClient = client(scope) { respond("not a number") }

    val response = client.get("https://example.test/search") { it.toInt() }

    assertThat(response).isInstanceOf<HttpResponse.Error.Decoding>()
  }

  @Test
  fun `transport and timeout failures remain distinct`() = runTestWithScope { scope ->
    val failures =
      listOf(
        IOException("offline"),
        HttpRequestTimeoutException("https://example.test/search", 30_000),
        ConnectTimeoutException("connection timed out"),
        SocketTimeoutException("socket timed out"),
      )

    failures.forEach { failure ->
      val client: HttpClient = client(scope) { throw failure }
      val response = client.get("https://example.test/search") { it }

      if (
        failure is HttpRequestTimeoutException ||
          failure is ConnectTimeoutException ||
          failure is SocketTimeoutException
      ) {
        assertThat(response).isInstanceOf<HttpResponse.Error.Timeout>()
      } else {
        assertThat(response).isInstanceOf<HttpResponse.Error.Transport>()
      }
    }
  }

  @Test
  fun `a stalled request reaches the configured timeout`() = runTestWithScope { scope ->
    val client: HttpClient = client(scope) { awaitCancellation() }

    val response = client.get("https://example.test/search") { it }

    assertThat(response).isInstanceOf<HttpResponse.Error.Timeout>()
  }

  @Test
  fun `request and decoder cancellation propagate`() = runTestWithScope { scope ->
    val requestClient: HttpClient = client(scope) { throw CancellationException("request") }
    val decoderClient: HttpClient = client(scope) { respond("body") }
    val decoderCancellation = CancellationException("decoder")

    assertFailure { requestClient.get("https://example.test/search") { it } }
      .isInstanceOf<CancellationException>()
    assertFailure {
        decoderClient.get("https://example.test/search") { throw decoderCancellation }
      }
      .isSameInstanceAs(decoderCancellation)
  }

  @Test
  fun `programming errors from the engine or decoder propagate`() = runTestWithScope { scope ->
    val requestClient: HttpClient = client(scope) { error("engine bug") }
    val decoderClient: HttpClient = client(scope) { respond("body") }
    val decoderFailure = IllegalStateException("decoder bug")

    assertFailure { requestClient.get("https://example.test/search") { it } }
      .isInstanceOf<IllegalStateException>()
    assertFailure { decoderClient.get("https://example.test/search") { throw decoderFailure } }
      .isSameInstanceAs(decoderFailure)
  }

  @Test
  fun `scope exit cancels an active request and closes the client`() = runTestWithScope { scope ->
    val started = CompletableDeferred<Unit>()
    val client: HttpClient =
      client(scope) {
        started.complete(Unit)
        awaitCancellation()
      }
    val request = async { client.get("https://example.test/search") { it } }
    started.await()

    scope.destroy()

    assertFailure { request.await() }.isInstanceOf<CancellationException>()
    assertThat(request.isActive).isFalse()
    assertFailure { client.get("https://example.test/search") { it } }
      .isInstanceOf<IllegalStateException>()
  }

  private fun TestScope.client(scope: Scope, handler: MockRequestHandler): HttpClient =
    KtorHttpClient(TestClientFactory(StandardTestDispatcher(testScheduler), handler)).also {
      scope.register(it)
    }

  private class TestClientFactory(
    private val dispatcher: CoroutineDispatcher,
    private val handler: MockRequestHandler,
    private val onCreate: (KtorClient) -> Unit = {},
  ) : KtorClientFactory() {
    var createCount = 0
      private set

    override fun create(configure: HttpClientConfig<*>.() -> Unit): KtorClient {
      createCount++
      return KtorClient(MockEngine) {
          configure(this)
          engine {
            dispatcher = this@TestClientFactory.dispatcher
            addHandler(handler)
          }
        }
        .also { onCreate(it) }
    }
  }
}
