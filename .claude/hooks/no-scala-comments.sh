#!/usr/bin/env bash
input=$(cat)
python3 - "$input" <<'PY'
import sys, json

data = json.loads(sys.argv[1])
ti = data.get("tool_input", {})
fp = ti.get("file_path", "")

exempt = ("/src/test/test-project/", "/src/test/test-project-213/")

if not fp.endswith((".scala", ".sbt")) or any(d in fp for d in exempt):
    sys.exit(0)

content = ti.get("content") or ti.get("new_string") or ""
bad = []
for n, line in enumerate(content.splitlines(), 1):
    s = line.lstrip()
    if s.startswith("//") and not any(d in s for d in ("scalafix:off", "scalafix:on", "scalafix:ok")):
        bad.append((n, line.strip()))
    elif s.startswith("/*"):
        bad.append((n, line.strip()))

if bad:
    listed = "; ".join(f"L{n} {t}" for n, t in bad[:5])
    reason = (
        "Blocked: this repo forbids code comments in Scala and .sbt source. "
        "Remove these comment line(s) and let the code self-document: " + listed
    )
    print(json.dumps({
        "hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "permissionDecision": "deny",
            "permissionDecisionReason": reason,
        }
    }))
PY
