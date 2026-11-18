// const sqlite3 = require('sqlite3').verbose();
const Database = require('better-sqlite3');
const path = require('path');

const DB_PATH = path.join(__dirname, 'notes.db');
// const db = new sqlite3.Database(DB_PATH);
const db = new Database('./note.db');

db.exec(`
    CREATE TABLE IF NOT EXISTS notes_backup (
      id          INTEGER PRIMARY KEY AUTOINCREMENT,
      device_id   TEXT NOT NULL,
      note_uuid   TEXT NOT NULL,
      title       TEXT,
      content     TEXT,
      created_at  INTEGER,
      modified_at INTEGER,
      backup_at   INTEGER,
      UNIQUE(device_id, note_uuid)
    )
  `);

module.exports = db;