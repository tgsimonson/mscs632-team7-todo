# Collaborative To-Do List Application (Team 7)

MSCS 632 Advanced Programming Languages. The same command-line to-do
application implemented twice, in Java and in JavaScript, against one shared
data contract.

## Why one contract

Both implementations read and write the same `data/tasks.json`. That means a
task created in the Java app appears in the JavaScript app and vice versa,
which demonstrates functional equivalence rather than asserting it. The contract
is `docs/SPEC.md` and it is binding on both implementations.

## Layout

```
docs/SPEC.md              the shared contract: schema, commands, exit codes
docs/design-document.docx deliverable 1
data/tasks.seed.json      known starting state, tracked in git
data/tasks.json           working copy, gitignored
java/                     Java implementation
javascript/               JavaScript implementation
tests/acceptance.sh       identical command sequence, run against either
tests/concurrency.sh      concurrency test, with and without locking
```

## Running

```bash
cp data/tasks.seed.json data/tasks.json

java -jar java/todo.jar list
node javascript/todo.js list
```

## Testing

Both implementations must produce byte-identical stdout:

```bash
./tests/acceptance.sh "java -jar java/todo.jar"
./tests/acceptance.sh "node javascript/todo.js"
```

Concurrency, three runs each with and without synchronization:

```bash
./tests/concurrency.sh "java -jar java/todo.jar"
./tests/concurrency.sh "node javascript/todo.js"
```

## Cross-language demonstration

```bash
cp data/tasks.seed.json data/tasks.json
java -jar java/todo.jar add --title "created in java" --category demo
node javascript/todo.js list          # the task appears here
node javascript/todo.js complete --id t4
java -jar java/todo.jar list          # the status change appears here
```

## Team

| Member | Implementation | Also owns |
|---|---|---|
| Todd Simonson | TBD | Repo, spec, test harness, report assembly, comparison section |
| Jahnavi [surname] | TBD | Report section for own language, own demo segment |

Language assignment is pending Jahnavi's choice. Everything else in this
repository is language-neutral and applies either way.
