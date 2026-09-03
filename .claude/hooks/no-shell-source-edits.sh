#!/usr/bin/env bash
input=$(cat)
python3 - "$input" <<'PY'
import sys, json, re

data = json.loads(sys.argv[1])
cmd = data.get("tool_input", {}).get("command", "")

source = re.compile(r"\.(scala|sbt)$")
interpreters = r"(python3?|perl|ruby|node|osascript|php)"

delimiters = re.findall(r"<<-?\s*['\"]?(\w+)", cmd)
spoken = cmd
for delimiter in delimiters:
    spoken = re.sub(
        r"<<-?\s*['\"]?" + delimiter + r"['\"]?\n.*?\n\s*" + delimiter + r"\b",
        " <<HEREDOC ",
        spoken,
        flags=re.S,
    )

bodies = "\n".join(
    match.group(1)
    for delimiter in delimiters
    for match in re.finditer(
        r"<<-?\s*['\"]?" + delimiter + r"['\"]?\n(.*?)\n\s*" + delimiter + r"\b", cmd, re.S
    )
)

def targets(pattern, group):
    return [match.group(group) for match in re.finditer(pattern, spoken)]

redirects = targets(r"(^|[^0-9<>])>>?\s*[\"']?([^\s\"'|;]+)", 2)
tees = targets(r"\btee\b(?:\s+-\w+)*\s+[\"']?([^\s\"'|;]+)", 1)
in_place = [
    operand
    for pattern in (
        r"\bsed\b[^|;]*\s-[a-zA-Z]*i\b[^|;]*",
        r"\bperl\b[^|;]*\s-[a-zA-Z]*i\b[^|;]*",
        r"\bawk\b[^|;]*-i[\s=]*inplace[^|;]*",
    )
    for match in re.finditer(pattern, spoken)
    for operand in re.findall(r"[\w./-]+", match.group(0))
]

written = [
    (path, what)
    for paths, what in (
        (redirects, "a redirect into a source file"),
        (tees, "tee into a source file"),
        (in_place, "an in-place edit of a source file"),
    )
    for path in paths
    if source.search(path)
]

script = bodies if re.search(interpreters + r"\b[^|;]*\s-\s*(<|$)", spoken) else ""
inline = re.search(interpreters + r"\b[^|;]*\s-[ce]\s+(.*)", spoken)
script += "\n" + inline.group(2) if inline else ""
opens = re.compile(
    r"(open|write_text|write|writeFile|writeFileSync|File\.write|IO\.write|\bcp\b|\bmv\b)"
    r"[^\n]*[\w./-]+\.(scala|sbt)\b"
)
if opens.search(script):
    written.append((opens.search(script).group(0), "an inline interpreter script"))

if written:
    subject, what = written[0]
    reason = (
        "Blocked: " + what + " (" + subject + "). Edit Scala and .sbt sources with the Edit or "
        "Write tool — the repo's source guards (comments, munit timeouts, CLAUDE.md rules) "
        "match Write|Edit only, so shell writes bypass them and hide the diff from review."
    )
    print(json.dumps({
        "hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "permissionDecision": "deny",
            "permissionDecisionReason": reason,
        }
    }))
PY
