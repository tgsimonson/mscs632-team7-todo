#!/usr/bin/env node
// collaborative to-do list, javascript implementation
// see docs/SPEC.md for the contract this shares with the java implementation
const commands = require('./lib/commands');
const concurrency = require('./lib/concurrency');

// parseOptions turns --key value pairs into an object
// flags with no value, such as --no-lock, become true
function parseOptions(argv) {
  const opts = {};
  for (let i = 0; i < argv.length; i++) {
    const token = argv[i];
    if (!token.startsWith('--')) {
      throw new commands.UsageError(`unexpected argument ${token}`);
    }
    const key = token.slice(2);
    const next = argv[i + 1];
    if (next === undefined || next.startsWith('--')) {
      opts[key] = true;
    } else {
      opts[key] = next;
      i++;
    }
  }
  return opts;
}

async function concurrencyTest(opts) {
  const workers = parseInt(opts.workers, 10);
  const ops = parseInt(opts.ops, 10);
  if (Number.isNaN(workers) || workers < 1) {
    throw new commands.UsageError('--workers must be a positive integer');
  }
  if (Number.isNaN(ops) || ops < 1) {
    throw new commands.UsageError('--ops must be a positive integer');
  }
  return concurrency.run({ workers, ops, lock: !opts['no-lock'] });
}

// dispatch table: verbs map to handler functions
const TABLE = {
  add: commands.add,
  list: commands.list,
  assign: commands.assign,
  complete: commands.complete,
  remove: commands.remove,
  users: commands.users,
  'concurrency-test': concurrencyTest,
};

async function main() {
  const [verb, ...rest] = process.argv.slice(2);

  if (!verb) {
    process.stderr.write(`error: no command given, expected one of ${Object.keys(TABLE).join(', ')}\n`);
    process.exit(1);
  }

  const handler = TABLE[verb];
  if (!handler) {
    process.stderr.write(`error: unknown command ${verb}\n`);
    process.exit(1);
  }

  try {
    const output = await handler(parseOptions(rest));
    if (output) process.stdout.write(`${output}\n`);
    process.exit(0);
  } catch (err) {
    process.stderr.write(`error: ${err.message}\n`);
    process.exit(err.code || 1);
  }
}

main();
