# Stage 10 evidence: release engineering

Date 2026-10-27, on the owner's Windows machine and Moto g86 power (Android 16), one run each unless stated. Numbers are measured here or marked.

## What was built

- **Version from the tag** (`scripts/release/version.py`, 9 tests): `vX.Y.Z`, `vX.Y.Z-rc.N`, `nightly-YYYYMMDD`. The code is `((X*1000+Y)*1000+Z)*100+stage`, so every later version has a larger code; both flavors share it; releases start at v0.1.0 so every nightly code is below every release code; a code over Android's 2,100,000,000 is refused. `app/build.gradle.kts` reads `BHEEM_VERSION_NAME` and `BHEEM_VERSION_CODE` (a local build is `0.0.1-dev`, code 1).
- **No signing in Gradle** (changed after the review): Gradle never reads a key; the release workflow signs with `apksigner`. A half-set or non-numeric version variable fails the build with a clear message (tried).
- **Release APK settings**: no git-revision note and no Google dependency block in the APK, so a build from a source archive and one from a clone can match.
- **`scripts/release/compare_apks.py`** (6 tests): byte-identical or not, and which entries differ.
- **`scripts/release/changelog.py`** (7 tests): the release notes for a version come from `CHANGELOG.md`; a version with no or an empty section is an error, so a release cannot ship without notes.
- **`scripts/release/model_hashes.py`** (3 tests): the pinned model files, sizes and SHA-256 for the release page.
- **`.github/workflows/release.yml`** (12 structure tests in `test_release_workflow.py`): on a `v*` or `nightly-*` tag only. Job `verify`: tag format, tagged commit is an ancestor of `origin/main`, CHANGELOG notes, the whole test suite, the gates and the manifest check. Job `build`: two copies, each building both flavors with no daemon and no build cache, each required to leave the tree clean. Job `release`: the only job with secrets and with write permission; it requires the two copies to be byte-identical, then writes the keystore to a temp file removed by a trap, checks alignment, signs, verifies the signature, makes `SHA256SUMS` and the model list, and creates the release (candidates and nightlies as pre-releases).
- **Schema pin** (`SchemaPinTest`, 2 tests): fails when the database layout changes, so a change is a deliberate decision with a migration that keeps the previous release able to read it.
- `CHANGELOG.md`, `docs/RELEASING.md`, `docs/INSTALL.md`, store text in `fastlane/metadata/android/en-US/`. The release script tests also run in the regular CI.

## Checks that fail when they should (each reverted afterwards)

- The workflow structure test fails when a secret is added to the build job's environment, when write permission is given to another job, and when an action is pinned to a branch name.
- `SchemaPinTest` fails when a column's definition is changed.

## Measured

| Check | Result |
| :-- | :-- |
| Two builds of one commit in two different folders (one a git worktree) | Byte-identical for both flavors (full and offline) after the git-revision note was removed. Before that change one entry differed (`META-INF/version-control-info.textproto`), because AGP wrote a revision in one folder and "no git found" in the worktree. SHA-256 prefixes of the unsigned APKs: full b61675cd, offline a1455cf2 |
| Signing the same unsigned APK twice with a throwaway RSA key (3072 bits, created for this check, not in the repository) | Byte-identical signed files. `zipalign -c 4` passed and `apksigner verify` passed |
| Version in the built APK (`aapt2 dump badging`) | versionCode 100001, versionName 0.1.0-rc.1 for tag `v0.1.0-rc.1` |
| Release APK size | about 82 MB for each flavor (unsigned) |
| Clean install of the signed full release build on the phone (debug build uninstalled first, which deleted its data) | Installs; `pm dump` shows versionCode 100001 and not debuggable. First launch to a first card with a sideloaded 2.6 GB model: 77.3 s (target 90 s), including the model import and check. Approving the first reminder showed the notification prompt; afterwards the exact-alarm and battery guidance cards appeared and the reminder card noted that exact alarms are off |
| 16 KB page alignment (`zipalign -c -P 16 -v 4`) of the signed APK | Passed |
| Gradle wrapper | The distribution is now pinned by SHA-256 (published by Gradle); a fresh download in an empty Gradle home was accepted |
| Release script tests | 50 pass |
| Schema pin and the rest of the JVM suite | pass (see the verification run in the commit) |

## After the Opus review

The review (PASS WITH CHANGES, no Critical findings) led to these changes.

- The `release` job now uses a GitHub Environment (`release`) that the owner must create and protect (required reviewer, tag rule); its actions are pinned to full commit hashes, its checkouts keep no token, and it sets up a pinned Java and `build-tools;36.0.0`. The workflow alone cannot protect the secrets from a pushed branch or tag; the environment rules and a tag ruleset (owner settings, in `docs/RELEASING.md`) do.
- The signing certificate must match `docs/release-cert.sha256`, a file the owner commits after making the key; the install guide tells users to compare against the repository copy. The workflow fails until that file exists, on purpose.
- A release candidate uses its own CHANGELOG section or falls back to the `X.Y.Z` section (the first planned tag would otherwise have failed); headings inside code fences are ignored and a section with only sub-headings is empty.
- The release is created as a draft; a person publishes it after the clean-phone check (nightlies are published at once).
- The two build copies differ in time zone and locale, the second has no Gradle cache, and the build job checks the version in the APK with `aapt2`.
- A nightly whose code would reach the release codes is refused. `compare_apks.py` refuses unknown flags and the same file twice.
- `MODEL.txt` carries the model URL; `INSTALL.md` states the storage need for the offline build (about twice the model size at import), the `adb push` command, and which numbers are targets or estimates.
- Tests: the tautology and a self-comparison were removed; new structure tests cover the environment, pinning, certificate pin, draft, tool pins and the build job holding no secrets or token.
- `docs/RELEASING.md` rollback and schema rules were rewritten: an older release cannot open a newer schema, and the pin guards creation statements only.

## Not done or not verified (still open)

- A test that opens a database made by an older release, with data, and checks it survives a migration (needs the phone), and removing the never-shipped version 2 rebuild path from `onUpgrade` before the first public tag.
- Dependency hash verification for Gradle (only the Gradle distribution is pinned).
- The owner's one-time setup: the key, `docs/release-cert.sha256`, the `release` environment with its secrets, and the tag ruleset.
- Items from the first list:

- **The release workflow has never run.** There is no tag, no key and no secrets yet; it is checked by its structure tests and by running its steps by hand (version, compare, sign, verify) on this machine. A first `v0.1.0-rc.1` tag is the real test.
- **The owner must make the release key and do the one-time setup** (see `docs/RELEASING.md`). The throwaway key used here is not a release key and was never stored in the repository.
- **Reproducibility is shown on one Windows machine with two folders.** The CI runner is Linux; a Windows build and a Linux build of one commit may differ and were not compared.
- **A real install from a release page on a phone that never had the app was not done** (no release exists). `docs/INSTALL.md` is untested as a whole; its first-run steps match what the signed build did here.
- **F-Droid is not promised.** The policy accepts prebuilt libraries from Google Maven only if freely licensed; the licence of LiteRT-LM and its native libraries was not verified.
- **The application id is still `app.bheem.bench`** and the benchmark app shares it. It must be decided before the first public tag (decision for the owner).
- Nightly tags are made by hand; nothing creates them.
- Rollback: Android refuses a lower versionCode, so a bad release is fixed by a newer one; the plan's "install the previous APK" does not work for people who already updated. `docs/RELEASING.md` says so.
- The phone now has the release-signed build; installing a debug build over it needs an uninstall first (a signature mismatch), which deletes the app's data.
