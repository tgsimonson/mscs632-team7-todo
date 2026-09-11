#!/usr/bin/env bash
# runs an identical command sequence against either implementation
# usage: ./tests/acceptance.sh "java -jar java/todo.jar"
#        ./tests/acceptance.sh "node javascript/todo.js"
set -u

CMD="${1:-}"
if [ -z "$CMD" ]; then
  echo "usage: $0 \"<command to invoke the implementation>\"" >&2
  exit 1
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
ACTUAL=$(mktemp)
EXPECTED="tests/expected/acceptance.txt"

# every run starts from the same state
cp data/tasks.seed.json data/tasks.json

run() {
  echo "\$ $*" >> "$ACTUAL"
  # shellcheck disable=SC2086
  $CMD "$@" >> "$ACTUAL" 2>&1
  echo "[exit $?]" >> "$ACTUAL"
  echo >> "$ACTUAL"
}

run users
run list
run add --title "write comparison section" --category writing
run add --title "stitch video segments" --category media --assignee u1
run list --status pending
run list --category writing
run list --user u1
run assign --id t3 --user u2
run complete --id t3
run list --status complete
run remove --id t5
run list
run list --category nonexistent
run complete --id t99
run assign --id t1 --user u99
run badcommand

if [ ! -f "$EXPECTED" ]; then
  echo "no expected output on file yet; writing current output as the baseline"
  mkdir -p tests/expected
  cp "$ACTUAL" "$EXPECTED"
  echo "baseline written to $EXPECTED -- review it by hand before committing"
  cat "$EXPECTED"
  exit 0
fi

if diff -u "$EXPECTED" "$ACTUAL"; then
  echo "ACCEPTANCE: PASS"
  exit 0
else
  echo "ACCEPTANCE: FAIL (diff above, expected vs actual)" >&2
  exit 1
fi
