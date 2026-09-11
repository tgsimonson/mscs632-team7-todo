#!/usr/bin/env bash
# runs the concurrency test with and without locking
# usage: ./tests/concurrency.sh "java -jar java/todo.jar"
set -u
CMD="${1:-}"
[ -z "$CMD" ] && { echo "usage: $0 \"<command>\"" >&2; exit 1; }
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"

for mode in "" "--no-lock"; do
  echo "===== locking: ${mode:-enabled} ====="
  for run in 1 2 3; do
    cp data/tasks.seed.json data/tasks.json
    # shellcheck disable=SC2086
    $CMD concurrency-test --workers 8 --ops 50 $mode
  done
  echo
done
