// concurrency test
// n workers each perform m add operations against the same store. with the
// mutex held the final count must equal the starting count plus n times m.
// with --no-lock the read-modify-write cycle is unguarded and later writes
// overwrite earlier ones
const store = require('./store');

// worker performs ops add operations, yielding at every await
async function worker(id, ops, lock) {
  for (let i = 0; i < ops; i++) {
    await store.withStore(
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
  }
}

async function run({ workers, ops, lock }) {
  const before = await store.readStore();
  const starting = before.tasks.length;
  const expected = starting + workers * ops;

  // promise.all launches every worker before any of them finishes, so the
  // event loop interleaves them at each await
  await Promise.all(
    Array.from({ length: workers }, (_, i) => worker(i + 1, ops, lock))
  );

  const after = await store.readStore();
  const actual = after.tasks.length;

  const lines = [
    `workers: ${workers}  ops each: ${ops}  locking: ${lock ? 'enabled' : 'disabled'}`,
    `expected tasks: ${expected}`,
    `actual tasks:   ${actual}`,
    `result: ${actual === expected ? 'PASS' : 'FAIL'}`,
  ];
  if (actual !== expected) {
    lines.push(`lost updates: ${expected - actual}`);
  }
  return lines.join('\n');
}

module.exports = { run };
