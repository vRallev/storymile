package software.ralf.storymile.util

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout

@OptIn(ExperimentalCoroutinesApi::class)
class StateFlowMapTest {
  @Test
  fun `a StateFlow can be mapped to another StateFlow`() = runTest {
    val original = MutableStateFlow(1)
    val mapped = original.mapStateFlow { it.toString() }

    assertThat(mapped.value).isEqualTo("1")

    original.value = 2
    assertThat(mapped.value).isEqualTo("2")

    mapped.test {
      assertThat(awaitItem()).isEqualTo("2")

      original.value = 3
      assertThat(awaitItem()).isEqualTo("3")
    }
  }

  @Test
  fun `the mapper function is called only once per element`() = runTest {
    var count = 0

    val original = MutableStateFlow(1)
    val mapped = original.mapStateFlow {
      count++
      it.toString()
    }

    assertThat(count).isEqualTo(0)

    assertThat(mapped.value).isEqualTo("1")
    assertThat(mapped.value).isEqualTo("1")
    assertThat(count).isEqualTo(1)

    original.value = 2
    assertThat(mapped.value).isEqualTo("2")
    assertThat(mapped.value).isEqualTo("2")
    assertThat(count).isEqualTo(2)

    mapped.test {
      assertThat(awaitItem()).isEqualTo("2")

      original.value = 1
      assertThat(awaitItem()).isEqualTo("1")
    }

    assertThat(mapped.value).isEqualTo("1")
    assertThat(count).isEqualTo(3)
  }

  @Test
  fun `the replay cache shares the current mapping with value and collectors`() = runTest {
    var count = 0
    val original = MutableStateFlow<Int?>(null)
    val mapped = original.mapStateFlow {
      count++
      it?.toString()
    }

    assertThat(mapped.replayCache).isEqualTo(listOf(null))
    assertThat(mapped.value).isNull()
    assertThat(count).isEqualTo(1)

    original.value = 2
    assertThat(mapped.replayCache).isEqualTo(listOf("2"))
    assertThat(mapped.value).isEqualTo("2")
    assertThat(count).isEqualTo(2)

    mapped.test {
      assertThat(awaitItem()).isEqualTo("2")
      assertThat(count).isEqualTo(2)

      original.value = null
      assertThat(awaitItem()).isNull()
      assertThat(mapped.replayCache).isEqualTo(listOf(null))
      assertThat(mapped.value).isNull()
      assertThat(count).isEqualTo(3)
    }
  }

  @Test
  fun `a StateFlow can be mapped using a reduce function`() = runTest {
    val original = MutableStateFlow(1)
    val mapped = original.mapStateFlow { 1 }

    mapped.test {
      assertThat(awaitItem()).isEqualTo(1)

      original.value = 3
      ensureAllEventsConsumed()
    }
  }

  @Test
  fun `a StateFlow can be mapped to null values`() = runTest {
    val original = MutableStateFlow(2)
    val mapped = original.mapStateFlow { if (it.mod(2) == 0) it else null }

    mapped.test {
      assertThat(awaitItem()).isEqualTo(2)

      original.value = 4
      assertThat(awaitItem()).isEqualTo(4)

      original.value = 5
      assertThat(awaitItem()).isNull()
    }
  }

  @Test
  fun `a StateFlow can be mapped to non-null values`() = runTest {
    val original = MutableStateFlow<Int?>(null)
    val mapped = original.mapStateFlow { it ?: -1 }

    assertThat(mapped.value).isEqualTo(-1)

    mapped.test {
      assertThat(awaitItem()).isEqualTo(-1)

      original.value = 2
      assertThat(awaitItem()).isEqualTo(2)
    }
  }

  @Test
  fun `a StateFlow with nullable values can be mapped to a StateFlow with nullable values`() =
    runTest {
      val original = MutableStateFlow<Int?>(null)
      val mapped = original.mapStateFlow { if (it == null) null else it * 2 }

      assertThat(mapped.value).isEqualTo(null)

      mapped.test {
        assertThat(awaitItem()).isEqualTo(null)

        original.value = 2
        assertThat(awaitItem()).isEqualTo(4)
      }
    }

  @Test
  fun `mapping a StateFlow is lazy`() = runTest {
    val original = MutableStateFlow(1)

    var called = 0
    val mapped = original.mapStateFlow {
      called++
      it.toString()
    }

    assertThat(called).isEqualTo(0)

    assertThat(mapped.value).isEqualTo("1")
    assertThat(called).isEqualTo(1)

    mapped.test {
      assertThat(awaitItem()).isEqualTo("1")
      assertThat(called).isEqualTo(1)

      original.value = 2
      assertThat(awaitItem()).isEqualTo("2")
      assertThat(called).isEqualTo(2)
    }
  }

  @Test
  fun `mapping a StateFlow allows parallel collect calls`() = runTest {
    val original = MutableStateFlow(0)
    val mapped = original.mapStateFlow { it.toString() }

    val deferredList =
      List(200) {
        async(UnconfinedTestDispatcher()) { mapped.take(5).toList().reduce { acc, s -> acc + s } }
      }

    repeat(4) { original.value = it + 1 }

    deferredList.forEach { deferred ->
      assertThat(withTimeout(1.seconds) { deferred.await() }).isEqualTo("01234")
    }
  }

  @Test
  fun `mapping to the same value will not emit a new value`() = runTest {
    val original = MutableStateFlow(0)

    var called = 0
    val mapped = original.mapStateFlow {
      called++
      "test"
    }

    assertThat(mapped.value).isEqualTo("test")
    assertThat(called).isEqualTo(1)

    original.value = 1
    assertThat(mapped.value).isEqualTo("test")
    assertThat(called).isEqualTo(2)

    mapped.test {
      assertThat(awaitItem()).isEqualTo("test")
      assertThat(called).isEqualTo(2)

      original.value = 2
      original.value = 3
      original.value = 4

      expectNoEvents()
      assertThat(called).isEqualTo(5)
    }
  }
}
