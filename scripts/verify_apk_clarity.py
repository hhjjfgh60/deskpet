#!/usr/bin/env python3
"""Audit packaged source pixels and signing continuity without exposing private keys."""
import hashlib
import io
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
APK = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
TOOLS = Path(os.environ["ANDROID_HOME"]) / "build-tools/35.0.0"
REPORT = ROOT / "app/build/reports/clarity"
EXPECTED_PNG = "58ff74425fa8d1e6cf1359532312d39d60735f9515c00df4633b75390485c843"


def signer(apk):
    result = subprocess.run([str(TOOLS / "apksigner"), "verify", "--print-certs", str(apk)],
                            capture_output=True, text=True, check=True)
    match = re.search(r"Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]+)", result.stdout)
    if not match:
        raise ValueError("APK signer certificate could not be read")
    return match.group(1).lower()


def main():
    REPORT.mkdir(parents=True, exist_ok=True)
    actual_signer = signer(APK)
    expected_signer = (ROOT / "signing-certificate.sha256").read_text().strip()
    assert actual_signer == expected_signer, "APK was not signed with the pinned fixed key"
    badging = subprocess.check_output([str(TOOLS / "aapt"), "dump", "badging", str(APK)], text=True)
    assert "name='com.deskpet'" in badging and "versionCode='2'" in badging
    path = "assets/pets/gpt-dragon/spritesheet.png"
    with zipfile.ZipFile(APK) as package:
        pixels = package.read(path)
        metadata = json.loads(package.read("assets/pets/gpt-dragon/pet.json"))
    assert hashlib.sha256(pixels).hexdigest() == EXPECTED_PNG
    assert (metadata["columns"], metadata["rows"]) == (8, 11)
    with Image.open(io.BytesIO(pixels)) as atlas:
        atlas.load()
        assert atlas.size == (1536, 2288) and atlas.mode == "RGBA"
        native = atlas.crop((0, 0, 192, 208))
        native.save(REPORT / "original-idle-frame-192x208.png")
        bbox = native.getchannel("A").getbbox()
    previous = Path(os.environ["RUNNER_TEMP"]) / "previous-app-debug.apk"
    old_signer = None
    same_source = None
    if previous.is_file() and previous.stat().st_size > 0:
        try:
            old_signer = signer(previous)
            with zipfile.ZipFile(previous) as old:
                same_source = old.read(path) == pixels
            assert same_source, "Original dragon artwork unexpectedly changed"
        except (subprocess.CalledProcessError, zipfile.BadZipFile):
            print("Previous artifact unavailable; fixed new certificate is still verified.")
    report = {
        "atlasPixels": [1536, 2288], "framePixels": [192, 208], "idleAlphaBounds": bbox,
        "sourcePngSha256": EXPECTED_PNG, "sourceUnchangedFromInstalledApk": same_source,
        "currentPhoneMeasuredPixels": None,
        "phoneMeasurement": "Read the new app's pet list: actual overlay pixels and px/dp are measured on-device.",
        "displayPixelsByScale": {str(scale): [int(192 * scale), int(208 * scale)] for scale in (0.5, 0.75, 1, 1.5, 2, 3)},
        "recommendedScale": "75%-100%; enlarging cannot recover native facial detail",
        "sourceIdleFrames": 6, "sourceIdleCycleMs": 1100,
        "fixedSignerSha256": actual_signer, "previousSignerSha256": old_signer,
        "canReplaceOriginalDebugVersion": old_signer == actual_signer if old_signer else False,
        "futureFixedKeyUpdates": "Same applicationId, higher versionCode, same pinned key; retain existing DataStore and files.",
        "firstMigration": "Back up in the old app only after this fixed-signed build succeeds; original debug private key was not retained.",
    }
    (REPORT / "clarity-and-signature.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(report, ensure_ascii=False, indent=2))

if __name__ == "__main__":
    main()
