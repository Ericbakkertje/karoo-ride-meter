#!/usr/bin/env python3
"""Show how often each Karoo Ride Meter APK was downloaded from GitHub Releases.

Usage:  python3 tools/downloads.py
Uses GitHub's public API (no login needed for a public repository).
"""
import json
import urllib.request

REPO = "Ericbakkertje/karoo-ride-meter"


def main() -> None:
    url = f"https://api.github.com/repos/{REPO}/releases"
    req = urllib.request.Request(url, headers={"Accept": "application/vnd.github+json", "User-Agent": "karoo-ride-meter-downloads"})
    with urllib.request.urlopen(req, timeout=15) as resp:
        releases = json.load(resp)

    total = 0
    print(f"Downloads for {REPO}\n")
    for r in releases:
        date = (r.get("published_at") or "")[:10]
        print(f"{r['name'] or r['tag_name']}  ({r['tag_name']}, {date})")
        for a in r.get("assets", []):
            print(f"    {a['download_count']:>6}  {a['name']}")
            total += a["download_count"]
    print(f"\nTotal: {total}")


if __name__ == "__main__":
    main()
