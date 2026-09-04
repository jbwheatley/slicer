#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
corpuses="slicer-core/src/test"

rebuild=false
tool=all
chosen=""

while [ $# -gt 0 ]; do
  case "$1" in
    --rebuild) rebuild=true ;;
    --tool) tool="${2:-}"; shift ;;
    --corpus) chosen="$chosen ${2:-}"; shift ;;
    *) echo "usage: $0 [--rebuild] [--tool sbt|mill|all] [--corpus <name>]..." >&2; exit 2 ;;
  esac
  shift
done

case "$tool" in
  sbt | mill | all) ;;
  *) echo "unknown tool '$tool': expected sbt, mill or all" >&2; exit 2 ;;
esac

sbt_corpuses=(test-project test-project-213 test-project-js test-project-native)
mill_corpuses=(test-project test-project-213)

for name in $chosen; do
  case " ${sbt_corpuses[*]} " in
    *" $name "*) ;;
    *) echo "unknown corpus '$name': expected one of ${sbt_corpuses[*]}" >&2; exit 2 ;;
  esac
done

wasChosen() {
  [ -z "$chosen" ] && return 0
  case " $chosen " in
    *" $1 "*) return 0 ;;
    *) return 1 ;;
  esac
}

findSourceNewerThanStamp() {
  local corpus="$1" stamp="$2" first="$3" second="$4"
  find "$corpus" \
    \( -path "$corpus/target" -o -path "$corpus/out" \) -prune -o \
    -type f \( -name "*.$first" -o -name "*.$second" \) -newer "$stamp" -print -quit
}

logs=$(mktemp -d)
trap 'rm -rf "$logs"' EXIT

buildCorpus() {
  local name="$1" outputName="$2" first="$3" second="$4"
  shift 4
  local corpus="$corpuses/$name"
  local output="$corpus/$outputName"
  local stamp="$output/.slicer-corpus-built"
  local log="$logs/$name-$outputName.log"

  if [ "$rebuild" = true ]; then rm -rf "$output"; fi
  if [ "$rebuild" = false ] && [ -f "$stamp" ] &&
    [ -z "$(findSourceNewerThanStamp "$corpus" "$stamp" "$first" "$second")" ]; then
    return
  fi

  echo "building the $name corpus with $*"
  if ! (cd "$corpus" && "$@") >"$log" 2>&1; then
    echo "the $name corpus failed to build with $*" >&2
    cat "$log" >&2
    touch "$logs/failed-$name-$outputName"
    return 1
  fi
  mkdir -p "$output"
  touch "$stamp"
}

processors=$( (nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo 2) )
concurrency=$((processors / 2))
[ "$concurrency" -lt 1 ] && concurrency=1

running=0

startCorpus() {
  if [ "$running" -ge "$concurrency" ]; then
    wait -n || true
    running=$((running - 1))
  fi
  buildCorpus "$@" &
  running=$((running + 1))
}

if [ "$tool" != mill ]; then
  for name in "${sbt_corpuses[@]}"; do
    if wasChosen "$name"; then startCorpus "$name" target scala sbt sbt compile; fi
  done
fi

if [ "$tool" != sbt ]; then
  for name in "${mill_corpuses[@]}"; do
    if wasChosen "$name"; then startCorpus "$name" out scala mill ./mill __.semanticDbData; fi
  done
fi

wait

failed=("$logs"/failed-*)
[ -e "${failed[0]}" ] || exit 0

for marker in "${failed[@]}"; do
  echo "corpus build failed: $(basename "$marker" | sed 's/^failed-//')" >&2
done
exit 1
