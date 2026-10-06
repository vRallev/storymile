"""Keep repository assets and source links in sync with the documentation."""

from hashlib import sha256
from pathlib import Path
import re
import shutil
from urllib.parse import quote, unquote, urlsplit, urlunsplit

from mkdocs.exceptions import PluginError


ROOT = Path(__file__).resolve().parents[2]
INLINE_LINK = re.compile(r"(?P<start>\[[^\]\n]+\]\()(?P<url>[^\s)]+)")
REFERENCE_LINK = re.compile(r"(?m)^(?P<start> {0,3}\[[^\]\n]+\]:\s*)(?P<url>\S+)")


def on_config(config):
    # Changed assets must have new URLs so cached scripts match the page controls.
    docs = Path(config["docs_dir"])
    for option in ("extra_css", "extra_javascript"):
        config[option] = [
            f"{path}?v={sha256((docs / path).read_bytes()).hexdigest()[:12]}"
            for path in config[option]
        ]
    return config


def on_pre_build(config):
    assets = Path(config["docs_dir"]) / "assets"
    assets.mkdir(exist_ok=True)
    resources = ROOT / "app/web/src/wasmJsMain/resources"
    shutil.copyfile(resources / "icons/icon-192.png", assets / "icon.png")
    shutil.copyfile(resources / "favicon.ico", assets / "favicon.ico")

    gallery = Path(config["docs_dir"]) / "design-research/gallery"
    if gallery.exists():
        shutil.rmtree(gallery)
    gallery.mkdir(parents=True)
    gallery_icons = gallery.parent / "images"
    gallery_icons.mkdir(exist_ok=True)
    for name in ("icon-light.svg", "icon-dark.svg"):
        shutil.copyfile(ROOT / "images" / name, gallery_icons / name)
    for name in (
        "index.html",
        "data.js",
        "image-loader.js",
        "prompts.json",
        "screens",
        "previews",
    ):
        source = ROOT / "design-research" / name
        if source.is_dir():
            shutil.copytree(source, gallery / name)
        else:
            shutil.copyfile(source, gallery / name)


def on_page_markdown(markdown, page, config, files):
    docs = Path(config["docs_dir"]).resolve()
    source = Path(page.file.abs_src_path).parent

    def replace(match):
        original = match["url"]
        enclosed = original.startswith("<") and original.endswith(">")
        url = urlsplit(original[1:-1] if enclosed else original)
        if url.scheme or url.netloc or not url.path or url.path.startswith("/"):
            return match[0]
        target = (source / unquote(url.path)).resolve()
        if target.is_relative_to(docs):
            return match[0]
        if not target.is_relative_to(ROOT) or not target.exists():
            raise PluginError(f"{page.file.src_uri}: missing repository source: {url.path}")
        kind = "tree" if target.is_dir() else "blob"
        path = quote(target.relative_to(ROOT).as_posix(), safe="/")
        repository = config["repo_url"].rstrip("/")
        rewritten = urlunsplit(
            urlsplit(f"{repository}/{kind}/main/{path}")._replace(
                query=url.query, fragment=url.fragment
            )
        )
        return match["start"] + (f"<{rewritten}>" if enclosed else rewritten)

    return REFERENCE_LINK.sub(replace, INLINE_LINK.sub(replace, markdown))
