#!/usr/bin/env bash
# One-command setup (and refresh) for the scala-semantic MCP server.
#
# Run on a fresh checkout, and again whenever dependencies or the Scala version
# change. Idempotent.
#
#   1. sbt mcpClientConfig — installs the launcher to ~/.local/bin and caches the
#      server jar. It writes .mcp.json but does NOT generate the classpath, and
#      points at a classpath path it never creates.
#   2. `sbt writeSemanticClasspath` writes that classpath from every module's test
#      classpath — sbt 2 classpaths are virtual-file references, so the build resolves
#      them through its own fileConverter rather than any log being parsed. Without
#      it the presentation-compiler overlay cannot resolve uncompiled edits, and
#      silently falls back to the on-disk SemanticDB index.
#   3. Rewrites .mcp.json to absolute paths for this machine. It is gitignored — it
#      holds absolute paths into the local Coursier cache, and ${HOME} /
#      ${CLAUDE_PROJECT_DIR} are not expanded in .mcp.json (a config using them
#      connects without error and returns empty results for every query).
#
# Requires java and sbt on PATH. Afterwards, reconnect the server in Claude Code
# (/mcp) so it picks up the new config.
set -euo pipefail

cd "$(dirname "$0")/.."
repo="$PWD"
classpath="$repo/.claude/scala-semantic-classpath.txt"
log="$(mktemp)"
backup="$(mktemp)"
trap 'rm -f "$log" "$backup"' EXIT

# Snapshot .mcp.json BEFORE sbt runs: step 1 writes that file, so merging into
# whatever it leaves behind would preserve nothing if it overwrote rather than
# merged. The merge below reads this copy instead.
if [ -f "$repo/.mcp.json" ]; then cp "$repo/.mcp.json" "$backup"; fi

echo "==> Installing the MCP launcher and caching the server jar..." >&2
sbt -batch mcpClientConfig > "$log" 2>&1 || { cat "$log" >&2; exit 1; }

echo "==> Writing the classpath (compiles first; this takes a moment)..." >&2
sbt -batch writeSemanticClasspath > "$log" 2>&1 || { cat "$log" >&2; exit 1; }

python3 - "$classpath" "$repo" "$backup" <<'PY'
import json, os, re, sys

classpath, repo, backup = sys.argv[1:4]

# Start from the config sbt just wrote so the pinned server version and any flags it
# sets are preserved, then correct the paths it cannot know.
generated = os.path.join(repo, ".mcp.json")
with open(generated) as handle:
    config = json.load(handle)

server = config["mcpServers"]["scala-semantic"]
flags = [a for a in server.get("args", []) if a.startswith("-")]
server["args"] = [repo, classpath] + flags

# Pin the server to the sbt plugin's version. mcpClientConfig writes no env, so
# without this the launcher serves whatever release is newest and drifts away from
# the plugin the build actually uses.
plugins = open(os.path.join(repo, "project", "plugins.sbt")).read()
pinned = re.search(r'sbt-scalasemantic-mcp"\s*%\s*"([^"]+)"', plugins)
if pinned:
    server.setdefault("env", {})["SCALASEMANTIC_VERSION"] = "v" + pinned.group(1)

# Merge rather than overwrite: .mcp.json is gitignored, so any other MCP server a
# developer configured here would be deleted with no diff to notice it by. The merge
# reads the snapshot taken before sbt ran, since mcpClientConfig has already
# rewritten the copy on disk by this point.
try:
    with open(backup) as handle:
        merged = json.load(handle)
except (OSError, ValueError):
    merged = {}
merged.setdefault("mcpServers", {})["scala-semantic"] = server
with open(generated, "w") as handle:
    json.dump(merged, handle, indent=2)
    handle.write("\n")

entries = [line for line in open(classpath).read().splitlines() if line.strip()]
outputs = [e for e in entries if "/target/" in e and not e.endswith(".jar")]
print("classpath: %d entries, %d module outputs -> %s"
      % (len(entries), len(outputs), classpath))
print("config:    %s" % generated)
print("\nReconnect the server in Claude Code (/mcp) to pick this up.")
PY
