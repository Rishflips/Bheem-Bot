#!/usr/bin/env python3
"""Lists the model files the app pins, with size and SHA-256, for the release page."""
import json
import re
import sys
from pathlib import Path

_SHA256 = re.compile(r"[0-9a-f]{64}")


def lines(registry_text: str) -> list[str]:
    out = []
    for model in json.loads(registry_text)["models"]:
        for f in model["files"]:
            sha = f.get("sha256", "")
            if not _SHA256.fullmatch(sha):
                raise ValueError(f"{model['id']} {f.get('name')}: sha256 is missing or malformed")
            url = f" url {f['url']}" if f.get("url") else ""
            out.append(f"{model['id']} {f['name']} {f['bytes']} bytes sha256 {sha}{url}")
    return out


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("usage: model_hashes.py models.json", file=sys.stderr)
        sys.exit(2)
    print("\n".join(lines(Path(sys.argv[1]).read_text(encoding="utf-8"))))
