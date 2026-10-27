#!/usr/bin/env python3
"""Reads the release notes for one version out of CHANGELOG.md. A release without a non-empty section is an error."""
import re
import sys
from pathlib import Path


class MissingNotes(ValueError):
    pass


def notes_for(changelog: str, version: str) -> str:
    heading = re.compile(r"^## \[(?P<v>[^\]]+)\].*$", re.MULTILINE)
    found = list(heading.finditer(changelog))
    for i, m in enumerate(found):
        if m.group("v") == version and version.lower() != "unreleased":
            end = found[i + 1].start() if i + 1 < len(found) else len(changelog)
            body = changelog[m.end():end].strip()
            if not body:
                raise MissingNotes(f"the CHANGELOG section for {version} is empty")
            return body
    raise MissingNotes(f"CHANGELOG.md has no section for {version}")


def main(argv: list[str]) -> int:
    if len(argv) != 3:
        print("usage: changelog.py CHANGELOG.md VERSION", file=sys.stderr)
        return 2
    try:
        print(notes_for(Path(argv[1]).read_text(encoding="utf-8"), argv[2]))
    except MissingNotes as e:
        print(f"error: {e}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
