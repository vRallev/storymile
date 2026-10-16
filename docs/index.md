---
hide:
  - navigation
  - toc
---

# Storymile

<div class="storymile-intro" markdown>

![Storymile](assets/icon.png){ .storymile-intro__icon }

A podcast app for Android, Desktop, iOS, and web, with support for custom generated
episodes in your library. Built with Kotlin Multiplatform and
[App Platform](https://github.com/vRallev/app-platform).

</div>

<section class="android-download" aria-label="Install Storymile on Android">
  <div>
    <h2>Take Storymile with you</h2>
    <p>Download the latest Android build from main. No Google Play needed.</p>
    <p><a class="md-button md-button--primary" href="{{ android_apk_url }}" download="storymile.apk">Install Android APK</a></p>
    <p class="android-download__help">Open the APK on your phone. If prompted, allow installs from your browser.</p>
    <a href="{{ android_build_url }}">Build details on GitHub ↗</a>
  </div>
  <figure>
    <a href="{{ android_apk_url }}" download="storymile.apk"><img src="assets/android-download.svg" alt="QR code to download the latest Android APK" width="225" height="225"></a>
    <figcaption>Scan to download on your phone</figcaption>
  </figure>
</section>

<div class="app-preview" data-app-preview>
  <div class="app-preview__toolbar">
    <div class="app-preview__presets" role="group" aria-label="App layout">
      <button class="app-preview__preset" type="button" data-preview-preset="phone" aria-pressed="true">Phone</button>
      <button class="app-preview__preset" type="button" data-preview-preset="tablet" aria-pressed="false">Tablet</button>
      <button class="app-preview__preset" type="button" data-preview-preset="desktop" aria-pressed="false">Desktop</button>
    </div>
    <output class="app-preview__size" data-preview-size aria-live="polite" aria-atomic="true">480 × 840</output>
    <a class="app-preview__open" href="web/index.html" target="_blank" rel="noopener">Open app <span aria-hidden="true">↗</span></a>
  </div>
  <div class="app-preview__stage" data-preview-stage>
    <div class="app-preview__container" data-preview-container>
      <iframe class="app-preview__frame" title="Storymile app" src="web/index.html" allow="autoplay; fullscreen"></iframe>
      <div class="app-preview__handle" data-preview-handle tabindex="0" role="separator" aria-label="Resize app width" aria-orientation="vertical" aria-valuemin="320" aria-valuemax="1440" aria-valuenow="480" aria-valuetext="480 pixels wide"></div>
    </div>
  </div>
</div>
