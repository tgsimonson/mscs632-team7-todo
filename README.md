# Collaborative To-Do List Application (Team 7)

MSCS 632 Advanced Programming Languages. A multi-user command-line to-do list,
built twice: once in Java, once in JavaScript.

Both versions read and write the same `data/tasks.json` and expose the same
commands with the same output, so a task added in one shows up in the other.
That was a deliberate choice. When the two programs do the same thing in the
same way, every difference left over is a difference between the languages,
which is what the report has to analyze. `docs/SPEC.md` holds the contract and
both implementations are held to it.

## Layout

```
docs/SPEC.md              schema, commands, output format, exit codes
docs/design-document.docx deliverable 1
data/tasks.seed.json      known starting state, tracked in git
data/tasks.json           working copy, gitignored
java/                     Java implementation
javascript/               JavaScript implementation
tests/acceptance.sh       one command sequence, run against either version
tests/concurrency.sh      concurrency test, with and without locking
```

## Running

```bash
cp data/tasks.seed.json data/tasks.json

java -jar java/todo.jar list
node javascript/todo.js list
```

## Testing

Both versions have to produce identical stdout for the same input:

```bash
./tests/acceptance.sh "java -jar java/todo.jar"
./tests/acceptance.sh "node javascript/todo.js"
```

The script runs sixteen commands, including three that are supposed to fail, and
diffs the result against `tests/expected/`. First run writes the baseline.

Concurrency, three runs each way:

```bash
./tests/concurrency.sh "java -jar java/todo.jar"
./tests/concurrency.sh "node javascript/todo.js"
```

Eight workers, fifty adds each. With locking both should pass. With `--no-lock`
the Java version should lose updates. The JavaScript version may not, since it
only yields at `await`, and that difference is a result we want rather than a
bug to paper over.

## Showing both versions on the same data

```bash
cp data/tasks.seed.json data/tasks.json
java -jar java/todo.jar add --title "created in java" --category demo
node javascript/todo.js list          # it's there
node javascript/todo.js complete --id t4
java -jar java/todo.jar list          # status changed
```

## Team

| Member | Implementation | Also owns |
|---|---|---|
| Todd Simonson | TBD | Repo, spec, test harness, report assembly, comparison section |
| Jahnavi Dammannagari | TBD | Report section for own language, own demo segment |

Language assignment is pending. Nothing else in this repo depends on it.
