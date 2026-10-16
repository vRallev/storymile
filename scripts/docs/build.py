"""Build the documentation with the web application and tested Android APK."""

import argparse
from pathlib import Path
import shutil
import subprocess
import sys


def stage_android_apk(directory, destination):
    apks = list(directory.glob("*.apk"))
    if len(apks) != 1 or not apks[0].is_file() or apks[0].stat().st_size == 0:
        raise ValueError("Expected exactly one nonempty tested APK in --android-apk-dir.")
    if apks[0].resolve().is_relative_to(destination.resolve()):
        raise ValueError("The Android APK must be outside docs/downloads.")
    if destination.exists():
        shutil.rmtree(destination)
    destination.mkdir(parents=True)
    shutil.copyfile(apks[0], destination / "storymile.apk")


def main():
    root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--android-apk-dir",
        type=Path,
        required=True,
        help="Directory containing the tested android-release CI artifact's APK.",
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
