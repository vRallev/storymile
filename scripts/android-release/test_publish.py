from copy import deepcopy
import unittest
from urllib.error import HTTPError

from publish import publish


COMMIT = "a" * 40
APK = b"tested APK"


def release(number, draft=False):
    return {
        "id": number,
        "tag_name": f"android-main-{number}",
        "target_commitish": COMMIT,
        "draft": draft,
        "assets": [],
    }


class FakeGitHub:
    def __init__(self, releases=()):
        self.releases = deepcopy(list(releases))
        self.calls = []
        self.upload_fails = False
        self.upload_size = len(APK)
        self.tag_commit = None

    def request(self, method, path, data=None):
        self.calls.append((method, path, deepcopy(data)))
        if method == "GET" and "?per_page=" in path:
            page = int(path.split("page=")[-1])
            return deepcopy(self.releases[(page - 1) * 100:page * 100])
        if method == "GET" and "/git/ref/" in path:
            if self.tag_commit:
                return {"object": {"type": "commit", "sha": self.tag_commit}}
            raise HTTPError(path, 404, "Not found", {}, None)
        if method == "POST" and path.endswith("/releases"):
            item = dict(data, id=999, assets=[])
            self.releases.append(item)
            return deepcopy(item)
        if method == "POST" and path.endswith("/assets?name=storymile.apk"):
            if self.upload_fails:
                raise HTTPError(path, 502, "Upload failed", {}, None)
            return {"state": "uploaded", "size": self.upload_size}
        if method == "DELETE":
            return None
        if method == "PATCH":
            return dict(data)
        raise AssertionError((method, path, data))

    @property
    def writes(self):
        return [call for call in self.calls if call[0] != "GET"]


class PublishTest(unittest.TestCase):
    def test_uploads_before_promoting_the_exact_commit(self):
        api = FakeGitHub()
        publish(api, "owner/repo", COMMIT, 412, APK)
        create, upload, promote = api.writes
        self.assertTrue(create[2]["draft"])
        self.assertEqual(create[2]["target_commitish"], COMMIT)
        self.assertEqual(create[2]["tag_name"], "android-main-412")
        self.assertEqual(upload[2], APK)
        self.assertFalse(promote[2]["draft"])
        self.assertEqual(promote[2]["make_latest"], "true")

    def test_older_runs_and_published_reruns_cannot_replace_the_apk(self):
        for number in (411, 412):
            with self.subTest(number=number):
                api = FakeGitHub([release(412)])
                publish(api, "owner/repo", COMMIT, number, APK)
                self.assertEqual(api.writes, [])

    def test_newer_unfinished_draft_does_not_block_a_successful_build(self):
        api = FakeGitHub([release(413, draft=True)])
        publish(api, "owner/repo", COMMIT, 412, APK)
        self.assertEqual(api.writes[-1][0], "PATCH")

    def test_retry_recovers_a_draft_without_touching_published_assets(self):
        draft = release(412, draft=True)
        draft["assets"] = [{"name": "storymile.apk", "id": 72}]
        api = FakeGitHub([release(411), draft])
        publish(api, "owner/repo", COMMIT, 412, APK)
        self.assertEqual([call[0] for call in api.writes], ["DELETE", "POST", "PATCH"])
        self.assertTrue(api.writes[0][1].endswith("/releases/assets/72"))
        self.assertTrue(api.writes[-1][1].endswith("/releases/412"))

    def test_upload_failure_preserves_the_public_latest_release(self):
        api = FakeGitHub([release(411)])
        api.upload_fails = True
        with self.assertRaises(HTTPError):
            publish(api, "owner/repo", COMMIT, 412, APK)
        self.assertNotIn("PATCH", [call[0] for call in api.writes])
        self.assertTrue(api.releases[-1]["draft"])

    def test_order_guard_checks_all_pages(self):
        api = FakeGitHub([release(n, draft=True) for n in range(100)] + [release(413)])
        publish(api, "owner/repo", COMMIT, 412, APK)
        self.assertEqual(api.writes, [])

    def test_incomplete_upload_is_not_published(self):
        api = FakeGitHub([release(411)])
        api.upload_size = len(APK) - 1
        with self.assertRaisesRegex(ValueError, "incomplete"):
            publish(api, "owner/repo", COMMIT, 412, APK)
        self.assertNotIn("PATCH", [call[0] for call in api.writes])
        self.assertTrue(api.releases[-1]["draft"])

    def test_refuses_a_tag_for_a_different_commit(self):
        api = FakeGitHub()
        api.tag_commit = "b" * 40
        with self.assertRaisesRegex(ValueError, "does not point"):
            publish(api, "owner/repo", COMMIT, 412, APK)
        self.assertEqual(api.writes, [])

    def test_refuses_a_draft_for_a_different_commit(self):
        draft = release(412, draft=True)
        draft["target_commitish"] = "b" * 40
        api = FakeGitHub([draft])
        with self.assertRaisesRegex(ValueError, "different source"):
            publish(api, "owner/repo", COMMIT, 412, APK)
        self.assertEqual(api.writes, [])


if __name__ == "__main__":
    unittest.main()
