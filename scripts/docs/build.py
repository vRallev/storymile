"""Build the documentation with the production web application."""

import argparse
from pathlib import Path
import shutil
import subprocess
import sys


def main():
    root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description=__doc__)
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
