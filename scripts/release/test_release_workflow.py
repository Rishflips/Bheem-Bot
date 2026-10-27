"""Structure checks on .github/workflows/release.yml. The workflow cannot run here, so these read its text for the rules that matter."""
import re
import unittest
from pathlib import Path

WORKFLOW = (Path(__file__).resolve().parents[2] / ".github" / "workflows" / "release.yml").read_text(encoding="utf-8")
LINES = WORKFLOW.splitlines()


def job_text(name: str) -> str:
    start = next(i for i, line in enumerate(LINES) if line == f"  {name}:")
    end = next((i for i in range(start + 1, len(LINES)) if re.fullmatch(r"  [a-z][a-z0-9-]*:", LINES[i])), len(LINES))
    return "\n".join(LINES[start:end])


class ReleaseWorkflowTest(unittest.TestCase):
    def test_it_runs_only_for_release_and_nightly_tags(self):
        trigger = WORKFLOW.split("\njobs:")[0]
        self.assertIn("tags:", trigger)
        self.assertIn("'v*'", trigger)
        self.assertIn("'nightly-*'", trigger)
        for banned in ("pull_request", "branches:", "workflow_dispatch", "schedule:"):
            self.assertNotIn(banned, trigger)

    def test_the_default_permission_is_read_and_write_is_only_in_the_release_job(self):
        self.assertRegex(WORKFLOW, r"(?m)^permissions:\n  contents: read\n")
        self.assertEqual(1, WORKFLOW.count("contents: write"))
        self.assertIn("contents: write", job_text("release"))

    def test_secrets_appear_only_as_environment_values_in_the_release_job(self):
        for line in LINES:
            if "secrets." in line:
                self.assertRegex(line, r"^\s+[A-Z][A-Z0-9_]+: \$\{\{ secrets\.[A-Z0-9_]+ \}\}$", line)
        for job in ("verify", "build"):
            self.assertNotIn("secrets.", job_text(job), f"the {job} job must not see a secret")
        self.assertNotIn("GITHUB_TOKEN", job_text("build"))

    def test_nothing_prints_a_secret_or_turns_on_command_tracing(self):
        for banned in ("set -x", "bash -x", "BHEEM_KEYSTORE_PASSWORD\"", "echo \"$BHEEM_", "echo $BHEEM_", "echo ${{", "cat \"$KEYSTORE"):
            self.assertNotIn(banned, WORKFLOW)

    def test_the_tag_is_checked_before_anything_is_built(self):
        verify = job_text("verify")
        self.assertIn("scripts/release/version.py", verify)
        self.assertIn("merge-base --is-ancestor", verify)
        self.assertIn("origin/main", verify)
        self.assertIn("scripts/release/changelog.py", verify)

    def test_the_whole_test_suite_and_the_gates_run_before_a_release(self):
        verify = job_text("verify")
        for needed in (":models:test", ":agent-core:test", ":eval:test", ":eval:gate", "scripts/check-tests-ran.py", "scripts/check-network-use.sh", "scripts/check-ui-strings.sh", ":app:verifyManifests", "python3 -m unittest"):
            self.assertIn(needed, verify)
        self.assertIn("needs: verify", job_text("build"))

    def test_both_flavors_are_built_twice_in_separate_jobs_without_a_daemon_and_the_tree_must_stay_clean(self):
        build = job_text("build")
        self.assertIn("- copy: a", build)
        self.assertIn("- copy: b", build)
        self.assertIn(":app:assembleFullRelease", build)
        self.assertIn(":app:assembleOfflineRelease", build)
        self.assertIn("--no-daemon", build)
        self.assertIn("git status --porcelain", build)
        self.assertIn("BHEEM_VERSION_CODE", build)
        self.assertIn("upload-artifact", build)

    def test_the_release_job_needs_both_copies_to_match_byte_for_byte_before_signing(self):
        release = job_text("release")
        self.assertIn("needs: [verify, build]", release)
        compare = release.index("compare_apks.py")
        sign = re.search(r'apksigner"? sign', release).start()
        verify = re.search(r'apksigner"? verify', release).start()
        self.assertLess(compare, sign)
        self.assertGreater(verify, sign)

    def test_the_keystore_is_written_to_a_temp_file_used_once_and_removed(self):
        release = job_text("release")
        self.assertIn("mktemp", release)
        self.assertIn("base64 -d", release)
        self.assertIn("trap", release)

    def test_the_release_lists_checksums_and_marks_candidates_and_nightlies_as_prereleases(self):
        release = job_text("release")
        self.assertIn("sha256sum", release)
        self.assertIn("SHA256SUMS", release)
        self.assertIn("--prerelease", release)
        self.assertIn("--verify-tag", release)
        self.assertIn("gh release create", release)

    def test_the_model_hash_is_published_with_the_release(self):
        self.assertIn("models.json", job_text("release"))

    def test_actions_are_pinned_to_a_major_version_at_least(self):
        for line in LINES:
            m = re.search(r"uses: ([^@\s]+)@(\S+)", line)
            if m:
                self.assertRegex(m.group(2), r"^(v\d+|[0-9a-f]{40})$", line)

    def test_the_build_job_has_no_token_and_no_secrets_in_any_form(self):
        build = job_text("build")
        for banned in ("secrets", "github.token", "toJSON", "GITHUB_TOKEN", "GH_TOKEN"):
            self.assertNotIn(banned, build, banned)

    def test_the_release_job_uses_a_protected_environment_and_does_not_keep_the_token_in_the_checkout(self):
        release = job_text("release")
        self.assertIn("environment: release", release)
        self.assertEqual(release.count("actions/checkout@"), release.count("persist-credentials: false"))
        for line in job_text("verify").splitlines() + job_text("build").splitlines():
            if "actions/checkout@" in line:
                self.assertIn("persist-credentials: false", WORKFLOW[WORKFLOW.index(line):WORKFLOW.index(line) + 200])

    def test_every_action_in_the_release_job_is_pinned_to_a_full_commit(self):
        for line in job_text("release").splitlines():
            m = re.search(r"uses: ([^@\s]+)@(\S+)", line)
            if m:
                self.assertRegex(m.group(2), r"^[0-9a-f]{40}$", line)

    def test_the_signing_tools_and_java_are_pinned_in_the_release_job(self):
        release = job_text("release")
        self.assertIn("setup-java", release)
        self.assertIn("build-tools;36.0.0", release)
        self.assertNotIn("sort -V", release)
        self.assertIn("-P 16", release)

    def test_the_signing_certificate_must_match_the_digest_committed_in_the_repository(self):
        release = job_text("release")
        self.assertIn("docs/release-cert.sha256", release)
        sign = re.search(r'apksigner"? sign', release).start()
        self.assertGreater(release.index('test "$actual" = "$expected"'), sign)

    def test_a_release_is_a_draft_until_a_person_publishes_it_except_nightlies(self):
        release = job_text("release")
        self.assertIn("--draft", release)
        self.assertIn("gh release edit", release)

    def test_the_two_build_copies_differ_in_time_zone_locale_and_gradle_cache(self):
        build = job_text("build")
        self.assertIn("TZ:", build)
        self.assertIn("LANG:", build)
        self.assertIn("cache-disabled", build)
        self.assertIn("aapt2", build)
        self.assertIn("build-tools;36.0.0", build)


if __name__ == "__main__":
    unittest.main()
