# JavaScript implementation

Complete. Satisfies `docs/SPEC.md`. No dependencies, Node 18 or newer.

## Running

```bash
cp ../data/tasks.seed.json ../data/tasks.json
node todo.js list
node todo.js add --title "write the report" --category writing
node todo.js assign --id t4 --user u2
node todo.js complete --id t4
```

## Layout

```
todo.js            argument parsing, dispatch table, exit codes
lib/store.js       load, save, async mutex, id generation
lib/format.js      fixed-width table formatting
lib/commands.js    one handler function per verb
lib/concurrency.js worker pool via Promise.all
```

## Language features demonstrated

**Asynchronous operations.** Every file access goes through `fs/promises` with
`async`/`await`. The synchronous variants would defeat the concurrency test,
because without a real yield point between reading and writing the store there
is nothing for workers to interleave on. `Promise.all` launches all workers
before any completes.

**JSON for data storage.** `JSON.parse` on read and `JSON.stringify` on write.
The serializer rebuilds each object with fields in the order the spec fixes
rather than trusting insertion order, and appends the trailing newline, so the
file matches the Java implementation byte for byte.

**First-class functions and dynamic typing.** Commands live in a dispatch table
mapping verb strings to handler functions, so adding a verb means adding one
entry. Options arrive as a plain object built at runtime, with destructuring
and default values in place of a parsed argument type.

## Concurrency result

`Mutex` serializes the read-modify-write cycle through a promise chain. Eight
workers, fifty adds each, three runs:

| Locking | Expected | Actual | Result |
|---|---|---|---|
| enabled | 403 | 403 | PASS |
| disabled | 403 | 53 or unreadable | FAIL |

The unsynchronized run fails reliably, not intermittently, but it fails in two
different ways depending on the filesystem.

**Lost updates.** Each worker awaits a read, and every worker that was waiting
resumes holding a snapshot taken before the others wrote, so the last write of
each cycle discards the rest. Observed on ext4: 350 of 400 writes lost.

**Torn reads.** `writeFile` truncates before it writes. A reader that lands in
that window gets an incomplete file and `JSON.parse` fails outright. Observed
on APFS, where the same run leaves the store unparseable rather than merely
short.

The test reports whichever occurred instead of assuming one. Both outcomes make
the same point: single-threaded execution does not prevent the race, it only
moves the interleaving to `await` boundaries. The second outcome is the worse
one, since a corrupted store loses data that was already committed rather than
only the writes that raced.
