// cross-process advisory lock
//
// the promise-chain mutex in store.js serializes work inside one node
// process. two separate invocations, which is what two users running the cli
// at the same time looks like, share no such state and will interleave their
// read-modify-write cycles freely.
//
// the lock is a file created with the exclusive flag, which fails if the path
// already exists. that check and create is atomic at the syscall level, so
// exactly one process wins. java acquires the same lock by the same means,
// which is why the mechanism is fixed in the spec: an advisory lock only
// works when every participant honors it the same way
const fs = require('fs/promises');
const path = require('path');

const LOCK_FILE = path.join(__dirname, '..', '..', 'data', 'tasks.lock');
const RETRY_MS = 5;
const TIMEOUT_MS = 10000;
const STALE_MS = 30000;

class LockError extends Error {
  constructor(message) {
    super(message);
    this.code = 3;
  }
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

// breakIfStale removes a lock left behind by a process that died holding it
async function breakIfStale(file) {
  try {
    const stat = await fs.stat(file);
    if (Date.now() - stat.mtimeMs > STALE_MS) {
      await fs.unlink(file);
      return true;
    }
  } catch {
    // the holder released it while we were looking, which is fine
  }
  return false;
}

async function acquire(file = LOCK_FILE) {
  const deadline = Date.now() + TIMEOUT_MS;

  for (;;) {
    try {
      // wx fails if the path exists, so the winner is decided by the kernel
      const handle = await fs.open(file, 'wx');
      await handle.writeFile(String(process.pid));
      await handle.close();
      return;
    } catch (err) {
      if (err.code !== 'EEXIST') {
        throw new LockError(`cannot acquire ${file}: ${err.message}`);
      }
      if (Date.now() > deadline) {
        if (await breakIfStale(file)) continue;
        throw new LockError(`timed out waiting for ${file} after ${TIMEOUT_MS}ms`);
      }
      await sleep(RETRY_MS);
    }
  }
}

async function release(file = LOCK_FILE) {
  try {
    await fs.unlink(file);
  } catch (err) {
    if (err.code !== 'ENOENT') {
      throw new LockError(`cannot release ${file}: ${err.message}`);
    }
  }
}

// withLock guarantees release even when fn throws
async function withLock(fn, file = LOCK_FILE) {
  await acquire(file);
  try {
    return await fn();
  } finally {
    await release(file);
  }
}

module.exports = { LOCK_FILE, LockError, acquire, release, withLock };
