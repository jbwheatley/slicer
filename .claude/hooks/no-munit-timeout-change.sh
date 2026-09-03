#!/usr/bin/env bash
input=$(cat)
python3 - "$input" <<'PY'
import sys, json, os, re

data = json.loads(sys.argv[1])
ti = data.get("tool_input", {})
fp = ti.get("file_path", "")

pattern = re.compile(r"munit(IO)?Timeout")

def timeouts(text):
    return [line.strip() for line in text.splitlines() if pattern.search(line)]

def deny(reason):
    print(json.dumps({
        "hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "permissionDecision": "deny",
            "permissionDecisionReason": reason,
        }
    }))
    sys.exit(0)

advice = (
    "Blocked: munitTimeout and munitIOTimeout are off limits. The existing values are tuned to the "
    "suites' real runtimes, and a suite that needs a longer one is a performance "
    "regression to investigate, not a number to raise or add. Ask the user before "
    "any munit timeout moves or appears."
)

if "content" in ti:
    before = []
    if os.path.exists(fp):
        with open(fp, encoding="utf-8") as handle:
            before = timeouts(handle.read())
    after = timeouts(ti.get("content") or "")
else:
    before = timeouts(ti.get("old_string") or "")
    after = timeouts(ti.get("new_string") or "")

added = [line for line in after if line not in before]
if added:
    deny(f"{advice} Rejected: {'; '.join(added)}")
PY
