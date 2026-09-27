#!/usr/bin/env python3
"""Regenerate gallery previews with Python 3 and Pillow; keep original mocks intact."""

import json
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from PIL import Image


def main():
    root = Path(__file__).resolve().parent
    data_path = root / "data.js"
    data = json.loads(data_path.read_text().split("=", 1)[1].strip().removesuffix(";"))
    (root / "previews").mkdir(exist_ok=True)

    def generate(screen):
        preview = Path("previews") / (Path(screen["file"]).stem + ".webp")
        thumbnail = preview.with_stem(preview.stem + "-small")
        with Image.open(root / screen["file"]) as image:
            image.thumbnail((1280, 720), Image.Resampling.LANCZOS)
            image.convert("RGB").save(root / preview, "WEBP", quality=86, method=6)
            image.thumbnail((640, 360), Image.Resampling.LANCZOS)
            image.convert("RGB").save(root / thumbnail, "WEBP", quality=86, method=6)
        screen["preview"] = preview.as_posix()
        screen["thumbnail"] = thumbnail.as_posix()

    with ThreadPoolExecutor(max_workers=4) as executor:
        list(executor.map(generate, data["screens"]))

    data_path.write_text("window.STORYMILE_DESIGN = " + json.dumps(data, indent=2, ensure_ascii=False) + ";\n")
    print(f"Generated two preview sizes for {len(data['screens'])} screens.")


if __name__ == "__main__":
    main()
