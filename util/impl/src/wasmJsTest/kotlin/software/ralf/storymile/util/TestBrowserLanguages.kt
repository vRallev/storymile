@file:OptIn(ExperimentalWasmJsInterop::class)

package software.ralf.storymile.util

import kotlin.js.ExperimentalWasmJsInterop

@JsFun(
  """(languages, test) => {
    const descriptor = Object.getOwnPropertyDescriptor(navigator, 'languages');
    Object.defineProperty(navigator, 'languages', {
      configurable: true,
      value: languages.split(',')
    });
    try {
      test();
    } finally {
      if (descriptor) Object.defineProperty(navigator, 'languages', descriptor);
      else delete navigator.languages;
    }
  }""",
)
internal external fun withBrowserLanguages(languages: String, test: () -> Unit)
