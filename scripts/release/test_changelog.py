import unittest

from changelog import MissingNotes, notes_for

SAMPLE = """# Changelog

All notable changes are listed here.

## [Unreleased]

- Something not released yet.

## [0.2.0] - 2026-12-01

### Added
- Two things.

## [0.1.0] - 2026-10-30

### Added
- First release.
- Voice.

## [0.1.0-rc.1] - 2026-10-28

- Candidate.
"""


class NotesTest(unittest.TestCase):
    def test_the_section_of_a_release_is_returned_without_its_heading(self):
        self.assertEqual("### Added\n- First release.\n- Voice.", notes_for(SAMPLE, "0.1.0"))

    def test_a_release_candidate_has_its_own_section(self):
        self.assertEqual("- Candidate.", notes_for(SAMPLE, "0.1.0-rc.1"))

    def test_the_last_section_runs_to_the_end_of_the_file(self):
        self.assertTrue(notes_for(SAMPLE, "0.2.0").endswith("- Two things."))

    def test_a_version_without_a_section_is_an_error_so_a_release_cannot_ship_without_notes(self):
        with self.assertRaises(MissingNotes):
            notes_for(SAMPLE, "0.3.0")

    def test_the_unreleased_section_is_never_used_for_a_release(self):
        with self.assertRaises(MissingNotes):
            notes_for(SAMPLE, "Unreleased")

    def test_an_empty_section_is_an_error(self):
        with self.assertRaises(MissingNotes):
            notes_for("## [0.1.0] - 2026-10-30\n\n## [0.0.9] - 2026-10-01\n- x\n", "0.1.0")

    def test_a_version_is_matched_exactly_not_as_a_prefix(self):
        with self.assertRaises(MissingNotes):
            notes_for(SAMPLE, "0.1")


if __name__ == "__main__":
    unittest.main()
