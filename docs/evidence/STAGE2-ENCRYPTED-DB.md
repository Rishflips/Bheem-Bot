# Stage 2 evidence: the encrypted database on the phone

Phone: Moto g86 power (Android 16), debug build, 2026-10-24. Commands and output as run (paths shortened).

Instrumented tests (`:storage:connectedDebugAndroidTest`, 22 tests, 0 failures; full list at the end of this file): wrong key refused; ciphertext header; schema version 3 with indexes; rising ledger ids; ledger rules on the real store; provenance and zone round trip; feed join; rollback leaves nothing; nested transactions; full-text search (case, accents, filler words, removal, VACUUM, update trigger); change signal once per commit and none after a rollback or a failed inner transaction; key wrapped and erased; erase removes database and key; legacy plain database deleted; key lost is reported and the database is left alone; layout 2 is rebuilt. Ledger read timing from the logcat line the test writes: 20 rounds of history plus last event over 1,000 rows took 21 ms and 22 ms in two runs (measured; target under 50 ms per read).

App run: cold start `am start -W` TotalTime 1071, 865 and 802 ms (measured). A typed command created a note in about 14 s (model warm) and a question about it was answered from the full-text index in about 6 s.

```
$ adb shell run-as app.bheem.bench ls -la databases no_backup/database.key
-rw------- 1 u0_a864 u0_a864   60 2026-10-24 23:06 no_backup/database.key
-rw------- 1 u0_a864 u0_a864 65536 2026-10-24 23:30 bheem-v2.db

$ adb exec-out run-as app.bheem.bench cat databases/bheem-v2.db > enc2.db
size 65536 first16 8778ec47c32fc4b691bf5d961cf7ba9a
plaintext "locker" present: False | "CREATE TABLE": False | "SQLite format 3": False
python sqlite3 (library 3.49.1): cannot read: file is not a database
```

The key file is 60 bytes: 12 IV + 32 key + 16 GCM tag. The file name `bheem-v2.db` is kept although the schema version is 3.

## Instrumented test result (`:storage:connectedDebugAndroidTest`, run on the phone after the review fixes)

`<testsuite name="app.bheem.storage.StorageInstrumentedTest" tests="22" failures="0" errors="0" skipped="0" time="22.662" timestamp="2026-10-24T18:09:50" hostname="localhost">`

| Test | Seconds |
| :-- | :-- |
| aLegacyPlainDatabaseFromTheFirstDevBuildIsDeleted | 0.631 |
| proposalsWhoseLastEventIsApprovedAreListedForTheStartupSweep | 0.991 |
| aCommittedWriteSignalsChangesOnceAndARollbackDoesNot | 1.166 |
| memorySearchUsesFullTextAndIgnoresCaseAndAccents | 0.682 |
| removingARecordRemovesItFromSearchAndKindQueriesWork | 0.678 |
| eraseEverythingRemovesTheDatabaseFilesAndTheKey | 1.305 |
| anOuterTransactionThatSwallowedAFailedInnerOneReportsTheRollback | 0.633 |
| theLedgerAssignsRisingIdsAndAnswersWithoutScanning | 0.713 |
| theSearchIndexSurvivesVacuumAndUpdates | 0.714 |
| anInstanceEraseClosesTheDatabaseThenRemovesTheKeyAndTheFiles | 1.363 |
| theSchemaIsVersionThreeWithTheIndexesAndTables | 0.647 |
| nestedTransactionsCommitTogether | 0.712 |
| theLedgerRulesWorkOnTheRealStore | 0.658 |
| aDevelopmentDatabaseFromTheUnreleasedVersionTwoLayoutIsRecreated | 1.432 |
| theDatabaseIsEncryptedAndTheWrongKeyIsRefused | 2.08 |
| theKeyIsCreatedOnceKeptOnlyWrappedAndErasedOnRequest | 0.831 |
| aFailedInnerTransactionCaughtByTheOuterOneDoesNotSignalOrPersist | 1.026 |
| aProposalKeepsItsProvenanceVersionsAndZoneAwareTimeThroughAReload | 1.358 |
| aDatabaseWhoseKeyFileIsGoneIsReportedAsLostAndLeftAlone | 1.362 |
| aThousandLedgerRowsAreReadFast | 0.877 |
| theFeedRowsJoinTheLastLedgerEntryAndFeedBuildsItems | 0.763 |
| aFailingTransactionLeavesNothingBehind | 0.705 |
