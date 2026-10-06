"""Serve the built site at its GitHub Pages project path."""

import argparse
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit


class PagesHandler(SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header("Cache-Control", "no-store")
        super().end_headers()

    def send_head(self):
        # Rebuilds can change files within the timestamp precision of HTTP caching.
        if "If-Modified-Since" in self.headers:
            del self.headers["If-Modified-Since"]
        return super().send_head()

    def do_GET(self):
        if urlsplit(self.path).path == "/":
            self.send_response(302)
            self.send_header("Location", "/storymile/")
            self.end_headers()
            return
        super().do_GET()

    def translate_path(self, path):
        if urlsplit(path).path == "/storymile":
            return super().translate_path("/")
        if not urlsplit(path).path.startswith("/storymile/"):
            return super().translate_path("/__outside_project_path__")
        return super().translate_path(path[len("/storymile"):])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--directory", default="site")
    parser.add_argument("--port", type=int, default=4174)
    args = parser.parse_args()
    handler = partial(PagesHandler, directory=args.directory)
    with ThreadingHTTPServer(("127.0.0.1", args.port), handler) as server:
        print(f"Preview: http://127.0.0.1:{args.port}/storymile/", flush=True)
        server.serve_forever()


if __name__ == "__main__":
    main()
