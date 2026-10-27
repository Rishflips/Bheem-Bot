# Changelog

All notable changes to Bheem are listed here, newest first. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the versions follow [Semantic Versioning](https://semver.org/). A release is built from a tag `vX.Y.Z` only when this file has a section for that version, so write the section before tagging.

## [Unreleased]

First release candidate in preparation (v0.1.0). Everything below is built on the development branch and is not yet released.

### Added
- Type or hold to talk. Bheem turns what you say into one proposed action (reminder, task, note, calendar event, drafted WhatsApp or SMS message, or a question about your own notes) on a card. Nothing happens until you tap.
- A language model that runs on the phone (Gemma 4 E2B) with no account and no cloud. The `full` build downloads it on Wi-Fi, the `offline` build has no internet permission and takes a model file you place yourself.
- Voice: Gemma hears the audio itself; audio stays in memory and is never written to a file.
- Reminders that survive restarts and Doze, with a clear card when notifications, exact alarms or battery settings would make them late.
- Today screen with Needs you, Upcoming reminders and Recent activity, an activity history, Settings with Erase everything, and a Diagnostics screen of recent refusals that stays on the phone unless you share it.
- Encrypted storage (SQLCipher, key in the Android Keystore).

### Security
- Payments are refused. A drafted message must use the recipient and words you spoke or typed. Every action waits for a tap. A red-team test suite runs against these rules in CI.

### Known limits
- English only. Android 12 or newer. Release to result is about 12 to 14 seconds for a 6 to 9 second voice command on the test phone (measured on one phone).
