#!/usr/bin/env python3
"""Restore only missing/corrupt binary assets. Never overwrite a verified local file."""
import argparse
import base64
import hashlib
import json
from pathlib import Path
import sys
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
UPSTREAM = "e96679fab754e94f787fbd4bfd695a622383ba81"
DRAGON = "3afe14603b40e24c75e87e097cbfee1bfc10290d"
PETS = {
    "feibi-2": ("44d690307a4ce692f000e674ac037d95b992869f", 2102210),
    "feixue": ("aab1b74b681d7a53dc0a4f8b4048326df331583f", 2894438),
    "furina": ("7b0dd26db2c4351d1c8c588811133d09c0697d4b", 2199566),
    "hutao": ("fbd5f8e565810192f8ca18a96ab3e789c3b58763", 2266412),
    "ikun": ("9cc6a52e7704ccb3e581b1545979d2c6d69988b7", 1644844),
    "jiyi": ("b8ff29308e7ce3951ca670a37bf8a4bb39a8a298", 1871226),
    "yi-bu": ("27001e296a84fb996fb87ab92ff85278acd56efc", 860912),
}
DRAGON_SHA256 = "58ff74425fa8d1e6cf1359532312d39d60735f9515c00df4633b75390485c843"

def blob_hash(data):
    return hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()

def verified(path, sha, size, sha256=False):
    if not path.is_file() or path.stat().st_size != size:
        return False
    data = path.read_bytes()
    return (hashlib.sha256(data).hexdigest() if sha256 else blob_hash(data)) == sha

def fetch(repo, commit, source_path, sha):
    # Raw binary endpoint first; Git Blobs JSON/base64 is the alternate for >1 MB.
    sources = [
        (f"https://raw.githubusercontent.com/{repo}/{commit}/{source_path}", False),
        (f"https://api.github.com/repos/{repo}/git/blobs/{sha}", True),
    ]
    errors = []
    for url, is_json in sources:
        try:
            request = urllib.request.Request(url, headers={"User-Agent": "deskpet-asset-builder"})
            with urllib.request.urlopen(request, timeout=15) as response:
                data = response.read(8 * 1024 * 1024 + 1)
            if len(data) > 8 * 1024 * 1024:
                raise ValueError("download exceeds 8 MiB")
            if is_json:
                body = json.loads(data)
                if body.get("encoding") != "base64":
                    raise ValueError("Git Blobs endpoint did not return base64")
                data = base64.b64decode(body["content"])
            if blob_hash(data) != sha:
                raise ValueError("Git blob checksum mismatch")
            return data
        except Exception as error:
            errors.append(f"{url}: {error}")
    raise RuntimeError("\n".join(errors))

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--offline", action="store_true", help="List pending assets without making requests")
    args = parser.parse_args()
    entries = []
    for name, (sha, size) in PETS.items():
        relative = f"app/src/main/assets/pets/{name}/spritesheet.webp"
        entries.append((relative, "faysal-X-N/deskpet", UPSTREAM, relative, sha, size))
    entries.append(("app/src/main/assets/pets/gpt-dragon/spritesheet.png",
                    "tqlearning/ai-girl-chatgpt-pets", DRAGON,
                    "assets/gpt-dragon/spritesheet.png", "cb59dfb94bafe38fefd9e4fc84ebb52bc137845b", 2602295))
    failures = []
    for relative, repo, commit, source, sha, size in entries:
        path = ROOT / relative
        if verified(path, sha, size):
            print(f"OK (kept existing): {relative}")
            continue
        if args.offline:
            failures.append(f"PENDING: {relative} (expected {size} bytes, git blob {sha})")
            continue
        try:
            data = fetch(repo, commit, source, sha)
            if len(data) != size:
                raise ValueError(f"wrong length: {len(data)}, expected {size}")
            path.parent.mkdir(parents=True, exist_ok=True)
            tmp = path.with_suffix(path.suffix + ".download")
            tmp.write_bytes(data)
            tmp.replace(path)
            print(f"RESTORED: {relative}")
        except Exception as error:
            failures.append(f"PENDING: {relative}\n{error}")
    # The test assets are byte-identical copies in the upstream tree.
    for name, (sha, size) in PETS.items():
        source = ROOT / f"app/src/main/assets/pets/{name}/spritesheet.webp"
        target = ROOT / f"test_pets/{name}/spritesheet.webp"
        if verified(source, sha, size) and not verified(target, sha, size):
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(source.read_bytes())
    if failures:
        print("\n".join(failures), file=sys.stderr)
        print("Assets incomplete; no complete dragon APK can be claimed. See doc/ANDROID_PHONE.md.", file=sys.stderr)
        return 1
    return 0

if __name__ == "__main__":
    sys.exit(main())
