@file:OptIn(ExperimentalWasmJsInterop::class)

package software.ralf.storymile.player

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny

internal external interface BrowserAudioTransport : JsAny {
  fun load(source: String)

  fun play()

  fun pause()

  fun seekTo(seconds: Double)

  fun setSpeed(speed: Double)

  fun close()
}

@JsFun(
  """(onState) => {
    // Keep the element so its user-gesture permission survives episode changes.
    const audio = new Audio();
    audio.preload = 'auto';
    audio.preservesPitch = true;
    let source = null;
    let expectedSource = '';
    let closed = false;
    let generation = 0;
    let playRequest = 0;
    let playing = false;
    let pendingSeek = null;
    let error = null;
    const listeners = [];

    function isCurrent() {
      return !closed && source !== null && audio.currentSrc === expectedSource;
    }
    function emit() {
      if (closed) return;
      const current = isCurrent();
      const position = current && Number.isFinite(audio.currentTime) ? audio.currentTime : 0;
      const duration = current && Number.isFinite(audio.duration) ? audio.duration : 0;
      onState(source, Math.max(0, position), Math.max(0, duration),
        current && playing && !audio.paused && !audio.ended,
        current && !audio.paused && !audio.ended && error === null,
        current && audio.readyState >= 1, current && audio.ended, error);
    }
    function listen(name, callback) {
      audio.addEventListener(name, callback);
      listeners.push([name, callback]);
    }
    function seek() {
      if (!isCurrent() || audio.readyState < 1 || pendingSeek === null) return;
      const limit = Number.isFinite(audio.duration) ? audio.duration : pendingSeek;
      audio.currentTime = Math.max(0, Math.min(pendingSeek, limit));
      pendingSeek = null;
    }
    listen('loadedmetadata', () => {
      if (!isCurrent()) return;
      seek();
      emit();
    });
    for (const name of ['durationchange', 'timeupdate', 'seeked', 'canplay']) {
      listen(name, () => { if (isCurrent()) emit(); });
    }
    listen('playing', () => {
      if (!isCurrent() || audio.paused || audio.ended) return;
      playing = true;
      emit();
    });
    listen('pause', () => {
      if (!isCurrent() || !audio.paused) return;
      playing = false;
      emit();
    });
    listen('waiting', () => {
      if (!isCurrent() || audio.readyState >= 3) return;
      playing = false;
      emit();
    });
    listen('ended', () => {
      if (!isCurrent() || !audio.ended) return;
      playing = false;
      emit();
    });
    listen('error', () => {
      if (closed || audio.src !== expectedSource || !audio.error) return;
      playing = false;
      error = audio.error.message || 'Audio playback failed (code ' + audio.error.code + ').';
      emit();
    });
    return {
      load(value) {
        if (closed) return;
        generation++;
        playRequest++;
        playing = false;
        pendingSeek = null;
        error = null;
        source = value;
        expectedSource = new URL(value, document.baseURI).href;
        audio.pause();
        audio.src = value;
        audio.load();
        emit();
      },
      play() {
        if (closed || source === null) return;
        const currentGeneration = generation;
        const currentRequest = ++playRequest;
        error = null;
        function failed(failure) {
          if (closed || generation !== currentGeneration || playRequest !== currentRequest) return;
          playing = false;
          error = failure.message || 'Audio playback could not start.';
          emit();
        }
        // Calling play here keeps the browser's transient user activation.
        try { audio.play().catch(failed); } catch (failure) { failed(failure); }
        emit();
      },
      pause() {
        if (closed) return;
        playRequest++;
        audio.pause();
        playing = false;
        emit();
      },
      seekTo(seconds) {
        if (closed || source === null) return;
        pendingSeek = Math.max(0, seconds);
        seek();
        emit();
      },
      setSpeed(speed) {
        if (closed) return;
        audio.preservesPitch = true;
        audio.defaultPlaybackRate = speed;
        audio.playbackRate = speed;
      },
      close() {
        if (closed) return;
        closed = true;
        generation++;
        playRequest++;
        for (const [name, callback] of listeners) audio.removeEventListener(name, callback);
        audio.pause();
        audio.removeAttribute('src');
        audio.load();
      }
    };
  }""",
)
internal external fun createBrowserAudioTransport(
  onState: (String?, Double, Double, Boolean, Boolean, Boolean, Boolean, String?) -> Unit,
): BrowserAudioTransport
