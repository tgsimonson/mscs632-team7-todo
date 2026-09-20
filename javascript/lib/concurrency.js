// concurrency test
// n workers each perform m add operations against the same store. with the
// mutex held the final count must equal the starting count plus n times m.
//
// without the mutex two distinct failures are possible and both are reported
// rather than raised:
//   lost updates  a worker writes a snapshot taken before another worker's
//                 write, discarding it
//   torn reads    a worker reads after writeFile has truncated the file but
//                 before the new bytes land, so JSON.parse fails
// which one appears depends on filesystem write timing, so the test reports
// whichever occurred instead of assuming one
const store = require('./store');

// worker performs ops add operations, yielding at every await
// in unsynchronized mode a failed cycle is counted rather than thrown, since
// the failure is the result being demonstrated
async function worker(id, ops, lock, tally) {
  for (let i = 0; i < ops; i++) {
    const cycle = store.withStore(
      async (s) => {
        s.tasks.push({
          id: store.nextTaskId(s.tasks),
          title: `worker ${id} task ${i}`,
          category: 'load',
          status: 'pending',
          assignee: null,
          createdAt: `${new Date().toISOString().split('.')[0]}Z`,
        });
      },
      { lock }
    );

    if (lock) {
      await cycle;
    } else {
      try {
        await cycle;
      } catch (err) {
        tally.failed++;
        if (/parse/.test(err.message)) tally.torn++;
      }
    }
  }
}

async function run({ workers, ops, lock }) {
  const before = await store.readStore();
  const starting = before.tasks.length;
  const expected = starting + workers * ops;
  const tally = { failed: 0, torn: 0 };

  // promise.all launches every worker before any of them finishes, so the
  // event loop interleaves them at each await
  await Promise.all(
    Array.from({ length: workers }, (_, i) => worker(i + 1, ops, lock, tally))
  );

  // the final read can itself fail if the last unsynchronized write left the
  // file mid-truncation
  let actual = null;
  let readError = null;
  try {
    const after = await store.readStore();
    actual = after.tasks.length;
  } catch (err) {
    readError = err.message;
  }

  const lines = [
    `workers: ${workers}  ops each: ${ops}  locking: ${lock ? 'enabled' : 'disabled'}`,
    `expected tasks: ${expected}`,
    `actual tasks:   ${actual === null ? 'unreadable' : actual}`,
    `result: ${actual === expected ? 'PASS' : 'FAIL'}`,
  ];

  if (actual === null) {
    lines.push('store corrupted: unsynchronized writes left invalid JSON');
    lines.push(`read error: ${readError}`);
  } else if (actual !== expected) {
    lines.push(`lost updates: ${expected - actual}`);
  }

  if (!lock && tally.failed > 0) {
    lines.push(`failed operations: ${tally.failed} (${tally.torn} torn reads)`);
  }

  return lines.join('\n');
}

module.exports = { run };
