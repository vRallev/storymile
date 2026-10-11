# Test episodes

Fake mode seeds the `Storymile test episodes` playlist with `test-episode-1`,
`test-episode-2`, and `test-episode-3`, in that order. Each ID matches an MP3 filename in
[`playback` resources](../../playback/impl/src/commonMain/composeResources/files/test-episodes).
The recordings use synthetic speech. The player does not play audio yet.

To regenerate the recordings on macOS, install FFmpeg and run:

```sh
./scripts/test-episodes/generate.sh
```

The script uses the adjacent transcripts and the Samantha voice at 165 words per minute.
It creates mono MP3 files at 64 kbit/s.
