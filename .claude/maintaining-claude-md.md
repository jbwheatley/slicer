# Maintaining CLAUDE.md

> Read before editing `CLAUDE.md`. Not needed for normal feature work.

This doc load into LLM context on every task. Every word cost budget on every future task. Bar for adding high. Bar for keeping high.

## What belongs

**Rules, design decisions, invariants — never structure.** Adding file, hook, suite, or rule that follow already-documented pattern **not** doc change. Update CLAUDE.md only when:

- rule added, changed, or dropped;
- design decision made or reversed;
- new invariant or silent-failure footgun found;
- something documented became false — **delete it**.

To find code, search it. Code always more accurate than prose about it. Lines that list files, classes, or commands = map that go stale silently — delete those lines on sight.

## What doesn't

- **Anything the code already state.** Import paths and package names, what a class extend or mix in, which members a type expose, where a file live, what a function do. The compiler or one look at the file answer these faster and never go stale. A **rule about** code earn its place; a **description of** code never do — and a description that drift is worse than no line, because it read as authoritative.
- **Anything not in code right now.** Doc describe code as it is on this commit — no future plans, no hypotheticals.
- **History and diffs.** "We considered X", "this replaced Z", "Y now gone" — meaningless once merged. Write current state.
- **Restatements of rule stated elsewhere in the file.** Every rule has exactly **one home** section. Other sections link, never repeat — rule stated twice drift and double-load into context.

Three tests before sentence stays:

1. **Does it change what someone does?** If no reader make different decision, it never earned tokens.
2. **Would the code have told you faster?** If opening the file, or the compile error, answer it quicker than this doc, delete the sentence. This doc exist for what code cannot say: why a rule hold, what break when it's broken, an invariant spanning files nobody read together. Naming a file as the *home* of a rule is fine; narrating what's inside it is not.
3. **Is *why* load-bearing?** State rule and stop. Rule keep *why* only when reader face real alternative and would pick wrong one. No alternative = no decision = no line, however tempting the thing look to a reader who don't know that: defending choice nobody can unmake is argument with imagined reader, and cost tokens every task. "Deliberate", "by choice", "on purpose" with no consequence attached = filler — cut it.

If addition need doc edit to be understood, signal that **pattern** undocumented — document pattern, not instance.

## Structure

**Rules** and **gotchas / invariants** only, grouped by area. Decision worth recording = rule worth stating as one.
