# Releasing Bheem

A release is a git tag. CI (`.github/workflows/release.yml`) tests it, builds both flavors twice, requires the two builds to match byte for byte, signs the first with the owner's key, checks the signature, and publishes a GitHub Release with the APKs, `SHA256SUMS` and the pinned model hashes.

## Tags

| Tag | Meaning | versionName | versionCode |
| :-- | :-- | :-- | :-- |
| `vX.Y.Z` | Release. Starts at `v0.1.0` | `X.Y.Z` | `((X*1000+Y)*1000+Z)*100+99` |
| `vX.Y.Z-rc.N` (N 1 to 98) | Release candidate, published as a pre-release | `X.Y.Z-rc.N` | same with `N` in place of 99 |
| `nightly-YYYYMMDD` | Test build, published as a pre-release | `0.0.0-nightly.YYYYMMDD` | days since 2026-01-01, plus one |

`scripts/release/version.py` does this and is tested. Every later version has a larger code, both flavors share one code, and every nightly code is below every release code, so a nightly can be replaced by a release but a release cannot be replaced by a nightly. Nightly tags are made by hand; nothing creates them automatically yet.

A tag is refused unless its commit is on `main`, `CHANGELOG.md` has a non-empty section for the version (not for nightlies), the whole test suite and the quality gate pass, and the working tree is still clean after the build.

## One-time setup (owner only)

The release key belongs to the owner and must never be committed, pasted in an issue or sent to anyone, including an assistant.

1. Make the key. Use RSA, because its signatures are deterministic (an ECDSA signature changes every time, which would make the signed APK differ between runs):

   ```bash
   keytool -genkeypair -v -keystore bheem-release.jks -alias bheem -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Keep `bheem-release.jks` and its passwords somewhere safe and backed up. Losing it means no update can ever be installed over an existing install.
3. Add four repository secrets (Settings, Secrets and variables, Actions): `BHEEM_KEYSTORE_BASE64` (the file, base64-encoded on one line), `BHEEM_KEYSTORE_PASSWORD`, `BHEEM_KEY_ALIAS` and `BHEEM_KEY_PASSWORD`. For example, with the GitHub CLI: `base64 -w0 bheem-release.jks | gh secret set BHEEM_KEYSTORE_BASE64`.
4. Decide the application id before the first public tag (below).

Only the `release` job sees these secrets. The build jobs have none, so the code being built cannot read the key.

## Cutting a release

1. Merge to `main` and wait for CI to pass there.
2. Add a `## [X.Y.Z] - YYYY-MM-DD` section to `CHANGELOG.md`, commit it to `main`.
3. Tag that commit and push the tag: `git tag vX.Y.Z && git push origin vX.Y.Z`. Do a `vX.Y.Z-rc.1` first.
4. Watch the `release` workflow. If the two builds differ, it stops before signing and nothing is published.
5. Follow `docs/INSTALL.md` on a phone that never had Bheem, with the APK from the release page, before announcing.

## Reproducible build

Two jobs build the tagged commit independently without the Gradle build cache; the release job runs `scripts/release/compare_apks.py` on the results and stops unless they are byte-identical. On the owner's Windows machine, two builds of one commit in two different folders were byte-identical for both flavors (evidence file). This proves the build does not depend on its folder or on timing. It does not prove a build on another operating system or with another JDK gives the same bytes; the CI runner is Linux with Temurin 17 and the version of Gradle and the Android Gradle Plugin are pinned in the repository. Release builds leave out the git revision note and the Google dependency block, which would otherwise depend on how the source was fetched.

## Rollback

Android refuses to install an older versionCode over a newer one, and removing the app deletes its data, so "install the previous APK" is not a rollback for people who already updated. A bad release is fixed by a newer release. To make that safe:

- A schema change must only add tables or columns, and the previous release must still be able to read the database. `SchemaPinTest` fails when the schema changes, as a reminder to do this on purpose and record it here.
- Keep the model registry and the on-disk file layout readable by the previous release.

Schema history: version 3 (the first public schema).

## Application id

The app id is still `app.bheem.bench`, a name from the benchmark phase. It is set in one place (`app/build.gradle.kts`) and the benchmark app (`bench/`) uses the same id, so installing one replaces the other. Changing the id after a public release means every user must uninstall and lose their data, so it must be decided before the first public tag.

## F-Droid

Not promised. F-Droid builds from source with a free toolchain and accepts prebuilt libraries from trusted Maven repositories (Google Maven is one) only if they are freely licensed ([policy](https://f-droid.org/docs/Inclusion_Policy/)). Whether the LiteRT-LM library and its native libraries pass that test has not been checked. Store text for a later submission is in `fastlane/metadata/`; it would use the `offline` flavor.

## Release gate for v0.01

Each line needs a measured value or a passing test next to it before the first release. The current state is in `docs/evidence/STAGE10-RELEASE-ENGINEERING.md` and the Stage 11 plan.
