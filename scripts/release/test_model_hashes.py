import json
import unittest
from pathlib import Path

from model_hashes import lines

MODELS = Path(__file__).resolve().parents[2] / "app" / "src" / "main" / "assets" / "models.json"


class ModelHashesTest(unittest.TestCase):
    def test_each_pinned_file_gets_one_line_with_its_size_and_sha256(self):
        text = json.dumps({"models": [{"id": "m1", "files": [{"name": "a.bin", "bytes": 12, "sha256": "ab" * 32, "url": "https://example.org/a.bin"}, {"name": "b.bin", "bytes": 7, "sha256": "cd" * 32}]}]})
        self.assertEqual(["m1 a.bin 12 bytes sha256 " + "ab" * 32 + " url https://example.org/a.bin", "m1 b.bin 7 bytes sha256 " + "cd" * 32], lines(text))

    def test_a_file_without_a_hash_is_an_error_not_an_empty_line(self):
        with self.assertRaises(ValueError):
            lines(json.dumps({"models": [{"id": "m1", "files": [{"name": "a.bin", "bytes": 1}]}]}))
        with self.assertRaises(ValueError):
            lines(json.dumps({"models": [{"id": "m1", "files": [{"name": "a.bin", "bytes": 1, "sha256": "short"}]}]}))

    def test_the_shipped_registry_lists_the_pinned_model(self):
        out = lines(MODELS.read_text(encoding="utf-8"))
        self.assertTrue(any("gemma-4-E2B-it.litertlm" in line for line in out), out)
        self.assertTrue(all("sha256 " in line for line in out))
        self.assertTrue(any(" url https://" in line for line in out))


if __name__ == "__main__":
    unittest.main()
