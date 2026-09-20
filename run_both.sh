#!/usr/bin/env bash
# builds both implementations, runs the acceptance test against each, runs the
# concurrency test both ways, and demonstrates the two languages sharing one
# data file
set -u
cd "$(dirname "$0")"

JS="node javascript/todo.js"
JV="./java/todo"

hr() { printf '\n========== %s ==========\n' "$1"; }
reset() { cp data/tasks.seed.json data/tasks.json; rm -f data/tasks.lock; }

hr "BUILD"
make -C java >/dev/null && echo "java: compiled to java/out"
node --version | sed 's/^/node: /'

hr "ACCEPTANCE: JAVASCRIPT"
reset; $JS --help >/dev/null 2>&1 || true
./tests/acceptance.sh "$JS" | tail -1

hr "ACCEPTANCE: JAVA"
reset
./tests/acceptance.sh "$JV" | tail -1

hr "CONCURRENCY: JAVASCRIPT"
for mode in "" "--no-lock"; do
  reset
  # shellcheck disable=SC2086
  $JS concurrency-test --workers 8 --ops 50 $mode
  echo
done

hr "CONCURRENCY: JAVA"
for mode in "" "--no-lock"; do
  reset
  # shellcheck disable=SC2086
  $JV concurrency-test --workers 8 --ops 50 $mode
  echo
done

hr "SHARED DATA FILE"
reset
echo "adding a task from java..."
$JV add --title "created in java" --category demo
echo "listing from javascript:"
$JS list --category demo
echo
echo "completing it from javascript..."
$JS complete --id t4
echo "listing from java:"
$JV list --category demo

hr "CROSS-LANGUAGE LOCKING"
reset
echo "10 java processes and 10 node processes adding at the same time..."
for i in $(seq 1 10); do $JV add --title "java $i" --category j & done >/dev/null 2>&1
for i in $(seq 1 10); do $JS add --title "node $i" --category n & done >/dev/null 2>&1
wait
TOTAL=$(node -e "console.log(JSON.parse(require('fs').readFileSync('data/tasks.json','utf8')).tasks.length)")
echo "expected 23 tasks (3 seed + 20), found $TOTAL"
if [ "$TOTAL" = "23" ]; then
  echo "PASS: no writes lost across languages"
else
  echo "FAIL: $((23 - TOTAL)) writes lost"
fi
