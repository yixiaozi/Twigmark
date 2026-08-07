#!/usr/bin/env python3
"""Generate docs/data/release.json for the Twigmark GitHub Pages site."""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.request
from datetime import date
from pathlib import Path


def read_version_properties(path: Path) -> dict[str, str]:
    data: dict[str, str] = {}
    if not path.is_file():
        return data
    for raw in path.read_text(encoding="utf-8", errors="replace").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        data[key.strip()] = value.strip().replace("\\:", ":")
    return data


def strip_v(tag: str) -> str:
    return re.sub(r"^[vV]", "", tag or "").strip()


def pick_asset_url(assets: list[dict], html_url: str) -> str:
    preferred = None
    for asset in assets:
        name = asset.get("name") or ""
        url = asset.get("browser_download_url") or ""
        if not url:
            continue
        if re.search(r"twigmark|docear|windows|\.zip|\.exe", name, re.I):
            preferred = url
            break
        if preferred is None:
            preferred = url
    return preferred or html_url or "https://github.com/yixiaozi/Twigmark/releases/latest"


def fetch_latest_release(repo: str, token: str | None) -> dict | None:
    url = f"https://api.github.com/repos/{repo}/releases/latest"
    headers = {
        "Accept": "application/vnd.github+json",
        "User-Agent": "twigmark-pages-release-sync",
    }
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError, json.JSONDecodeError):
        return None


def infer_status(tag: str, name: str, prop_status: str) -> str:
    blob = f"{tag} {name} {prop_status}".lower()
    if re.search(r"pre|rc|beta|alpha|snapshot", blob):
        return "preview"
    return prop_status or "stable"


def build_payload(args: argparse.Namespace) -> dict:
    props = read_version_properties(Path(args.version_properties))
    version = props.get("docear_version", "0.0.0")
    status = props.get("docear_version_status", "stable")
    repo = args.repo or "yixiaozi/Twigmark"
    repo_url = f"https://github.com/{repo}"
    notes_url = f"{repo_url}/blob/master/RELEASE_NOTES.md"
    html_url = f"{repo_url}/releases"
    download_url = f"{repo_url}/releases/latest"
    published_at = date.today().isoformat()
    tag = f"v{version}"

    if args.event_name == "release" and args.release_tag:
        tag = args.release_tag
        version = strip_v(tag) or version
        html_url = args.release_html_url or html_url
        download_url = html_url
        if args.release_published_at:
            published_at = args.release_published_at[:10]
        status = infer_status(tag, args.release_name or "", status)

        # Prefer a concrete asset when the event payload is available via API
        token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
        latest = fetch_latest_release(repo, token)
        if latest and strip_v(latest.get("tag_name", "")) == version:
            download_url = pick_asset_url(latest.get("assets") or [], html_url)
            if latest.get("published_at"):
                published_at = latest["published_at"][:10]
    else:
        token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
        latest = fetch_latest_release(repo, token)
        if latest and latest.get("tag_name"):
            tag = latest["tag_name"]
            version = strip_v(tag) or version
            html_url = latest.get("html_url") or html_url
            download_url = pick_asset_url(latest.get("assets") or [], html_url)
            if latest.get("published_at"):
                published_at = latest["published_at"][:10]
            status = infer_status(tag, latest.get("name") or "", status)
        else:
            # Fall back to version.properties only
            tag = f"v{version}"
            status = infer_status(tag, "", status)

    return {
        "name": "Twigmark",
        "version": version,
        "status": status,
        "tag": tag,
        "publishedAt": published_at,
        "htmlUrl": html_url,
        "downloadUrl": download_url,
        "notesUrl": notes_url,
        "repoUrl": repo_url,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", required=True, help="Output release.json path")
    parser.add_argument(
        "--version-properties",
        default="docear_plugin_core/resources/version.properties",
    )
    parser.add_argument("--event-name", default="")
    parser.add_argument("--release-tag", default="")
    parser.add_argument("--release-name", default="")
    parser.add_argument("--release-html-url", default="")
    parser.add_argument("--release-published-at", default="")
    parser.add_argument("--repo", default="yixiaozi/Twigmark")
    args = parser.parse_args()

    payload = build_payload(args)
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
