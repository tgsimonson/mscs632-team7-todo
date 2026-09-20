# SPEC: shared contract

Both implementations must satisfy this document exactly. It is the contract that
makes the two versions comparable. If one implementation needs to deviate, raise
it before changing it, because a silent deviation invalidates the report's
comparison and breaks the cross-language demo.

Version 1.0. Owner: whoever changes it opens an issue first.

---

## 1. Data model

Both implementations read and write the same file: `data/tasks.json`.
Seed a fresh copy from `data/tasks.seed.json` before each test run.

```json
{
  "schemaVersion": 1,
  "users": [
    { "id": "u1", "name": "todd" }
  ],
  "tasks": [
    {
      "id": "t1",
      "title": "draft design document",
      "category": "writing",
      "status": "pending",
      "assignee": "u1",
      "createdAt": "2026-09-11T18:00:00Z"
    }
  ]
}
```

### Field rules

| Field | Type | Rules |
|---|---|---|
| `schemaVersion` | integer | Always `1`. Reject the file if it differs. |
| `users[].id` | string | `u` followed by digits. Unique. |
| `users[].name` | string | Lowercase, no spaces. Unique. |
| `tasks[].id` | string | `t` followed by digits. Unique. Assigned as `t` + (highest existing numeric suffix + 1). |
| `tasks[].title` | string | Non-empty, max 120 chars. |
| `tasks[].category` | string | Non-empty, lowercase. Free-form, not an enum. |
| `tasks[].status` | string | Exactly `pending` or `complete`. |
| `tasks[].assignee` | string or null | A `users[].id`, or `null` for unassigned. |
| `tasks[].createdAt` | string | ISO 8601 UTC with `Z` suffix, second precision. |

Write the file with 2-space indentation, keys in the order shown above, and a
trailing newline. This keeps `git diff` readable and lets the acceptance test
compare files byte for byte.

---

## 2. Command surface

Invocation differs by language, which is expected and is itself a reportable
difference:

```
java -jar todo.jar <command> [options]
node todo.js <command> [options]
```

Every command below must exist in both with identical options and identical
stdout.

### `add`

```
add --title "<text>" --category <cat> [--assignee <userId>]
```

Creates a task with status `pending`. Prints the new id.

```
added t4
```

### `list`

```
list [--user <userId>] [--category <cat>] [--status pending|complete]
```

Filters combine with AND. Sort by `createdAt` ascending, then `id` ascending.
Output is a fixed-width table. An unassigned task shows `-`.

```
ID    STATUS    CATEGORY    ASSIGNEE    TITLE
t1    complete  writing     todd        draft design document
t2    pending   dev         jahnavi     implement storage layer
t3    pending   media       -           record demo segment
```

Column widths: ID 6, STATUS 10, CATEGORY 12, ASSIGNEE 12, TITLE remainder.
Left-aligned, space-padded. If no tasks match, print exactly:

```
no tasks match
```

### `assign`

```
assign --id <taskId> --user <userId>
```

```
assigned t3 to jahnavi
```

### `complete`

```
complete --id <taskId>
```

Sets status to `complete`. Completing an already-complete task is not an error
and prints the same line.

```
completed t3
```

### `remove`

```
remove --id <taskId>
```

```
removed t3
```

### `adduser`

```
adduser --name <name>
```

Name must be lowercase letters, digits, hyphen or underscore, and unique. The
id is `u` followed by the highest existing numeric suffix plus one.

```
added user u3
```

### `users`

```
users
```

```
u1    todd
u2    jahnavi
```

### `shell`

```
shell [--user <name>]
```

Opens an interactive session that accepts the same verbs, plus `help` and
`exit`. The one-shot form remains primary and is what the acceptance test
drives; the session is a thin wrapper over the same dispatch, with no
behavioral differences.

### `concurrency-test`

```
concurrency-test --workers <N> --ops <M> [--no-lock]
```

Spawns N concurrent workers, each performing M `add` operations against the
shared store. Prints the expected and actual final task count and a verdict.

```
workers: 8  ops each: 50  locking: enabled
expected tasks: 403
actual tasks:   403
result: PASS
```

With `--no-lock` the implementation bypasses its synchronization so lost updates
can be demonstrated. A `FAIL` result there is the intended outcome, not a defect.

---

## 3. Exit codes

| Code | Meaning |
|---|---|
| 0 | Success |
| 1 | Usage error (unknown command, missing or malformed option) |
| 2 | Not found (task id or user id does not exist) |
| 3 | Storage error (unreadable, unparseable, or wrong `schemaVersion`) |

Errors go to stderr with the prefix `error: `. Nothing but the specified output
goes to stdout, so the acceptance test can diff stdout directly.

```
error: no task with id t99
```

---

## 3a. Locking

Both implementations acquire the same lock before any read-modify-write cycle:
a file at `data/tasks.lock`, created with an exclusive-create flag that fails
if the path already exists. That check and create is atomic at the syscall
level, so exactly one process wins.

- **JavaScript:** `fs.open(path, 'wx')`
- **Java:** `Files.createFile(path)`, catching `FileAlreadyExistsException`

Retry every 5ms, time out after 10s. A lock whose mtime is older than 30s is
treated as stale and broken, which recovers from a process that died holding
it. Release by deleting the file, in a `finally` block or `try`-with-resources
so a thrown exception cannot leave it held.

The mechanism is fixed here rather than chosen per language because an advisory
lock only works when every participant honors it the same way. `FileChannel.lock`
in Java and an exclusive create in Node do not exclude one another, so mixing
them would leave the two implementations unprotected against each other.

In-language synchronization is still required and is not a substitute. A
promise-chain mutex or a `ReentrantLock` serializes work inside one runtime;
the file lock excludes other processes. Both layers are needed.

## 4. Concurrency requirements

Both implementations must serialize the read-modify-write cycle on
`data/tasks.json`. The cycle is: read the whole file, parse, mutate, serialize,
write the whole file. Without serialization, two workers read the same state and
the second write discards the first worker's task.

**Java.** Real OS threads via `ExecutorService`. Guard the critical section with
`ReentrantLock` or a `synchronized` block. Because threads genuinely run in
parallel, `--no-lock` should reliably lose updates.

**JavaScript.** Cooperative concurrency on one event loop via `async`/`await`
and `Promise.all`. Guard the critical section with a promise-chain mutex.

Measured result. Both implementations fail without synchronization, and both
fail in two distinct ways depending on timing:

- **Lost updates.** A worker writes a snapshot taken before another worker's
  write, discarding it.
- **Torn reads.** The write truncates the file before the new bytes land, so a
  reader in that window gets invalid JSON and parsing fails outright.

JavaScript's single-threaded event loop does not prevent this. It only moves
the interleaving to `await` boundaries. Observed: 343 lost updates with 116
torn reads in JavaScript, 340 lost updates with 241 torn reads in Java, and
runs of the same binary that left the file unparseable entirely.

The finding for the report is that the shared resource is a file, not an
in-memory structure, so neither `synchronized` nor the event loop reaches it.
Both languages need an OS-level primitive, and it has to be the same one.

---

## 5. Acceptance test

`tests/acceptance.sh` runs an identical command sequence against either
implementation and diffs stdout against `tests/expected/`. Both must pass before
the feature freeze.

```bash
./tests/acceptance.sh "java -jar java/todo.jar"
./tests/acceptance.sh "node javascript/todo.js"
```

---

## 6. Change log

| Version | Date | Change |
|---|---|---|
| 1.0 | 2026-09-11 | Initial contract |
| 1.1 | 2026-09-20 | Added section 3a, cross-process lock file, required in both implementations |
| 1.2 | 2026-09-20 | Added the adduser command and an interactive shell mode |
