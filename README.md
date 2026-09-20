# Collaborative To-Do List Application (Team 7)

MSCS 632 Advanced Programming Languages. A multi-user command-line to-do list,
built twice: once in Java, once in JavaScript.

Both versions read and write the same `data/tasks.json` and expose the same
commands with the same output, so a task added in one shows up in the other.
That was a deliberate choice. When the two programs do the same thing in the
same way, every difference left over is a difference between the languages,
which is what the report has to analyze. `docs/SPEC.md` holds the contract and
both implementations are held to it.

## Status

Both implementations are complete and pass the same acceptance baseline.

```bash
./run_both.sh
```

That builds both, runs the acceptance test against each, runs the concurrency
test with and without locking in both, shows a task created in Java appearing
in JavaScript, and finishes by launching ten processes of each language against
the same file at once.

## Layout

```
docs/SPEC.md              schema, commands, output format, exit codes, locking
docs/design-document.docx deliverable 1
data/tasks.seed.json      known starting state, tracked in git
data/tasks.json           working copy, gitignored
data/tasks.lock           lock file, gitignored
java/                     Java implementation
javascript/               JavaScript implementation
tests/acceptance.sh       one command sequence, run against either version
tests/concurrency.sh      concurrency test, with and without locking
run_both.sh               everything above, in order
```

## Running

```bash
cp data/tasks.seed.json data/tasks.json

make -C java
./java/todo list
node javascript/todo.js list
```

Interactive session in either language:

```bash
./java/todo shell --user jahnavi
node javascript/todo.js shell --user todd
```

## Commands

Identical in both implementations.

```
add       --title "<text>" --category <cat> [--assignee <userId>]
adduser   --name <name>
list      [--user <id>] [--category <cat>] [--status pending|complete]
assign    --id <taskId> --user <userId>
complete  --id <taskId>
remove    --id <taskId>
users
shell     [--user <name>]
concurrency-test --workers <n> --ops <n> [--no-lock]
```

Exit codes: 0 success, 1 usage error, 2 not found, 3 storage error.

## Testing

Both versions produce identical stdout for the same input:

```bash
./tests/acceptance.sh "node javascript/todo.js"
./tests/acceptance.sh "./java/todo"
```

Twenty-two commands, including five that are supposed to fail, diffed against
`tests/expected/`.

## Concurrency

Eight workers, fifty adds each, in both languages:

| Locking | Expected | Actual | Result |
|---|---|---|---|
| enabled | 403 | 403 | PASS |
| disabled | 403 | 53 to 63, or unreadable | FAIL |

Without synchronization both languages fail, in two ways that vary run to run:
lost updates, where a worker writes a snapshot taken before another's write,
and torn reads, where the file is truncated before the new bytes land and
parsing fails outright.

Two layers of exclusion are required and neither alone is enough. A
promise-chain mutex in JavaScript and a `ReentrantLock` in Java serialize work
inside one runtime. A lock file, created by atomic exclusive create in both
languages, excludes other processes. Before the file lock, twenty-four
concurrent Node processes produced two surviving writes.

## Team

| Member | Implementation | Also owns |
|---|---|---|
| Todd Simonson | JavaScript | Repo, spec, test harness, report assembly, comparison section |
| Jahnavi Dammannagari | Java | Report section for own language, own demo segment |
