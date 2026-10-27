import unittest

from version import InvalidTag, from_tag

MAX_VERSION_CODE = 2_100_000_000


class VersionFromTagTest(unittest.TestCase):
    def test_a_release_tag_gives_semver_name_and_a_final_code(self):
        v = from_tag("v0.1.0")
        self.assertEqual(("0.1.0", "release"), (v.name, v.channel))
        self.assertEqual(100099, v.code)

    def test_a_release_candidate_sorts_below_its_release(self):
        self.assertLess(from_tag("v0.1.0-rc.1").code, from_tag("v0.1.0-rc.2").code)
        self.assertLess(from_tag("v0.1.0-rc.98").code, from_tag("v0.1.0").code)
        self.assertEqual("rc", from_tag("v0.1.0-rc.1").channel)
        self.assertEqual("0.1.0-rc.1", from_tag("v0.1.0-rc.1").name)

    def test_codes_increase_with_every_part_of_the_version(self):
        order = ["v0.1.0-rc.1", "v0.1.0", "v0.1.1-rc.1", "v0.1.1", "v0.2.0", "v0.10.0", "v1.0.0-rc.1", "v1.0.0", "v1.0.1", "v1.1.0", "v2.0.0"]
        codes = [from_tag(t).code for t in order]
        self.assertEqual(sorted(codes), codes)
        self.assertEqual(len(set(codes)), len(codes))

    def test_every_code_fits_the_android_limit_up_to_the_largest_supported_version(self):
        self.assertLessEqual(from_tag("v20.999.999").code, MAX_VERSION_CODE)
        with self.assertRaises(InvalidTag):
            from_tag("v21.0.0")
        with self.assertRaises(InvalidTag):
            from_tag("v1.1000.0")
        with self.assertRaises(InvalidTag):
            from_tag("v1.0.1000")

    def test_a_nightly_tag_is_a_separate_low_numbered_channel(self):
        v = from_tag("nightly-20261027")
        self.assertEqual(("0.0.0-nightly.20261027", "nightly"), (v.name, v.channel))
        self.assertLess(v.code, from_tag("v0.1.0-rc.1").code)
        self.assertLess(from_tag("nightly-20261027").code, from_tag("nightly-20261028").code)
        self.assertLess(from_tag("nightly-20261231").code, from_tag("nightly-20270101").code)

    def test_a_bad_nightly_date_is_refused(self):
        for tag in ["nightly-20261340", "nightly-2026", "nightly-20260230", "nightly-19990101", "nightly-30261027", "nightly-22991231"]:
            with self.assertRaises(InvalidTag, msg=tag):
                from_tag(tag)

    def test_anything_else_is_refused(self):
        for tag in ["", "0.1.0", "v1", "v1.2", "v1.2.3.4", "v01.2.3", "v1.02.3", "v1.2.3-beta.1", "v1.2.3-rc.0", "v1.2.3-rc.99", "v1.2.3-rc", "v1.2.3+build", "vx.y.z", "refs/tags/v1.2.3", "latest", " v1.2.3", "v1.2.3\n"]:
            with self.assertRaises(InvalidTag, msg=repr(tag)):
                from_tag(tag)

    def test_the_latest_accepted_nightly_is_still_below_the_first_release(self):
        self.assertLess(from_tag("nightly-22990331").code, from_tag("v0.1.0-rc.1").code)

    def test_releases_start_at_0_1_0_so_every_nightly_code_stays_below_every_release(self):
        for tag in ["v0.0.1", "v0.0.9-rc.1", "v0.0.0"]:
            with self.assertRaises(InvalidTag, msg=tag):
                from_tag(tag)
        self.assertLess(from_tag("nightly-21000101").code, from_tag("v0.1.0-rc.1").code)


if __name__ == "__main__":
    unittest.main()
