# JavaScript implementation

Entry point `javascript/todo.js`. Must satisfy `docs/SPEC.md` exactly.

## Required language features to demonstrate

1. **Asynchronous operations.** `async`/`await` throughout, `Promise.all` to
   run concurrent workers in `concurrency-test`. All file I/O through
   `fs/promises`, never the synchronous variants.
2. **JSON for data storage.** `JSON.parse` and `JSON.stringify` with 2-space
   indentation, matching the spec's field order and trailing newline.
3. **Dynamic typing and first-class functions.** A command table mapping verb
   names to handler functions, object destructuring for parsed options.

## Suggested structure

```
javascript/
  todo.js             arg parsing, command dispatch, exit codes
  lib/store.js        load, save, async mutex
  lib/commands.js     one handler per verb
  lib/format.js       table formatting, shared column widths
  lib/concurrency.js  worker pool via Promise.all
  package.json
```

## Note on the concurrency test

Because the event loop yields only at `await`, an unsynchronized run may still
pass if nothing awaits between reading and writing the store. Make the file I/O
genuinely asynchronous rather than papering over this. If `--no-lock` passes
anyway, that is a real finding and belongs in the report.
