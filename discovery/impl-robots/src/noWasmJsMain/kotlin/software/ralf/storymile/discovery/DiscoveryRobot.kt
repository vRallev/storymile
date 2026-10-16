package software.ralf.storymile.discovery

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import dev.zacsweers.metro.AppScope
import software.ralf.app.platform.inject.robot.ContributesRobot
import software.ralf.app.platform.robot.ComposeRobot
import software.ralf.storymile.util.runOnUiThread

/** Drives discovery through its rendered controls and a local catalog. */
@ContributesRobot(AppScope::class)
class DiscoveryRobot(private val service: TestPodcastDiscoveryService) : ComposeRobot() {
  /** Opens browsing from Home, optionally failing its request to exercise retry. */
  fun discover(failRequest: Boolean = false) {
    if (failRequest) {
      service.failNextRequest()
    }
    request("", requireRequest = failRequest) {
      runOnUiThread(compose) { compose.onNodeWithTag("home-discover").performClick() }
    }
  }

  /** Opens discovery through Home's search field. */
  fun openSearch() {
    request("") { runOnUiThread(compose) { compose.onNodeWithTag("home-search").performClick() } }
  }

  /** Opens a hardcoded topic from browsing. */
  fun selectNature() {
    request("Nature", expectedFilter = "discovery-filter-OCEANS" to "Oceans") {
      scrollCatalogTo("discovery-topic-NATURE")
      compose.onNodeWithTag("discovery-topic-NATURE").assertTextEquals("Nature")
      runOnUiThread(compose) { compose.onNodeWithTag("discovery-topic-NATURE").performClick() }
    }
  }

  /** Enters a query through the renderer's text field. */
  fun search(query: String) {
    compose.waitUntilCatching("Search field displayed") {
      compose.onNodeWithTag("discovery-search").assertExists()
    }
    request(query.trim()) {
      scrollCatalogTo("discovery-search")
      runOnUiThread(compose) {
        compose.onNodeWithTag("discovery-search").performTextReplacement(query)
      }
    }
  }

  /** Checks that the presenter's restored text is visible in the renderer. */
  fun seeQuery(query: String) {
    compose.waitUntilCatching("Search field restored") {
      compose.onNodeWithTag("discovery-search").assertExists()
    }
    scrollCatalogTo("discovery-search")
    compose
      .onNodeWithTag("discovery-search")
      .assert(
        SemanticsMatcher("Editable text is $query") {
          it.config[SemanticsProperties.EditableText].text == query
        },
      )
  }

  /** Clears the query through the visible clear button. */
  fun clearSearch() {
    request("") {
      scrollCatalogTo("discovery-clear")
      runOnUiThread(compose) { compose.onNodeWithTag("discovery-clear").performClick() }
    }
  }

  /** Checks a catalog result after its request completes. */
  fun seePodcast(id: String) {
    compose.waitUntilCatching("Podcast $id displayed") {
      val catalog =
        compose.onAllNodes(hasTestTag("discovery-list")).fetchSemanticsNodes().isNotEmpty()
      runOnUiThread(compose) {
        compose
          .onNodeWithTag(if (catalog) "discovery-list" else "show-list")
          .performScrollToNode(hasTestTag("discovery-podcast-$id"))
      }
      compose.onNodeWithTag("discovery-podcast-$id").assertIsDisplayed()
    }
  }

  /** Opens a catalog result or selects a different show in the wide list. */
  fun openPodcast(id: String) {
    seePodcast(id)
    runOnUiThread(compose) { compose.onNodeWithTag("discovery-podcast-$id").performClick() }
  }

  /** Returns to the previous catalog or Home screen. */
  fun backFromCatalog() {
    scrollCatalogTo("discovery-back")
    compose.onNodeWithTag("discovery-back").assertIsDisplayed()
    runOnUiThread(compose) { compose.onNodeWithTag("discovery-back").performClick() }
  }

  /** Checks the empty-results state after the request completes. */
  fun seeEmptyResults() {
    compose.waitUntilCatching("Empty search results displayed") {
      scrollCatalogTo("discovery-empty")
      compose.onNodeWithTag("discovery-empty").assertIsDisplayed()
    }
  }

  /** Retries a failed catalog request through the visible error state. */
  fun retry() {
    request(requireNotNull(service.lastQuery), requireRequest = true) {
      scrollCatalogTo("discovery-retry")
      runOnUiThread(compose) { compose.onNodeWithTag("discovery-retry").performClick() }
    }
  }

  private fun scrollCatalogTo(tag: String) {
    runOnUiThread(compose) {
      if (tag in listOf("discovery-search", "discovery-clear", "discovery-back")) {
        compose.onNodeWithTag("discovery-list").performScrollToIndex(0)
      } else {
        compose.onNodeWithTag("discovery-list").performScrollToNode(hasTestTag(tag))
      }
    }
  }

  private fun request(
    query: String,
    expectedFilter: Pair<String, String>? = null,
    requireRequest: Boolean = query.isNotEmpty(),
    action: () -> Unit,
  ) {
    val previousRequestCount = service.completedRequestCount
    action()
    compose.waitUntilCatching("Catalog results for $query displayed") {
      compose.onNodeWithTag("discovery-screen").assertIsDisplayed()
      if (requireRequest) {
        assertThat(service.completedRequestCount).isGreaterThan(previousRequestCount)
        assertThat(service.lastQuery).isEqualTo(query)
      }
      if (query.isEmpty()) {
        compose
          .onNodeWithTag("discovery-search")
          .assert(
            SemanticsMatcher("Search field is empty") {
              it.config[SemanticsProperties.EditableText].text.isEmpty()
            },
          )
      }
      compose.onNodeWithTag("discovery-loading").assertDoesNotExist()
      expectedFilter?.let { (tag, label) -> compose.onNodeWithTag(tag).assertTextEquals(label) }
    }
  }
}
