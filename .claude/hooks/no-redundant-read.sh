#!/usr/bin/env bash
input=$(cat)
python3 - "$input" <<'PY'
import sys, json, os, time, hashlib

READ_LINE_LIMIT = 2000

READ_BYTE_LIMIT = 256 * 1024

data = json.loads(sys.argv[1])
event = data.get("hook_event_name", "PreToolUse")
tool_input = data.get("tool_input", {})
path = tool_input.get("file_path", "")

if not path:
    sys.exit(0)

identity = "%s\0%s" % (data.get("session_id", "nosession"),
                       data.get("transcript_path", ""))

state_dir = os.path.join(os.environ.get("TMPDIR", "/tmp"), "claude-reread")
os.makedirs(state_dir, exist_ok=True)

now = time.time()
for stale in os.listdir(state_dir):
    full = os.path.join(state_dir, stale)
    try:
        if now - os.path.getmtime(full) > 86400:
            os.remove(full)
    except OSError:
        pass

def digest(value, length):
    return hashlib.sha256(value.encode()).hexdigest()[:length]

state_path = os.path.join(state_dir, "%s-%s.json" % (
    digest(data.get("session_id", "nosession"), 16), digest(identity, 16)))

try:
    with open(state_path) as handle:
        seen = json.load(handle)
except (OSError, ValueError):
    seen = {}

try:
    mtime = os.path.getmtime(path)
except OSError:
    sys.exit(0)  # missing file — let Read produce the real error

window = (tool_input.get("offset"), tool_input.get("limit"), tool_input.get("pages"))
is_whole = not any(w is not None for w in window)
key = path if is_whole else "%s@%s" % (path, window)

def entry(record):
    """Stored as [mtime, whole_read_was_complete]; tolerate the older bare float."""
    if isinstance(record, list) and len(record) == 2:
        return record[0], bool(record[1])
    return record, True

def covered():
    """Is this exact read already in context?"""
    if key in seen:
        recorded, _ = entry(seen[key])
        return recorded == mtime
    if not is_whole and path in seen:
        recorded, complete = entry(seen[path])
        return complete and recorded == mtime
    return False

def delivered_whole_file():
    """Whether a whole-file read of this path returned all of it."""
    if os.path.splitext(path)[1].lower() in (".pdf", ".ipynb"):
        return False
    try:
        with open(path, "rb") as handle:
            content = handle.read()
    except OSError:
        return False
    return len(content) <= READ_BYTE_LIMIT and content.count(b"\n") <= READ_LINE_LIMIT

def failed(response):
    """A Read that produced no content must not be recorded as read."""
    if isinstance(response, dict):
        return bool(response.get("error") or response.get("is_error"))
    return response is None

def record(entry_key, value):
    """Merge one entry into the record under a lock, re-reading it first. The tool
    calls in a single assistant block run concurrently, so a plain read-modify-write
    over the copy loaded at startup lets the later writer drop whatever the earlier
    one added — and the dropped file then gets read a second time, which is exactly
    the cost this hook exists to prevent."""
    lock = state_path + ".lock"
    handle = None
    for _ in range(50):
        try:
            handle = os.open(lock, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
            break
        except FileExistsError:
            try:
                if time.time() - os.path.getmtime(lock) > 5:
                    os.remove(lock)
                    continue
            except OSError:
                pass
            time.sleep(0.02)
    try:
        try:
            with open(state_path) as current_handle:
                current = json.load(current_handle)
        except (OSError, ValueError):
            current = {}
        current[entry_key] = value
        tmp = "%s.%d.tmp" % (state_path, os.getpid())
        with open(tmp, "w") as tmp_handle:
            json.dump(current, tmp_handle)
        os.replace(tmp, state_path)
    finally:
        if handle is not None:
            os.close(handle)
            try:
                os.remove(lock)
            except OSError:
                pass

if event == "PostToolUse":
    if not failed(data.get("tool_response")):
        record(key, [mtime, delivered_whole_file() if is_whole else True])
elif covered():
    what = "This file" if is_whole else "This range of the file"
    print(json.dumps({
        "hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "permissionDecision": "deny",
            "permissionDecisionReason": (
                "Blocked: %s was already read in this session and has not changed "
                "since (mtime identical). Its content is already in context — scroll "
                "back rather than paying for a second copy.\n\n"
                "Reading again is allowed once the file is edited, or after a "
                "compaction clears the session record.\n\n"
                "Path: %s" % (what, path)
            ),
        }
    }))
PY
