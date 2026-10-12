package software.ralf.storymile

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dev.zacsweers.metro.createGraphFactory
import kotlinx.browser.document
import org.jetbrains.compose.resources.configureWebResources
import org.w3c.dom.url.URL

/** Launches the browser application inside the document body. */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
  // Resolve resources against the document directory, including explicit index.html URLs.
  configureWebResources {
    resourcePathMapping { path -> URL(path, document.baseURI).href }
  }
  ComposeViewport(checkNotNull(document.body)) {
    val wasmJsApp = remember {
      WasmJsApp { createGraphFactory<WasmJsAppGraph.Factory>().create(it) }
    }

    DisposableEffect(wasmJsApp) { onDispose { wasmJsApp.destroy() } }
    wasmJsApp.renderTemplates()
  }
}
