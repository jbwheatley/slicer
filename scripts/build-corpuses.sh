#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
corpuses="slicer-core/src/test"

rebuild=false
tool=all

while [ $# -gt 0 ]; do
  case "$1" in
    --rebuild) rebuild=true ;;
    --tool) tool="${2:-}"; shift ;;
    *) echo "usage: $0 [--rebuild] [--tool sbt|mill|all]" >&2; exit 2 ;;
  esac
  shift
done

case "$tool" in
  sbt | mill | all) ;;
  *) echo "unknown tool '$tool': expected sbt, mill or all" >&2; exit 2 ;;
esac

sbt_corpuses=(test-project test-project-213 test-project-js test-project-native)
mill_corpuses=(test-project test-project-213)

findSourceNewerThanStamp() {
  local corpus="$1" stamp="$2" first="$3" second="$4"
  find "$corpus" \
    \( -path "$corpus/target" -o -path "$corpus/out" \) -prune -o \
    -type f \( -name "*.$first" -o -name "*.$second" \) -newer "$stamp" -print -quit
}

buildCorpus() {
  local name="$1" outputName="$2" first="$3" second="$4"
  shift 4
  local corpus="$corpuses/$name"
  local output="$corpus/$outputName"
  local stamp="$output/.slicer-corpus-built"

  if [ "$rebuild" = true ]; then rm -rf "$output"; fi
  if [ "$rebuild" = false ] && [ -f "$stamp" ] &&
    [ -z "$(findSourceNewerThanStamp "$corpus" "$stamp" "$first" "$second")" ]; then
    return
  fi

  echo "building the $name corpus with $*"
  (cd "$corpus" && "$@")
  mkdir -p "$output"
  touch "$stamp"
}

if [ "$tool" != mill ]; then
  for name in "${sbt_corpuses[@]}"; do
    buildCorpus "$name" target scala sbt sbt compile
  done
fi

if [ "$tool" != sbt ]; then
  for name in "${mill_corpuses[@]}"; do
    buildCorpus "$name" out scala mill ./mill __.semanticDbData
  done
fi
