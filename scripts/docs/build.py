"""Build the documentation with the web application and tested Android APK."""

import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys


def stage_android_apk(directory, destination):
    apks = list(directory.glob("*.apk"))
    if len(apks) != 1 or not apks[0].is_file() or apks[0].stat().st_size == 0:
        raise ValueError("Expected exactly one nonempty tested APK in --android-apk-dir.")
    if apks[0].resolve().is_relative_to(destination.resolve()):
        raise ValueError("The Android APK must be outside docs/downloads.")
    try:
        metadata = json.loads((directory / "output-metadata.json").read_text())
    except (OSError, ValueError) as error:
        raise ValueError("Expected output-metadata.json from the tested APK build.") from error
    elements = metadata.get("elements") if isinstance(metadata, dict) else None
    if (
        not isinstance(elements, list)
        or len(elements) != 1
        or not isinstance(elements[0], dict)
        or elements[0].get("outputFile") != apks[0].name
    ):
        raise ValueError("APK metadata must describe exactly the tested APK.")
    version_name = elements[0].get("versionName")
    version_code = elements[0].get("versionCode")
    if (
        not isinstance(version_name, str)
        or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]*", version_name)
        or type(version_code) is not int
        or version_code <= 0
    ):
        raise ValueError("APK metadata must contain a filename-safe versionName and positive versionCode.")
    filename = f"storymile-{version_name}-{version_code}.apk"
    if destination.exists():
        shutil.rmtree(destination)
    destination.mkdir(parents=True)
    shutil.copyfile(apks[0], destination / filename)


def main():
    root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--android-apk-dir",
        type=Path,
        required=True,
        help="Directory containing the tested android-release CI artifact's APK and output-metadata.json.",
    )
    parser.add_argument(
        "--web-dist",
        type=Path,
        default=root / "app/web/build/dist/wasmJs/productionExecutable",
        help="Production Wasm distribution to embed in the documentation.",
    )
    args = parser.parse_args()
    distribution = args.web_dist.resolve()
    required = ("index.html", "storymile.js")
    if not all((distribution / name).is_file() for name in required) or not list(
        distribution.glob("*.wasm")
    ):
        parser.error(
            "The production web distribution is missing. Build "
            ":app:web:wasmJsBrowserDistribution with "
            "-Pstorymile.enableWasm=true --no-isolated-projects first."
        )

    destination = root / "docs/web"
    if distribution == destination or destination in distribution.parents:
        parser.error("The web distribution must be outside docs/web.")
    try:
        stage_android_apk(args.android_apk_dir, root / "docs/downloads")
    except ValueError as error:
        parser.error(str(error))
    if destination.exists():
        shutil.rmtree(destination)
    shutil.copytree(distribution, destination)
    subprocess.run(
        [sys.executable, "-m", "mkdocs", "build", "--strict"],
        cwd=root,
        check=True,
    )


if __name__ == "__main__":
    main()
