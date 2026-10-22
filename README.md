# PR 140 Desktop comparison

Before: `96925c34f54449a58f99bc8848d24e074fd87091`.
After: `7c6bf7948d7811ff5bd4347eeef8a7c1d08e5582`.

Each filename identifies the viewport and state. Native window captures use matching light-theme fake data and font scale 1.0, except files marked `font150` (1.5). Viewport dimensions exclude native window decorations.

Files marked `player-motion` use the Desktop Compose UI test renderer at half expansion and after reversing to quarter expansion. Both versions use the same drag positions and frozen test clock.

The temporary capture tools are outside the application checkout. These image assets do not change application code.
