#!/usr/bin/env bash
input=$(cat)
python3 - "$input" <<'PY'
import sys, json, re

data = json.loads(sys.argv[1])
cmd = data.get("tool_input", {}).get("command", "")

if not re.search(r"\bgit\b[^\n]*\bcommit\b", cmd):
    sys.exit(0)

violations = []

if "<<" in cmd:
    violations.append("HEREDOC in the commit command — pass the message with -m instead.")

if re.search(r"co-authored-by", cmd, re.IGNORECASE):
    violations.append("Co-Authored-By trailer — commits carry no co-author.")

emoji = re.compile(
    "[" 
    "\U0001F000-\U0001FAFF"
    "\U00002600-\U000027BF"
    "\U00002B00-\U00002BFF"
    "\U0001F1E6-\U0001F1FF"
    "\U0000FE00-\U0000FE0F"
    "\U00002190-\U000021FF"
    "]"
)
if emoji.search(cmd):
    violations.append("emoji in the commit message — none allowed.")

if violations:
    reason = "Blocked commit-style rule(s): " + " ".join(violations)
    print(json.dumps({
        "hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "permissionDecision": "deny",
            "permissionDecisionReason": reason,
        }
    }))
PY
