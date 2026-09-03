#!/usr/bin/env bash
input=$(cat)
python3 - "$input" <<'PY'
import sys, json, os, glob, hashlib

data = json.loads(sys.argv[1])
session = data.get("session_id", "nosession")
safe = hashlib.sha256(session.encode()).hexdigest()[:16]

pattern = os.path.join(
    os.environ.get("TMPDIR", "/tmp"), "claude-reread", safe + "-*.json"
)
for state_path in glob.glob(pattern):
    try:
        os.remove(state_path)
    except OSError:
        pass
PY
