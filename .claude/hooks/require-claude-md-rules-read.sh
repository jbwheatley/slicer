#!/usr/bin/env bash
input=$(cat)
python3 - "$input" <<'PY'
import sys, json, os, re

data = json.loads(sys.argv[1])
ti = data.get("tool_input", {})
fp = ti.get("file_path", "")

norm = fp.replace("\\", "/")
if not re.search(r"(^|/)CLAUDE\.md$", norm):
    sys.exit(0)

transcript = data.get("transcript_path", "")
if not transcript:
    session = data.get("session_id", "")
    cwd = data.get("cwd", os.getcwd())
    slug = cwd.replace("/", "-")
    transcript = os.path.expanduser(f"~/.claude/projects/{slug}/{session}.jsonl")

read_it = False
try:
    with open(transcript) as f:
        for line in f:
            if "maintaining-claude-md.md" not in line:
                continue
            try:
                obj = json.loads(line)
            except ValueError:
                continue
            message = obj.get("message")
            blocks = message.get("content", []) if isinstance(message, dict) else []
            for block in blocks if isinstance(blocks, list) else []:
                if isinstance(block, dict) and block.get("type") == "tool_use" and block.get("name") == "Read":
                    if str(block.get("input", {}).get("file_path", "")).endswith("maintaining-claude-md.md"):
                        read_it = True
            if read_it:
                break
except OSError:
    sys.exit(0)

if not read_it:
    reason = (
        "Blocked: read .claude/maintaining-claude-md.md before editing CLAUDE.md. "
        "It defines what belongs, the no-structure/no-archaeology rules and the one-home rule. "
        "Read it, then retry."
    )
    print(json.dumps({
        "hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "permissionDecision": "deny",
            "permissionDecisionReason": reason,
        }
    }))
PY
