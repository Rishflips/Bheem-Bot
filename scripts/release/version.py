#!/usr/bin/env python3
"""Turns a release tag into the app's version name and version code.

Tags: vMAJOR.MINOR.PATCH (a release), vMAJOR.MINOR.PATCH-rc.N (a release candidate, N 1 to 98), nightly-YYYYMMDD.
The code is ((MAJOR * 1000 + MINOR) * 1000 + PATCH) * 100 + STAGE, where STAGE is 99 for a release and N for rc.N, so every later
version has a larger code. Releases start at 0.1.0, which keeps every nightly code (days since 2026-01-01, plus one) below
every release code. Both flavors use the same code because it depends on the tag alone.
"""
import datetime
import re
import sys
from dataclasses import dataclass

MAX_VERSION_CODE = 2_100_000_000
NIGHTLY_EPOCH = datetime.date(2026, 1, 1)
FIRST_RELEASE_CODE = 100001  # v0.1.0-rc.1

_RELEASE = re.compile(r"v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(?:-rc\.([1-9][0-9]*))?")
_NIGHTLY = re.compile(r"nightly-([0-9]{8})")


class InvalidTag(ValueError):
    pass


@dataclass(frozen=True)
class Version:
    name: str
    code: int
    channel: str


def from_tag(tag: str) -> Version:
    m = _RELEASE.fullmatch(tag)
    if m:
        major, minor, patch = (int(m.group(i)) for i in (1, 2, 3))
        rc = int(m.group(4)) if m.group(4) else None
        if (major, minor, patch) < (0, 1, 0):
            raise InvalidTag(f"{tag}: releases start at v0.1.0")
        if minor > 999 or patch > 999:
            raise InvalidTag(f"{tag}: minor and patch must be at most 999")
        if rc is not None and rc > 98:
            raise InvalidTag(f"{tag}: the release candidate number must be at most 98")
        code = ((major * 1000 + minor) * 1000 + patch) * 100 + (99 if rc is None else rc)
        if code > MAX_VERSION_CODE:
            raise InvalidTag(f"{tag}: the version code {code} would pass Android's limit of {MAX_VERSION_CODE}")
        return Version(tag[1:], code, "release" if rc is None else "rc")
    m = _NIGHTLY.fullmatch(tag)
    if m:
        digits = m.group(1)
        try:
            day = datetime.date(int(digits[:4]), int(digits[4:6]), int(digits[6:]))
        except ValueError as e:
            raise InvalidTag(f"{tag}: not a calendar date") from e
        if day < NIGHTLY_EPOCH:
            raise InvalidTag(f"{tag}: nightly dates start on {NIGHTLY_EPOCH}")
        code = (day - NIGHTLY_EPOCH).days + 1
        if code >= FIRST_RELEASE_CODE:
            raise InvalidTag(f"{tag}: that nightly date is too far ahead; its code would reach the release codes")
        return Version(f"0.0.0-nightly.{digits}", code, "nightly")
    raise InvalidTag(f"{tag!r}: expected vX.Y.Z, vX.Y.Z-rc.N or nightly-YYYYMMDD")


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print("usage: version.py TAG", file=sys.stderr)
        return 2
    try:
        v = from_tag(argv[1])
    except InvalidTag as e:
        print(f"error: {e}", file=sys.stderr)
        return 1
    print(f"BHEEM_VERSION_NAME={v.name}")
    print(f"BHEEM_VERSION_CODE={v.code}")
    print(f"BHEEM_CHANNEL={v.channel}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
