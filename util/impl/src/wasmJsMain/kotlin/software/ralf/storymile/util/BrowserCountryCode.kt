@file:OptIn(ExperimentalWasmJsInterop::class)

package software.ralf.storymile.util

import kotlin.js.ExperimentalWasmJsInterop

@JsFun(
  """() => {
    const languages = navigator.languages.length ? navigator.languages : [navigator.language];
    const locales = languages.map(language => new Intl.Locale(language));
    const explicit = locales.find(locale => /^[A-Z]{2}${'$'}/.test(locale.region ?? ''));
    if (explicit) return explicit.region;
    return locales[0]?.maximize().region ?? null;
  }""",
)
internal external fun browserCountryCode(): String?
