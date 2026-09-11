# Java implementation

Build to `java/todo.jar`. Must satisfy `docs/SPEC.md` exactly.

## Required language features to demonstrate

1. **Classes and object-oriented structure.** `User` and `Task` as classes,
   a `TaskStore` owning persistence, a `Command` interface with one
   implementation per verb. Encapsulation with private fields and accessors.
2. **Concurrency with real threads.** `ExecutorService` with a fixed pool for
   `concurrency-test`. Guard the read-modify-write cycle with `ReentrantLock`
   or `synchronized`. The `--no-lock` path must bypass it.
3. **Static typing and the type system.** Enum for `status`, `Optional` for the
   nullable assignee, generics in the store's collections.

## Suggested structure

```
java/
  src/main/java/todo/
    Main.java           arg parsing, command dispatch, exit codes
    model/User.java
    model/Task.java
    model/Status.java   enum: PENDING, COMPLETE
    store/TaskStore.java    load, save, locking
    store/JsonCodec.java    read and write the exact spec format
    command/Command.java    interface
    command/AddCommand.java ... one per verb
    concurrency/ConcurrencyTest.java
  pom.xml or build.gradle
```

Keep JSON output formatting in one place so the byte-for-byte match with the
JavaScript implementation is easy to maintain.
