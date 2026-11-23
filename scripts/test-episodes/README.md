# Test episodes

Fake mode is included by default. At launch in Fake mode, the app adds the sample episodes to **Library → Queue**.

Pass `-Pstorymile.enableFakeMode=false` to exclude Fake mode and its MP3s from the app.

| Episodes | Podcast | Narrator |
| --- | --- | --- |
| 1–3 | Small Science | Coral |
| 4–6 | History on Foot | Daniel |
| 7–11 | Design Notes | Karen |

Each `test-episode-{number}` ID matches an MP3 filename in
[`:podcast:impl-fake` resources](../../podcast/impl-fake/src/commonMain/composeResources/files/test-episodes).
Playback starts paused at 1× and plays the queue in order.
Speed changes preserve pitch and last only for the current session.

To regenerate episodes 1–3:

1. Open [ttsMP3 AI Voices](https://ttsmp3.com/ai).
2. Select **Coral (female, deeper voice)** with the default settings: speed **1.00**, instructions off.
3. Paste the matching `test-episode-{number}.txt` transcript.
4. Select **Download as MP3**.
5. Replace the matching resource with the download, named `test-episode-{number}.mp3`.

To regenerate a macOS recording, choose the settings for its narrator and run from the repository root:

```sh
number=4 voice=Daniel rate=160 gain=-6.5
# For Design Notes, use number=7 voice=Karen rate=135 gain=-10.
speech_dir=$(mktemp -d)
say -v "$voice" -r "$rate" -f "scripts/test-episodes/test-episode-$number.txt" -o "$speech_dir/episode.aiff"
ffmpeg -i "$speech_dir/episode.aiff" -af "volume=${gain}dB" -ar 24000 -ac 1 -b:a 128k \
  -write_xing 0 -id3v2_version 0 \
  "podcast/impl-fake/src/commonMain/composeResources/files/test-episodes/test-episode-$number.mp3"
```

All recordings use mono audio at 24 kHz and 128 kbit/s.
