# Install Bheem

This guide is for a tagged release on the project's GitHub Releases page. It has not yet been followed on a phone that never had Bheem (see `docs/evidence/STAGE10-RELEASE-ENGINEERING.md` for what was and was not tested).

## What you need

- Android 12 or newer (the app's minimum) and about 6 GB of memory or more (a target, from the model registry). Free storage: about 3 GB for the **full** build (the 2.6 GB model plus working space) and about 6 GB for the **offline** build, because the model file you copy over is copied into the app's private storage and checked while the first copy still exists. These storage figures are estimates from the size of the model file, not measured on a nearly full phone.
- The release page lists, for each version: `Bheem-VERSION-offline.apk`, `Bheem-VERSION-full.apk`, `SHA256SUMS`, `MODEL.txt` (the model file's name, size and SHA-256) and the signing certificate fingerprint.

## Pick a build

- **full**: downloads the model (about 2.6 GB) on Wi-Fi after you tap Download. It has the internet permission for that one purpose.
- **offline**: has no internet permission. You copy the model file to the phone yourself (below).

You can only have one of them installed at a time. They share one app id.

## Check the download

On a computer, in the folder with the files:

```bash
sha256sum -c SHA256SUMS
```

Both lines must say OK. To check who signed it (needs the Android SDK build tools):

```bash
apksigner verify --print-certs Bheem-VERSION-full.apk
```

The certificate SHA-256 digest must match the one in `docs/release-cert.sha256` **in the project repository**, not only the one on the release page: anyone who could replace the APK on the release page could replace the number next to it. If you downloaded only one APK, use `sha256sum -c --ignore-missing SHA256SUMS`. If any check fails, do not install.

## Install

1. Copy the APK to the phone (USB, or download it on the phone from the release page).
2. Open it. Android asks to allow installs from the app you opened it with (your browser or file manager). Allow it for this install.
3. Tap Install, then open Bheem.

Or from a computer with USB debugging on: `adb install Bheem-VERSION-full.apk`.

## First run

1. Read the one-screen welcome and tap Get started.
2. **full**: tap Download on Wi-Fi on the card that appears. It resumes if interrupted, and checks the file when finished.
3. **offline**: download the model file from the address on its line in `MODEL.txt`, check its size and SHA-256 against that line, and put it where the card says. On Android 11 or newer file managers cannot open another app's folder, so use a computer with USB debugging: `adb shell mkdir -p /sdcard/Android/data/APP_ID/files/models` and then `adb push gemma-4-E2B-it.litertlm /sdcard/Android/data/APP_ID/files/models/` (APP_ID is `app.bheem.bench` until the final id is chosen). Open Bheem again. Bheem checks the file against the fingerprint before using it.
4. Type "remind me tomorrow at 9 AM to call Ravi" and tap OK on the card. Android asks for the notification permission at this moment.

## Update and remove

- To update, install the newer APK over the old one. Your data stays.
- Android does not let you install an older version over a newer one without uninstalling first, and uninstalling deletes your data. If a release is bad, the fix is a newer version.
- To remove everything, use Settings, Erase everything, or uninstall the app.

## Report a problem

Settings, Diagnostics lists the latest refusals and why. It stays on the phone; you choose whether to share it. Never post anything with your own notes or numbers in it.
