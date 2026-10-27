#!/usr/bin/env python3
"""Reads the release notes for one version out of CHANGELOG.md. A release without non-empty notes is an error."""
import re
import sys
from pathlib import Path


class MissingNotes(ValueError):
    pass


_HEADING = re.compile(r"^## \[(?P<v>[^\]]+)\]")
_RC = re.compile(r"(?P<release>[0-9]+\.[0-9]+\.[0-9]+)-rc\.[0-9]+")


def _sections(changelog: str) -> list[tuple[str, str]]:
    """(version, body) for every `## [version]` heading, ignoring headings inside code fences."""
    sections: list[tuple[str, list[str]]] = []
    in_fence = False
    for line in changelog.splitlines():
        if line.lstrip().startswith("```"):
            in_fence = not in_fence
        m = None if in_fence else _HEADING.match(line)
        if m:
            sections.append((m.group("v"), []))
        elif sections:
            sections[-1][1].append(line)
    return [(v, "\n".join(body).strip()) for v, body in sections]


def _has_content(body: str) -> bool:
    return any(line.strip() and not line.lstrip().startswith("#") for line in body.splitlines())


def notes_for(changelog: str, version: str) -> str:
    if version.lower() == "unreleased":
        raise MissingNotes("the Unreleased section is not a release")
    sections = dict(_sections(changelog))
    candidates = [version]
    rc = _RC.fullmatch(version)
    if rc:
        candidates.append(rc.group("release"))
    for name in candidates:
        if name in sections:
            if not _has_content(sections[name]):
                raise MissingNotes(f"the CHANGELOG section for {name} is empty")
            return sections[name]
    raise MissingNotes(f"CHANGELOG.md has no section for {' or '.join(candidates)}")


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
