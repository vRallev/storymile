import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from build import stage_android_apk


class AndroidPackagingTest(unittest.TestCase):
    def setUp(self):
        self.temporary = TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.source = self.root / "artifact"
        self.source.mkdir()
        self.destination = self.root / "docs/downloads"
        self.destination.mkdir(parents=True)
        (self.destination / "old.apk").write_bytes(b"previous build")
        self.element = {
            "versionName": "1.2.3-rc1",
            "versionCode": 456,
            "outputFile": "android-release.apk",
        }
        self.write_metadata()

    def write_metadata(self):
        (self.source / "output-metadata.json").write_text(
            json.dumps({"elements": [self.element]})
        )

    def test_replaces_old_downloads_with_the_exact_tested_apk(self):
        apk = b"PK\x03\x04tested signed APK bytes"
        (self.source / "android-release.apk").write_bytes(apk)
        stage_android_apk(self.source, self.destination)
        filename = "storymile-1.2.3-rc1-456.apk"
        self.assertEqual(list(self.destination.iterdir()), [self.destination / filename])
        self.assertEqual((self.destination / filename).read_bytes(), apk)
        self.assertEqual((self.source / "android-release.apk").read_bytes(), apk)

        self.element["versionCode"] = 457
        self.write_metadata()
        stage_android_apk(self.source, self.destination)
        self.assertEqual(
            list(self.destination.iterdir()), [self.destination / "storymile-1.2.3-rc1-457.apk"]
        )

    def test_missing_or_malformed_metadata_preserves_previous_downloads(self):
        (self.source / "android-release.apk").write_bytes(b"tested APK")
        metadata = self.source / "output-metadata.json"
        metadata.unlink()
        for contents in (None, "{", "null", '{}', '{"elements": []}', '{"elements": [null]}'):
            with self.subTest(contents=contents):
                if contents is not None:
                    metadata.write_text(contents)
                with self.assertRaisesRegex(ValueError, "metadata"):
                    stage_android_apk(self.source, self.destination)
                self.assertEqual((self.destination / "old.apk").read_bytes(), b"previous build")

    def test_mismatched_or_ambiguous_metadata_preserves_previous_downloads(self):
        (self.source / "android-release.apk").write_bytes(b"tested APK")
        for elements in (
            [{**self.element, "outputFile": "different.apk"}],
            [self.element, self.element],
        ):
            with self.subTest(elements=elements):
                (self.source / "output-metadata.json").write_text(json.dumps({"elements": elements}))
                with self.assertRaisesRegex(ValueError, "exactly the tested APK"):
                    stage_android_apk(self.source, self.destination)
                self.assertEqual((self.destination / "old.apk").read_bytes(), b"previous build")

    def test_invalid_versions_preserve_previous_downloads(self):
        (self.source / "android-release.apk").write_bytes(b"tested APK")
        for version_name, version_code in (
            (None, 456),
            ("", 456),
            ("../../escape", 456),
            ('1.0"', 456),
            ("1.0.0", None),
            ("1.0.0", "456"),
            ("1.0.0", True),
            ("1.0.0", 0),
        ):
            with self.subTest(version_name=version_name, version_code=version_code):
                self.element.update(versionName=version_name, versionCode=version_code)
                self.write_metadata()
                with self.assertRaisesRegex(ValueError, "versionName.*versionCode"):
                    stage_android_apk(self.source, self.destination)
                self.assertEqual((self.destination / "old.apk").read_bytes(), b"previous build")

    def test_missing_empty_or_ambiguous_apks_preserve_previous_downloads(self):
        for files in ([], [("empty.apk", b"")], [("a.apk", b"a"), ("b.apk", b"b")]):
            with self.subTest(files=files):
                for path in self.source.iterdir():
                    path.unlink()
                for name, data in files:
                    (self.source / name).write_bytes(data)
                with self.assertRaisesRegex(ValueError, "exactly one nonempty"):
                    stage_android_apk(self.source, self.destination)
                self.assertEqual((self.destination / "old.apk").read_bytes(), b"previous build")

    def test_directory_with_apk_extension_is_rejected(self):
        (self.source / "directory.apk").mkdir()
        with self.assertRaisesRegex(ValueError, "exactly one nonempty"):
            stage_android_apk(self.source, self.destination)

    def test_source_inside_downloads_is_not_deleted(self):
        with self.assertRaisesRegex(ValueError, "outside docs/downloads"):
            stage_android_apk(self.destination, self.destination)
        self.assertEqual((self.destination / "old.apk").read_bytes(), b"previous build")


if __name__ == "__main__":
    unittest.main()
