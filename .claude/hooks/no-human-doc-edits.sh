#!/usr/bin/env bash
input=$(cat)
python3 - "$input" <<'PY'
import sys, json, os, re

data = json.loads(sys.argv[1])
tool = data.get("tool_name", "")
ti = data.get("tool_input", {})

docs = re.compile(r"(^|/)(README|CONTRIBUTING)\.md$", re.I)
mentions = re.compile(r"(README|CONTRIBUTING)\.md", re.I)
writes = re.compile(
    r"(^|[^0-9<>])>>?\s*[\"']?[^\s\"'|;]*(README|CONTRIBUTING)\.md"
    r"|\btee\b(?:\s+-\w+)*\s+[\"']?[^\s\"'|;]*(README|CONTRIBUTING)\.md"
    r"|\b(sed|perl)\b[^|;]*\s-[a-zA-Z]*i\b[^|;]*(README|CONTRIBUTING)\.md"
    r"|\b(cp|mv|rm|truncate)\b[^|;]*(README|CONTRIBUTING)\.md",
    re.I,
)

reason = (
    "Blocked: README.md and CONTRIBUTING.md are written by humans, for humans. "
    "Tell the user what needs saying there and let them write it; put rules meant "
    "for you in CLAUDE.md instead."
)

def deny():
    print(json.dumps({
        "hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "permissionDecision": "deny",
            "permissionDecisionReason": reason,
        }
    }))
    sys.exit(0)

if tool == "Bash":
    command = ti.get("command", "")
    if mentions.search(command) and writes.search(command):
        deny()
else:
    path = ti.get("file_path", "")
    if docs.search(os.path.normpath(path)):
        deny()
PY
