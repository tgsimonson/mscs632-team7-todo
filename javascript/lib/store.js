// storage layer for the shared tasks.json
// all file access is asynchronous so that concurrent workers genuinely
// interleave at await points; synchronous i/o would hide the race the
// concurrency test is meant to expose
const fs = require('fs/promises');
const path = require('path');
const filelock = require('./filelock');

const SCHEMA_VERSION = 1;
const DATA_FILE = path.join(__dirname, '..', '..', 'data', 'tasks.json');

// StorageError carries the exit code the spec assigns to storage failures
class StorageError extends Error {
  constructor(message) {
    super(message);
    this.code = 3;
  }
}

class NotFoundError extends Error {
  constructor(message) {
    super(message);
    this.code = 2;
  }
}

// promise-chain mutex
// javascript runs on a single event loop, so no thread can preempt mid
// statement, but an await inside a read-modify-write cycle yields control
// and lets another worker read the same state. the mutex serializes the
// whole cycle rather than any individual operation
class Mutex {
  constructor() {
    this.tail = Promise.resolve();
  }

  // run executes fn with exclusive access and returns its result
  run(fn) {
    const result = this.tail.then(() => fn());
    // swallow rejections on the chain so one failure cannot poison the queue
    this.tail = result.then(() => undefined, () => undefined);
    return result;
  }
}

const mutex = new Mutex();

async function readStore(file = DATA_FILE) {
  let raw;
  try {
    raw = await fs.readFile(file, 'utf8');
  } catch (err) {
    throw new StorageError(`cannot read ${file}: ${err.message}`);
  }

  let store;
  try {
    store = JSON.parse(raw);
  } catch (err) {
    throw new StorageError(`cannot parse ${file}: ${err.message}`);
  }

  if (store.schemaVersion !== SCHEMA_VERSION) {
    throw new StorageError(
      `schemaVersion ${store.schemaVersion} is not supported, expected ${SCHEMA_VERSION}`
    );
  }
  if (!Array.isArray(store.users) || !Array.isArray(store.tasks)) {
    throw new StorageError(`${file} is missing a users or tasks array`);
  }
  return store;
}

// serialize writes the exact format the spec fixes: field order, two-space
// indentation, trailing newline. the java implementation must match this byte
// for byte, so the ordering is explicit rather than left to the json encoder
function serialize(store) {
  const shaped = {
    schemaVersion: store.schemaVersion,
    users: store.users.map((u) => ({ id: u.id, name: u.name })),
    tasks: store.tasks.map((t) => ({
      id: t.id,
      title: t.title,
      category: t.category,
      status: t.status,
      assignee: t.assignee === undefined ? null : t.assignee,
      createdAt: t.createdAt,
    })),
  };
  return `${JSON.stringify(shaped, null, 2)}\n`;
}

async function writeStore(store, file = DATA_FILE) {
  try {
    await fs.writeFile(file, serialize(store), 'utf8');
  } catch (err) {
    throw new StorageError(`cannot write ${file}: ${err.message}`);
  }
}

// nextTaskId returns t followed by the highest existing numeric suffix plus one
function nextTaskId(tasks) {
  let highest = 0;
  for (const task of tasks) {
    const n = parseInt(String(task.id).slice(1), 10);
    if (!Number.isNaN(n) && n > highest) highest = n;
  }
  return `t${highest + 1}`;
}

function findTask(store, id) {
  const task = store.tasks.find((t) => t.id === id);
  if (!task) throw new NotFoundError(`no task with id ${id}`);
  return task;
}

function findUser(store, id) {
  const user = store.users.find((u) => u.id === id);
  if (!user) throw new NotFoundError(`no user with id ${id}`);
  return user;
}

// withStore runs a read-modify-write cycle under the mutex
// pass lock false to bypass it and demonstrate lost updates
async function withStore(fn, { lock = true, file = DATA_FILE } = {}) {
  const cycle = async () => {
    const store = await readStore(file);
    const result = await fn(store);
    await writeStore(store, file);
    return result;
  };
  // two layers: the mutex serializes workers inside this process, the file
  // lock excludes other processes. neither alone is sufficient
  return lock
    ? mutex.run(() => filelock.withLock(cycle))
    : cycle();
}

module.exports = {
  SCHEMA_VERSION,
  DATA_FILE,
  StorageError,
  NotFoundError,
  Mutex,
  readStore,
  writeStore,
  serialize,
  nextTaskId,
  findTask,
  findUser,
  withStore,
};
