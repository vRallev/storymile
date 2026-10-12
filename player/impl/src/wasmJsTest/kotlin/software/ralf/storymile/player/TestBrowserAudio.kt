@file:OptIn(ExperimentalWasmJsInterop::class)

package software.ralf.storymile.player

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny

internal external interface TestBrowserAudio : JsAny {
  val playCount: Int
  val elementCount: Int
  val listenerCount: Int
  val speed: Double
  val preservesPitch: Boolean

  fun prepare(duration: Double)

  fun startPlaying()

  fun waitForData()

  fun nativePause()

  fun advance(position: Double)

  fun rejectPlay(index: Int, message: String)

  fun finish()

  fun dispatchEnded()

  fun restore()
}

@JsFun(
  """() => {
    const originalAudio = globalThis.Audio;
    const rejections = [];
    let audio;
    let elementCount = 0;
    class TestAudio {
      constructor() {
        audio = this;
        elementCount++;
        this.listeners = new Map();
        this.src = '';
        this.currentSrc = '';
        this.currentTime = 0;
        this.duration = NaN;
        this.readyState = 0;
        this.paused = true;
        this.ended = false;
        this.playbackRate = 1;
        this.defaultPlaybackRate = 1;
        this.preservesPitch = false;
      }
      addEventListener(name, callback) { this.listeners.set(name, callback); }
      removeEventListener(name) { this.listeners.delete(name); }
      dispatch(name) { this.listeners.get(name)?.(); }
      load() {
        this.currentSrc = '';
        this.currentTime = 0;
        this.duration = NaN;
        this.readyState = 0;
        this.ended = false;
        this.playbackRate = this.defaultPlaybackRate;
      }
      play() {
        this.paused = false;
        return { catch(callback) { rejections.push(callback); } };
      }
      pause() {
        this.paused = true;
        this.dispatch('pause');
      }
      removeAttribute(name) { if (name === 'src') this.src = ''; }
    }
    globalThis.Audio = TestAudio;
    return {
      get playCount() { return rejections.length; },
      get elementCount() { return elementCount; },
      get listenerCount() { return audio.listeners.size; },
      get speed() { return audio.playbackRate; },
      get preservesPitch() { return audio.preservesPitch; },
      prepare(duration) {
        audio.currentSrc = new URL(audio.src, document.baseURI).href;
        audio.duration = duration;
        audio.readyState = 4;
        audio.dispatch('loadedmetadata');
      },
      startPlaying() { audio.dispatch('playing'); },
      waitForData() {
        audio.readyState = 2;
        audio.dispatch('waiting');
      },
      nativePause() { audio.pause(); },
      advance(position) {
        audio.currentTime = position;
        audio.dispatch('timeupdate');
      },
      rejectPlay(index, message) { rejections[index](new Error(message)); },
      finish() {
        audio.currentTime = audio.duration;
        audio.paused = true;
        audio.ended = true;
        audio.dispatch('ended');
      },
      dispatchEnded() { audio.dispatch('ended'); },
      restore() { globalThis.Audio = originalAudio; }
    };
  }""",
)
internal external fun installTestBrowserAudio(): TestBrowserAudio
