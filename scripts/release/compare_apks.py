#!/usr/bin/env python3
"""Compares two APKs: byte for byte, and entry by entry (so a difference can be named)."""
import hashlib
import sys
import zipfile
from dataclasses import dataclass, field
from pathlib import Path

SIGNATURE_PREFIX = "META-INF/"


@dataclass
class Result:
    identical: bool
    different_entries: list[str] = field(default_factory=list)
    only_in_first: list[str] = field(default_factory=list)
    only_in_second: list[str] = field(default_factory=list)

    @property
    def entries_match(self) -> bool:
        return not (self.different_entries or self.only_in_first or self.only_in_second)


def _entries(path: Path, ignore_signature: bool) -> dict[str, str]:
    try:
        with zipfile.ZipFile(path) as z:
            return {
                i.filename: hashlib.sha256(z.read(i)).hexdigest()
                for i in z.infolist()
                if not i.is_dir() and not (ignore_signature and i.filename.startswith(SIGNATURE_PREFIX))
            }
    except zipfile.BadZipFile as e:
        raise ValueError(f"{path} is not a zip file") from e


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def compare(first: Path, second: Path, ignore_signature: bool = False) -> Result:
    a, b = _entries(first, ignore_signature), _entries(second, ignore_signature)
    return Result(
        identical=_sha256(first) == _sha256(second),
        different_entries=sorted(n for n in a.keys() & b.keys() if a[n] != b[n]),
        only_in_first=sorted(a.keys() - b.keys()),
        only_in_second=sorted(b.keys() - a.keys()),
    )


def main(argv: list[str]) -> int:
    args = [a for a in argv[1:] if not a.startswith("--")]
    if len(args) != 2:
        print("usage: compare_apks.py [--ignore-signature] FIRST SECOND", file=sys.stderr)
        return 2
    r = compare(Path(args[0]), Path(args[1]), ignore_signature="--ignore-signature" in argv)
    print(f"byte-identical: {r.identical}")
    print(f"every entry matches: {r.entries_match}")
    for label, names in (("different", r.different_entries), ("only in first", r.only_in_first), ("only in second", r.only_in_second)):
        for n in names[:50]:
            print(f"  {label}: {n}")
    return 0 if r.identical else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv))
