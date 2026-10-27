# Releasing Bheem

A release is a git tag. CI (`.github/workflows/release.yml`) tests it, builds both flavors twice, requires the two builds to match byte for byte, signs the first with the owner's key outside Gradle (`apksigner`), checks the signature against the certificate digest committed in `docs/release-cert.sha256`, and creates a **draft** GitHub Release with the APKs, `SHA256SUMS` and the pinned model hashes. A person publishes the draft (nightlies are published automatically).

## Tags

| Tag | Meaning | versionName | versionCode |
| :-- | :-- | :-- | :-- |
| `vX.Y.Z` | Release. Starts at `v0.1.0` | `X.Y.Z` | `((X*1000+Y)*1000+Z)*100+99` |
| `vX.Y.Z-rc.N` (N 1 to 98) | Release candidate, published as a pre-release | `X.Y.Z-rc.N` | same with `N` in place of 99 |
| `nightly-YYYYMMDD` | Test build, published as a pre-release | `0.0.0-nightly.YYYYMMDD` | days since 2026-01-01, plus one |

`scripts/release/version.py` does this and is tested. Every later version has a larger code, both flavors share one code, and every nightly code is below every release code, so a nightly can be replaced by a release but a release cannot be replaced by a nightly. Nightly tags are made by hand; nothing creates them automatically yet.

A tag is refused unless its commit is on `main`, `CHANGELOG.md` has a non-empty section for the version (a release candidate `X.Y.Z-rc.N` uses its own section if it has one, otherwise the `X.Y.Z` section; nightlies need none), the whole test suite and the quality gate pass, and the working tree is still clean after the build. A nightly date so far ahead that its code would reach the release codes (about 270 years after 2026-01-01) is refused, so a typo cannot give a nightly a code above the releases.

## One-time setup (owner only)

The release key belongs to the owner and must never be committed, pasted in an issue or sent to anyone, including an assistant.

1. Make the key. Use RSA, because its signatures are deterministic (an ECDSA signature changes every time, which would make the signed APK differ between runs). `keytool` makes a PKCS12 keystore, which has one password for the store and the key, so `BHEEM_KEY_PASSWORD` must equal `BHEEM_KEYSTORE_PASSWORD`:

   ```bash
   keytool -genkeypair -v -keystore bheem-release.jks -alias bheem -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Keep `bheem-release.jks` and its password somewhere safe and backed up. Losing it means no update can ever be installed over an existing install.
3. **Commit the certificate digest.** Run `keytool -list -v -keystore bheem-release.jks -alias bheem`, copy the SHA-256 line, and commit it as `docs/release-cert.sha256` (the hex digest, colons optional). The workflow refuses to sign unless the key it was given produces exactly that digest, and users compare downloads with the file in the repository, not with the release page, so a swapped key or a swapped secret is noticed.
4. Create a GitHub **Environment** named `release` (Settings, Environments) and put the four secrets in it, not in the repository secrets: `BHEEM_KEYSTORE_BASE64` (the file, base64-encoded on one line, for example `base64 -w0 bheem-release.jks | gh secret set BHEEM_KEYSTORE_BASE64 --env release`), `BHEEM_KEYSTORE_PASSWORD`, `BHEEM_KEY_ALIAS` and `BHEEM_KEY_PASSWORD`. Under that environment set a required reviewer (yourself) and a deployment rule that allows only tags matching `v*` and `nightly-*`.
5. Protect the repository: a ruleset so only you can create `v*` and `nightly-*` tags, and a protected `main` that requires review. The workflow file that runs for a tag is the one in the tagged commit, so anyone who can push a tag or edit a workflow on a branch could otherwise try to read the secrets; the environment rules and the tag ruleset are what stop that (the workflow alone cannot).
6. Decide the application id before the first public tag (below).

Only the `release` job sees the secrets, and it runs only after you approve the `release` environment. Its actions are pinned to full commit hashes, and its checkout does not keep the token.

## Cutting a release

1. Merge to `main` and wait for CI to pass there.
2. Add a `## [X.Y.Z] - YYYY-MM-DD` section to `CHANGELOG.md`, commit it to `main`.
3. Tag that commit and push the tag: `git tag vX.Y.Z && git push origin vX.Y.Z`. Do a `vX.Y.Z-rc.1` first.
4. Approve the `release` environment when the workflow asks, and watch it. If the two builds differ or the key does not match the committed digest, it stops before anything is published.
5. The workflow leaves a draft release. Follow `docs/INSTALL.md` on a phone that never had Bheem, with the APK from the draft, then publish the draft by hand.

## Reproducible build

Two jobs build the tagged commit independently, with different time zones and locales, without the Gradle build cache (and the second without the Gradle dependency cache); the release job runs `scripts/release/compare_apks.py` on the results and stops unless they are byte-identical. On the owner's Windows machine, two builds of one commit in two different folders on one day were byte-identical for both flavors (evidence file). That shows the build does not depend on its folder and has no run-to-run randomness. It does not show independence from the date, a Windows-versus-Linux difference, an unpinned JDK patch or build-tools revision, or a compromised dependency (which would give identical bytes in both copies). The Gradle distribution is pinned by checksum; the dependencies are not yet verified by hash. Release builds leave out the git revision note (it depends on whether a `.git` folder exists) and the Google dependency block (an encrypted blob that varies between builds).

## Rollback

Android refuses to install an older versionCode over a newer one, and removing the app deletes its data, so "install the previous APK" is not a rollback for people who already updated. A bad release is fixed by a newer release.

Database changes need care for a different reason: the app's database helper does not support going back to an older schema version, so an older release cannot open a database a newer release has upgraded. The rules are therefore:

- Every migration from every version that was ever shipped must keep the user's data (add tables or columns; never drop or rename one that is in use).
- `SchemaPinTest` fails when the schema statements change, as a reminder to write the migration and record it here. It guards only the creation statements, not the migration code. A test that opens a database created by an older release with data in it and checks the data survives is **not written yet**; it is needed before the first schema change after release (it needs the phone).
- The old "version 2" path, which drops and rebuilds the tables, is a development path that never shipped; remove it before the first public tag so no release has a data-loss upgrade.

Schema history: version 3 (the first public schema).

## Application id

The app id is still `app.bheem.bench`, a name from the benchmark phase. It is set in one place (`app/build.gradle.kts`) and the benchmark app (`bench/`) uses the same id, so installing one replaces the other. Changing the id after a public release means every user must uninstall and lose their data, so it must be decided before the first public tag.

## F-Droid

Not promised. F-Droid builds from source with a free toolchain and accepts prebuilt libraries from trusted Maven repositories (Google Maven is one) only if they are freely licensed ([policy](https://f-droid.org/docs/Inclusion_Policy/)). Whether the LiteRT-LM library and its native libraries pass that test has not been checked. Store text for a later submission is in `fastlane/metadata/`; it would use the `offline` flavor (the full flavor is not intended for F-Droid).

## Release gate for v0.01

Each line needs a measured value or a passing test next to it before the first release. The current state is in `docs/evidence/STAGE10-RELEASE-ENGINEERING.md` and the Stage 11 plan.
