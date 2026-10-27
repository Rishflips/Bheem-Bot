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

    def test_a_release_candidate_without_its_own_section_uses_the_section_of_its_release(self):
        self.assertEqual("### Added\n- First release.\n- Voice.", notes_for(SAMPLE, "0.1.0-rc.7"))
        with self.assertRaises(MissingNotes):
            notes_for(SAMPLE, "0.3.0-rc.1")

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

    def test_a_section_with_only_sub_headings_is_empty(self):
        with self.assertRaises(MissingNotes):
            notes_for("## [0.1.0] - 2026-10-30\n\n### Added\n\n### Fixed\n\n## [0.0.1] - x\n- y\n", "0.1.0")

    def test_a_heading_inside_a_code_block_does_not_end_the_section(self):
        text = "## [0.1.0] - 2026-10-30\n\n- A\n\n```\n## [0.0.9] - fake\n```\n\n- B\n\n## [0.0.1] - 2026-01-01\n- old\n"
        notes = notes_for(text, "0.1.0")
        self.assertIn("- B", notes)
        self.assertNotIn("- old", notes)

    def test_a_version_is_matched_exactly_not_as_a_prefix(self):
        with self.assertRaises(MissingNotes):
            notes_for(SAMPLE, "0.1")


if __name__ == "__main__":
    unittest.main()
