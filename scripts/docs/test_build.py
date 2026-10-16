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

    def test_replaces_old_downloads_with_the_exact_tested_apk(self):
        apk = b"PK\x03\x04tested signed APK bytes"
        (self.source / "android-release.apk").write_bytes(apk)
        stage_android_apk(self.source, self.destination)
        self.assertEqual(list(self.destination.iterdir()), [self.destination / "storymile.apk"])
        self.assertEqual((self.destination / "storymile.apk").read_bytes(), apk)
        self.assertEqual((self.source / "android-release.apk").read_bytes(), apk)

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
