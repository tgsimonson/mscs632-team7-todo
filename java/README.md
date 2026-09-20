# Java implementation

Complete. Satisfies `docs/SPEC.md`. Java 17 or newer, no dependencies.

## Building and running

```bash
make
./todo list
./todo add --title "write the report" --category writing
./todo shell --user jahnavi
```

`make` compiles to `out/`. The `todo` wrapper points the program at
`../data/tasks.json` so both implementations share one file.

## Layout

```
src/todo/Main.java                   argument parsing, dispatch, exit codes
src/todo/model/User.java
src/todo/model/Task.java
src/todo/model/Status.java           enum: PENDING, COMPLETE
src/todo/store/TaskStore.java        load, save, ReentrantLock, FileLock
src/todo/store/JsonCodec.java        hand-written JSON parser and escaper
src/todo/store/FileLock.java         cross-process advisory lock
src/todo/store/StoreException.java   carries the spec's exit codes
src/todo/command/Command.java        interface, one implementation per verb
src/todo/command/Formatter.java      fixed-width table rendering
src/todo/concurrency/ConcurrencyTest.java
```

## Language features demonstrated

**Classes and object-oriented principles.** `User` and `Task` are classes with
private fields and accessors. `Status` is an enum, so an invalid status cannot
be constructed, which is the static counterpart to JavaScript validating the
string at runtime. Each verb is a class implementing the `Command` interface,
so dispatch is a map lookup where every entry carries its own type. `TaskStore`
encapsulates persistence and is the only class that touches the file.

**Concurrency with threads.** `ConcurrencyTest` uses an `ExecutorService` with
a fixed pool, so workers are real operating system threads running in parallel
rather than cooperatively scheduled on one loop. `TaskStore` guards the
read-modify-write cycle with a `ReentrantLock`.

**Static typing.** Generics on every collection, `Optional` for the nullable
lookup, and an enum for status. The JSON codec is the cost of that strictness:
JavaScript gets `JSON.parse` in the language, while the same capability here is
several hundred hand-written lines or an external dependency.

## Concurrency results

Eight threads, fifty adds each:

| Locking | Expected | Actual | Result |
|---|---|---|---|
| enabled | 403 | 403 | PASS |
| disabled | 403 | 63 or unreadable | FAIL |

The unsynchronized run fails in both of the ways the spec anticipates, and
which one appears varies between runs of the same binary. One run lost 340
updates with 241 torn reads; the next left the file unparseable entirely.

## Cross-process locking

`ReentrantLock` serializes threads inside one JVM and nothing more. Two
separate `./todo` invocations, or one Java process and one Node process, share
no JVM state. `FileLock` closes that gap by creating a lock file with
`CREATE_NEW`, which fails if the path exists. That check-and-create is atomic
at the syscall level.

The JavaScript implementation acquires the same lock the same way, which is why
the spec fixes the mechanism rather than leaving it per language.
`FileChannel.lock` would be the more idiomatic Java choice, but an OS advisory
lock and an exclusive create do not exclude one another.

Verified: ten Java processes and ten Node processes adding simultaneously
produced all twenty tasks with none lost.
