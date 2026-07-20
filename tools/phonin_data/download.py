"""Cached downloader using only the standard library (avoids requests/urllib3 + macOS
LibreSSL pitfalls). Follows redirects, retries with exponential backoff, skips on cache hit.
"""

import os
import time
import urllib.request


def fetch(url: str, cache_rel: str, cache_dir: str) -> str:
    target = os.path.join(cache_dir, cache_rel)
    if os.path.exists(target) and os.path.getsize(target) > 0:
        print(f"  cached:  {cache_rel}")
        return target
    os.makedirs(os.path.dirname(target), exist_ok=True)
    print(f"  fetching: {url}")
    last = None
    for attempt in range(5):
        try:
            req = urllib.request.Request(
                url,
                headers={"User-Agent": "PhonIn-dataset/0.1", "Accept": "*/*"},
            )
            with urllib.request.urlopen(req, timeout=120) as resp:
                data = resp.read()
            with open(target, "wb") as f:
                f.write(data)
            print(f"  saved:    {cache_rel} ({len(data)} bytes)")
            return target
        except Exception as e:  # noqa: BLE001
            last = e
            if os.path.exists(target):
                os.remove(target)
            wait = 0.75 * (2 ** attempt)
            print(f"  retry {attempt + 2}/5 after {wait:.0f}s: {e}")
            time.sleep(wait)
    raise RuntimeError(f"failed to fetch {url}: {last}")


def cache_path(cache_rel: str, cache_dir: str) -> str:
    return os.path.join(cache_dir, cache_rel)
