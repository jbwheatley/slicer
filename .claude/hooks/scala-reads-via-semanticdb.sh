#!/usr/bin/env bash
input=$(cat)
python3 - "$input" <<'PY'
import sys, json, os, re
from pathlib import Path

data = json.loads(sys.argv[1])
tool = data.get("tool_name", "")
args = data.get("tool_input", {})

indexed = re.compile(r"(^|/)[\w.-]+/src/(main|test)/scala/")
exempt = re.compile(r"/(resources|target)/|(^|/)example/")

root = Path(os.environ.get("CLAUDE_PROJECT_DIR") or os.getcwd()).resolve()
outputs = ("target", "out")

def relative_source(path):
    try:
        return (root / path).resolve().relative_to(root).as_posix()
    except ValueError:
        return None

def has_semanticdb(path):
    relative = relative_source(path)
    if relative is None:
        return True
    wanted = "/" + relative + ".semanticdb"
    basename = Path(relative).name + ".semanticdb"
    for output in outputs:
        directory = root / output
        if not directory.is_dir():
            continue
        for found in directory.rglob(basename):
            if found.as_posix().endswith(wanted):
                return True
    return False

def covered(path):
    return (
        bool(path)
        and bool(indexed.search(path))
        and not exempt.search(path)
        and has_semanticdb(path)
    )

def read_of_source():
    path = args.get("file_path", "")
    return (path.endswith(".scala") and covered(path), path)

def search_of_source():
    scope = args.get("glob", "") or args.get("path", "") or ""
    return (".scala" in scope or covered(scope), scope)

def shell_read_of_source():
    command = args.get("command", "")
    readers = r"\b(cat|head|tail|less|more|sed|awk|rg|grep|ag|ack)\b"
    paths = [path for path in re.findall(r"[\w./-]+\.scala\b", command) if covered(path)]
    return (bool(re.search(readers, command)) and bool(paths), paths[0] if paths else "")

checks = {
    "Read": read_of_source,
    "Grep": search_of_source,
    "Glob": search_of_source,
    "Bash": shell_read_of_source,
}

denied, subject = checks.get(tool, lambda: (False, ""))()

if denied:
    reason = (
        "Blocked: text read of indexed Scala source (" + subject + "). CLAUDE.md routes these "
        "through the scala-semantic MCP tools - ToolSearch them first, they load deferred. "
        "annotated_source reads a file (format 'plain' for raw text, 'annotated' for the "
        "compiler's inferred types and synthesised implicits); document_outline surveys one "
        "without reading it; find_symbol resolves a name; find_usages finds every reference, "
        "renames and re-exports included. Answers describe the last compile: when a staleness "
        "warning fires, run `sbt Test/compile` rather than falling back to text search. "
        "Fixtures under resources/, the example project and any source the build has not "
        "emitted SemanticDB for carry no index entry and stay readable here."
    )
    print(json.dumps({
        "hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "permissionDecision": "deny",
            "permissionDecisionReason": reason,
        }
    }))
PY
