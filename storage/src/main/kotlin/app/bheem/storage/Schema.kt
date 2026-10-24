package app.bheem.storage

internal object Schema {
    val dropAll = listOf(
        "DROP TRIGGER IF EXISTS memory_ai",
        "DROP TRIGGER IF EXISTS memory_ad",
        "DROP TRIGGER IF EXISTS memory_au",
        "DROP TABLE IF EXISTS memory_fts",
        "DROP TABLE IF EXISTS memory",
        "DROP TABLE IF EXISTS ledger",
        "DROP TABLE IF EXISTS proposals",
        "DROP TABLE IF EXISTS reminders",
    )

    val current = listOf(
        """
        CREATE TABLE proposals (
            id TEXT PRIMARY KEY,
            action TEXT NOT NULL,
            args TEXT NOT NULL,
            level TEXT NOT NULL,
            requires_tap INTEGER NOT NULL,
            created_at_epoch INTEGER NOT NULL,
            zone_id TEXT NOT NULL,
            user_text TEXT NOT NULL,
            undo_token TEXT,
            provenance TEXT NOT NULL,
            source TEXT NOT NULL,
            prompt_version TEXT NOT NULL,
            model_id TEXT NOT NULL,
            backend TEXT NOT NULL
        )
        """,
        "CREATE INDEX proposals_created ON proposals(created_at_epoch)",
        """
        CREATE TABLE ledger (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            at_epoch INTEGER NOT NULL,
            zone_id TEXT NOT NULL,
            proposal_id TEXT NOT NULL,
            event TEXT NOT NULL,
            action TEXT NOT NULL,
            source TEXT NOT NULL,
            detail TEXT NOT NULL
        )
        """,
        "CREATE INDEX ledger_proposal ON ledger(proposal_id, id)",
        """
        CREATE TABLE memory (
            seq INTEGER PRIMARY KEY,
            id TEXT NOT NULL UNIQUE,
            kind TEXT NOT NULL,
            text TEXT NOT NULL,
            created_at_epoch INTEGER NOT NULL,
            zone_id TEXT NOT NULL,
            merchant TEXT,
            amount TEXT,
            direction TEXT
        )
        """,
        "CREATE INDEX memory_kind ON memory(kind)",
        "CREATE VIRTUAL TABLE memory_fts USING fts5(text, content='memory', content_rowid='seq', tokenize='unicode61')",
        "CREATE TRIGGER memory_ai AFTER INSERT ON memory BEGIN INSERT INTO memory_fts(rowid, text) VALUES (new.seq, new.text); END",
        "CREATE TRIGGER memory_ad AFTER DELETE ON memory BEGIN INSERT INTO memory_fts(memory_fts, rowid, text) VALUES ('delete', old.seq, old.text); END",
        "CREATE TRIGGER memory_au AFTER UPDATE OF text ON memory BEGIN INSERT INTO memory_fts(memory_fts, rowid, text) VALUES ('delete', old.seq, old.text); INSERT INTO memory_fts(rowid, text) VALUES (new.seq, new.text); END",
        """
        CREATE TABLE reminders (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            proposal_id TEXT NOT NULL,
            text TEXT NOT NULL,
            fire_at_epoch INTEGER NOT NULL,
            zone_id TEXT NOT NULL,
            state TEXT NOT NULL
        )
        """,
        "CREATE INDEX reminders_due ON reminders(state, fire_at_epoch)",
    ).map { it.trimIndent() }
}
