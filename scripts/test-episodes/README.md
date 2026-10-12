# Test episodes

Fake mode seeds the `Storymile test episodes` playlist with `test-episode-1`,
`test-episode-2`, and `test-episode-3`, in that order. Each ID matches an MP3 filename in
[`playback` resources](../../playback/impl/src/commonMain/composeResources/files/test-episodes).
The recordings use the ttsMP3 Coral AI voice. The player does not play audio yet.

To regenerate each recording:

1. Open [ttsMP3 AI Voices](https://ttsmp3.com/ai).
2. Select **Coral (female, deeper voice)** with the default settings: speed **1.00**, instructions off.
3. Paste the matching `test-episode-{number}.txt` transcript.
4. Select **Download as MP3**.
5. Replace the matching resource with the download, named `test-episode-{number}.mp3`.

The original downloads use mono audio at 24 kHz and 128 kbit/s.
