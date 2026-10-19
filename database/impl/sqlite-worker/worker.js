// Implements the WebWorkerSQLiteDriver protocol documented by AndroidX SQLite.
import sqlite3InitModule from "@sqlite.org/sqlite-wasm";

let sqlite;
let pool;
let nextDatabaseId = 0;
let nextStatementId = 0;
let shuttingDown = false;
const databases = new Map();
const statements = new Map();
const persistentDatabases = new Set();
let releaseStorageLock;
let storageLockRequest;

async function acquireStorageLock() {
  if (releaseStorageLock) return;
  let granted;
  let rejected;
  const acquired = new Promise((resolve, reject) => {
    granted = resolve;
    rejected = reject;
  });
  storageLockRequest = navigator.locks.request(
    "storymile-sqlite",
    { ifAvailable: true },
    (lock) => {
      if (!lock) {
        rejected(new Error("Storymile database is open in another tab."));
        return;
      }
      granted();
      return new Promise((resolve) => { releaseStorageLock = resolve; });
    },
  );
  storageLockRequest.catch(rejected);
  await acquired;
}

async function pauseStorage() {
  if (pool) await pool.pauseVfs();
  if (releaseStorageLock) {
    releaseStorageLock();
    releaseStorageLock = undefined;
    await storageLockRequest;
  }
}

async function dispatch({ id, data }) {
  switch (data.cmd) {
    case "open": {
      sqlite ??= await sqlite3InitModule();
      let database;
      if (data.fileName === ":memory:") {
        database = new sqlite.oo1.DB(":memory:");
      } else {
        // Reject competing workers before VFS initialization can touch their files.
        await acquireStorageLock();
        try {
          if (!pool) {
            // This VFS works on static hosts without COOP/COEP response headers.
            pool = await sqlite.installOpfsSAHPoolVfs({ directory: ".storymile-sqlite" });
          } else {
            await pool.unpauseVfs();
          }
          await pool.reserveMinimumCapacity(pool.getFileCount() + 4);
          database = new pool.OpfsSAHPoolDb(data.fileName, "c");
        } catch (error) {
          if (persistentDatabases.size === 0) await pauseStorage();
          throw error;
        }
      }
      const databaseId = nextDatabaseId++;
      databases.set(databaseId, database);
      if (data.fileName !== ":memory:") persistentDatabases.add(databaseId);
      postMessage({ id, data: { databaseId } });
      break;
    }
    case "prepare": {
      const database = databases.get(data.databaseId);
      if (!database) throw new Error("Unknown database");
      const statement = database.prepare(data.sql);
      const statementId = nextStatementId++;
      statements.set(statementId, { statement, databaseId: data.databaseId });
      const columnNames = [];
      for (let column = 0; column < statement.columnCount; column++) {
        columnNames.push(sqlite.capi.sqlite3_column_name(statement, column));
      }
      postMessage({ id, data: {
        statementId,
        parameterCount: sqlite.capi.sqlite3_bind_parameter_count(statement),
        columnNames,
      } });
      break;
    }
    case "step": {
      const entry = statements.get(data.statementId);
      if (!entry) throw new Error("Unknown statement");
      const { statement } = entry;
      statement.reset();
      statement.clearBindings();
      data.bindings.forEach((value, index) => statement.bind(index + 1, value));
      const rows = [];
      const columnTypes = [];
      while (statement.step()) {
        if (columnTypes.length === 0) {
          for (let column = 0; column < statement.columnCount; column++) {
            columnTypes.push(sqlite.capi.sqlite3_column_type(statement, column));
          }
        }
        rows.push(statement.get([]));
      }
      // A statement at SQLITE_ROW can keep a read lock until reset.
      statement.reset();
      postMessage({ id, data: { rows, columnTypes } });
      break;
    }
    case "close": {
      if (data.statementId != null) {
        statements.get(data.statementId)?.statement.finalize();
        statements.delete(data.statementId);
      }
      if (data.databaseId != null) {
        for (const [key, entry] of statements) {
          if (entry.databaseId === data.databaseId) {
            entry.statement.finalize();
            statements.delete(key);
          }
        }
        databases.get(data.databaseId)?.close();
        databases.delete(data.databaseId);
        persistentDatabases.delete(data.databaseId);
        if (persistentDatabases.size === 0) await pauseStorage();
        if (databases.size === 0) {
          if (shuttingDown) {
            postMessage({ id: -1, data: {} });
            self.close();
          }
        }
      }
      break;
    }
    case "shutdown":
      shuttingDown = true;
      if (databases.size === 0) {
        await pauseStorage();
        postMessage({ id, data: {} });
        self.close();
      }
      break;
    default:
      throw new Error(`Unknown SQLite command: ${data.cmd}`);
  }
}

// Preserve message order across asynchronous initialization, open, and close operations.
let requests = Promise.resolve();
self.onmessage = ({ data }) => {
  requests = requests.then(() => dispatch(data)).catch((error) => {
    postMessage({ id: data.id, error: error.message });
  });
};
