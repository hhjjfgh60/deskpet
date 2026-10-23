#!/usr/bin/env python3
"""Decode real images and verify the upstream atlas and adapted Android metadata."""
import hashlib
import json
from pathlib import Path
import sys
from PIL import Image
from prepare_assets import ROOT, PETS, DRAGON_SHA256, verified


def verify():
    errors = []
    for name, (sha, size) in PETS.items():
        for prefix in ("app/src/main/assets/pets", "test_pets"):
            path = ROOT / prefix / name / "spritesheet.webp"
            if not verified(path, sha, size):
                errors.append(f"missing/corrupt upstream atlas: {path.relative_to(ROOT)}")
        path = ROOT / "app/src/main/assets/pets" / name / "spritesheet.webp"
        if verified(path, sha, size):
            with Image.open(path) as image:
                image.load()
                assert image.width % 8 == 0 and image.height % 9 == 0, name

    atlas = ROOT / "app/src/main/assets/pets/gpt-dragon/spritesheet.png"
    if not verified(atlas, DRAGON_SHA256, 2602295, sha256=True):
        errors.append("missing/corrupt dragon spritesheet.png (expected 2602295 bytes and pinned SHA-256)")
    else:
        with Image.open(atlas) as image:
            image.load()
            assert image.format == "PNG" and image.mode == "RGBA", "Not an RGBA PNG"
            assert image.size == (1536, 2288), "Incorrect dragon dimensions"
            counts = [6, 8, 8, 4, 5, 8, 6, 6, 6, 8, 8]
            for row, count in enumerate(counts):
                for col in range(8):
                    cell = image.crop((192 * col, 208 * row, 192 * (col + 1), 208 * (row + 1)))
                    occupied = cell.getchannel("A").getbbox() is not None
                    assert occupied == (col < count), f"Unexpected alpha at row={row}, col={col}"
            print("Dragon PNG decoded: 57 animation frames + 16 direction poses + 15 transparent cells")

    upstream = json.loads((ROOT / "doc/gpt-dragon/upstream-pet.json").read_text())
    adapted = json.loads((ROOT / "app/src/main/assets/pets/gpt-dragon/pet.json").read_text())
    assert (adapted["columns"], adapted["rows"]) == (8, 11)
    assert adapted["spritesheetPath"] == "spritesheet.png"
    assert len(adapted["states"]) == 9
    for actual, source in zip(adapted["states"], upstream["animations"]):
        assert actual == {"name": source["state"], "row": source["row"],
                          "frames": source["frame_count"], "frameDurationsMs": source["frame_durations_ms"]}
    print("Android metadata matches upstream rows, frame counts and individual durations")
    if errors:
        raise ValueError("\n".join(errors))
    print("All asset checks passed")

if __name__ == "__main__":
    try:
        verify()
    except Exception as error:
        print(f"ASSET VALIDATION FAILED: {error}", file=sys.stderr)
        sys.exit(1)
