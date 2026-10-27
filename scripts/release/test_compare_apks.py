import tempfile
import unittest
import zipfile
from pathlib import Path

from compare_apks import compare


def make(path: Path, entries: dict[str, bytes], order: list[str] | None = None, timestamp=(2026, 1, 1, 0, 0, 0)) -> Path:
    with zipfile.ZipFile(path, "w") as z:
        for name in order or list(entries):
            info = zipfile.ZipInfo(name, date_time=timestamp)
            z.writestr(info, entries[name])
    return path


class CompareApksTest(unittest.TestCase):
    def setUp(self):
        self.dir = Path(tempfile.mkdtemp())

    def test_two_files_with_the_same_bytes_are_identical(self):
        a = make(self.dir / "a.apk", {"classes.dex": b"x", "AndroidManifest.xml": b"m"})
        b = self.dir / "b.apk"
        b.write_bytes(a.read_bytes())
        r = compare(a, b)
        self.assertTrue(r.identical)
        self.assertEqual([], r.different_entries)

    def test_a_changed_entry_is_named(self):
        a = make(self.dir / "a.apk", {"classes.dex": b"x", "res/a.png": b"1"})
        b = make(self.dir / "b.apk", {"classes.dex": b"y", "res/a.png": b"1"})
        r = compare(a, b)
        self.assertFalse(r.identical)
        self.assertEqual(["classes.dex"], r.different_entries)

    def test_a_missing_and_an_extra_entry_are_named(self):
        a = make(self.dir / "a.apk", {"a": b"1", "only-a": b"2"})
        b = make(self.dir / "b.apk", {"a": b"1", "only-b": b"3"})
        r = compare(a, b)
        self.assertEqual(["only-a"], r.only_in_first)
        self.assertEqual(["only-b"], r.only_in_second)
        self.assertFalse(r.identical)

    def test_same_contents_in_a_different_order_or_with_other_timestamps_are_not_byte_identical_but_every_entry_matches(self):
        a = make(self.dir / "a.apk", {"a": b"1", "b": b"2"})
        b = make(self.dir / "b.apk", {"a": b"1", "b": b"2"}, order=["b", "a"], timestamp=(2026, 2, 2, 0, 0, 0))
        r = compare(a, b)
        self.assertFalse(r.identical)
        self.assertEqual([], r.different_entries)
        self.assertTrue(r.entries_match)

    def test_signature_files_can_be_ignored_for_comparing_an_unsigned_and_a_signed_build(self):
        a = make(self.dir / "a.apk", {"classes.dex": b"x", "META-INF/CERT.RSA": b"sig1", "META-INF/CERT.SF": b"sf1", "META-INF/MANIFEST.MF": b"mf1"})
        b = make(self.dir / "b.apk", {"classes.dex": b"x", "META-INF/CERT.RSA": b"sig2", "META-INF/CERT.SF": b"sf2", "META-INF/MANIFEST.MF": b"mf2"})
        self.assertFalse(compare(a, b).entries_match)
        self.assertTrue(compare(a, b, ignore_signature=True).entries_match)

    def test_a_file_that_is_not_a_zip_is_an_error_not_a_pass(self):
        bad = self.dir / "bad.apk"
        bad.write_bytes(b"not a zip")
        good = make(self.dir / "g.apk", {"a": b"1"})
        with self.assertRaises(ValueError):
            compare(bad, good)


if __name__ == "__main__":
    unittest.main()
