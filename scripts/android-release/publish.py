"""Publish a tested main APK. The workflow serializes calls to this script."""

from hashlib import sha256
import json
import os
from pathlib import Path
import re
import sys
from urllib.error import HTTPError
from urllib.request import Request, urlopen


class GitHub:
    def __init__(self, token):
        self.token = token

    def request(self, method, path, data=None):
        binary = isinstance(data, bytes)
        host = "https://uploads.github.com" if binary else "https://api.github.com"
        request = Request(
            host + path,
            method=method,
            data=data if binary or data is None else json.dumps(data).encode(),
            headers={
                "Authorization": f"Bearer {self.token}",
                "Accept": "application/vnd.github+json",
                "Content-Type": "application/vnd.android.package-archive" if binary else "application/json",
                "X-GitHub-Api-Version": "2022-11-28",
            },
        )
        with urlopen(request, timeout=120) as response:
            body = response.read()
            return json.loads(body) if body else None


def publish(api, repository, commit, run_number, apk):
    if not 0 < run_number <= 2_100_000_000:
        raise ValueError("Run number must be a valid Android version code")
    base = f"/repos/{repository}"
    tag = f"android-main-{run_number}"
    releases = []
    page = 1
    while True:
        batch = api.request("GET", f"{base}/releases?per_page=100&page={page}")
        releases.extend(batch)
        if len(batch) < 100:
            break
        page += 1

    for release in releases:
        match = re.fullmatch(r"android-main-(\d+)", release["tag_name"])
        if match and not release["draft"] and int(match[1]) >= run_number:
            print(f"Main build {match[1]} is already published; skipping {run_number}.")
            return

    # Do not attach an APK to a pre-existing tag for a different source commit.
    try:
        ref = api.request("GET", f"{base}/git/ref/tags/{tag}")
    except HTTPError as error:
        if error.code != 404:
            raise
    else:
        if ref["object"]["type"] != "commit" or ref["object"]["sha"] != commit:
            raise ValueError(f"Tag {tag} does not point to the build commit")

    body = (
        "Android build from main. Download **storymile.apk** below.\n\n"
        f"Version code: `{run_number}`\n\n"
        f"Source: [{commit}](https://github.com/{repository}/commit/{commit})\n\n"
        f"APK SHA-256: `{sha256(apk).hexdigest()}`"
    )
    release = next((item for item in releases if item["tag_name"] == tag), None)
    if release is None:
        release = api.request("POST", f"{base}/releases", {
            "tag_name": tag,
            "target_commitish": commit,
            "name": f"Main build {run_number}",
            "draft": True,
            "prerelease": False,
            "body": body,
        })
    elif release["target_commitish"] != commit:
        raise ValueError(f"Draft {tag} belongs to a different source commit")

    release_path = f"{base}/releases/{release['id']}"
    # Only drafts are replaced on retries. The public latest APK remains available.
    for asset in release["assets"]:
        if asset["name"] == "storymile.apk":
            api.request("DELETE", f"{base}/releases/assets/{asset['id']}")
    asset = api.request("POST", f"{release_path}/assets?name=storymile.apk", apk)
    if asset["state"] != "uploaded" or asset["size"] != len(apk):
        raise ValueError("APK upload is incomplete; leaving the release as a draft")
    # Refresh the checksum when recovering a draft from a previous run attempt.
    api.request("PATCH", release_path, {
        "draft": False, "make_latest": "true", "body": body,
    })
    print(f"Published https://github.com/{repository}/releases/tag/{tag}")


if __name__ == "__main__":
    if os.environ.get("GITHUB_REF") != "refs/heads/main":
        raise SystemExit("Only main builds may publish Android releases")
    apks = list(Path(sys.argv[1]).glob("*.apk"))
    if len(apks) != 1:
        raise SystemExit("Expected exactly one tested release APK")
    publish(
        GitHub(os.environ["GH_TOKEN"]),
        os.environ["GITHUB_REPOSITORY"],
        os.environ["GITHUB_SHA"],
        int(os.environ["GITHUB_RUN_NUMBER"]),
        apks[0].read_bytes(),
    )
