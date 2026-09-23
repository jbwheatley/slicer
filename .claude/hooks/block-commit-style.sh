#!/usr/bin/env bash
input=$(cat)
python3 - "$input" <<'PY'
import sys, json, re

data = json.loads(sys.argv[1])
cmd = data.get("tool_input", {}).get("command", "")

def deny(reason):
    print(json.dumps({
        "hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "permissionDecision": "deny",
            "permissionDecisionReason": reason,
        }
    }))
    sys.exit(0)

attribution = re.compile(r"claude\.com/claude-code|generated with \[?claude|co-authored-by|\U0001F916", re.IGNORECASE)

if re.search(r"\bgh\s+pr\s+(create|edit)\b", cmd) or re.search(r"\bgh\s+api\b[^\n]*\bpulls\b", cmd):
    body_files = re.findall(r"(?:--body-file|-F)[\s=]+['\"]?([^'\"\s]+)", cmd)
    texts = [cmd]
    for path in body_files:
        try:
            with open(path, encoding="utf-8") as handle:
                texts.append(handle.read())
        except OSError:
            pass
    if any(attribution.search(text) for text in texts):
        deny("Blocked PR-style rule: Claude Code attribution in the PR description — PR overviews carry none.")
    sys.exit(0)

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
    deny("Blocked commit-style rule(s): " + " ".join(violations))
PY
