# slicer

Extract compilable vertical slice of Scala codebase from SemanticDB.

## Entry points

- **No standalone CLI, no path flags, no switches outside options panel.** Both entry points open TUI; nothing writes slice without it.
- **Nothing sniffs a build**: no detection, no shelling out to sbt or mill for versions or dependencies.
- **Mill subprocess told everything, asks mill nothing.** `mill show __.mvnDeps` from inside `slice` deadlocks on daemon lock that command holds.
- **`sliceClear` removes slice directories, never root.** User picked root, may keep own files in it.
- `SlicerModule` must **extend** `ScalaModule`; self-type compiles, then fails at run time with "Unable to resolve command". `Task.dest` cannot be argument to task applied inside command — hand picker output directory over as task.
- **Resize = SIGWINCH, never timer.** Measuring terminal forks `stty size`, so sampling per tick costs fork per tick and lands redraw late.
- **Mill picker opens in own process, never daemon**, whose stdout is log and whose `/dev/tty` belongs to whoever started it; `Jvm.callInteractiveProcess` routes it back to launcher with stdio inherited raw, so `./mill slice` works without `--no-daemon`.
- Picker refuses unless `test -t 0 && test -t 1` passes on its stdio; `System.console()` cannot be that check — stops discriminating on JDK 22+.
- **Leaving picker must not touch scrollback**; layoutz clears it on exit unless the terminal refuses to.
- **sbt terminal is own module `sbt-tui`, package `sbt`, depending on no slicer module** — `Terminal.get`, raw mode and `printStream` are `private[sbt]`, and package merely *named* `slicer.sbt` does not count.
- **Screen decorator, escapes and terminal size tracking are `tui-viewport`, package `tui.viewport`, no slicer or sbt dependency**: layoutz repaints whole frame per tick, so every driver needs the same screen handling and a size to lay out against. Driver measuring size its own way keeps that tracking in its own module, never in a `slicer-*` one.

## Versions & the example project

- **Git tag is version; deriving it from anything else breaks `example/`**, which hardcodes the snapshot CI publishes locally. dynver resolves at build load, so tag made under running sbt needs `reload`.
- **Never publish, locally or otherwise** — maintainer's, and `example/` runs only once they have.

## Sliced languages

- Slicer reads **Scala 3 and Scala 2.13**, one corpus each. New Scala-2-only logic goes behind `ScalaVersionRules` in own file, never as branch in `Index` or `Reachability`.
- Scala 2 SemanticDB carries synthetics only under `-P:semanticdb:synthetics:on`; without it implicit arguments and conversions invisible. **Every entry point reads that flag from `ScalaVersionRules`** — plugin holding own copy silently slices Scala 2 without implicits.
- **Scala 3 corpus pinned to newest stable Scala 3, never LTS**: corpus is input specimen, so newest syntax has to be in it.
- **Brace and indentation syntax both input; slice stays in syntax it arrived in.** Only emitter's own syntax written: emptied body, its `:` or `with`, `end` marker outliving its definition. Given emptied of members still needs body; class, trait, object drop theirs.
- **Macro expansion is call-site fact, so definition side over-approximates**: anything `expandsAtCallSite` keeps every given in owner's scope, and string literal equal to definition's fully qualified name is edge whose target keeps its members — only way `Symbol.requiredModule("a.b.C")` / `c.mirror.staticModule` survive.
- **Scala 3 compiles macro implementation and definition in one run; Scala 2 answers `macro implementation not found`**, so Scala 2 slice emits module holding implementations as own project.
- **Kept type keeps what its supertypes define**, concrete members of parents and self-types included, bodies and all: SemanticDB records no occurrence for default the subclass never names. Abstract members left to rules that keep them.
- **Edges out of inherited member are inherited too.** Default calling overridable hook is dispatch inside kept behaviour, not use; treating it as use fans out to every implementation and swallows project.
- **Implementations follow generic reference, never inheritance.** Slicing `Bar extends Foo` keeps `Foo`, leaves `Baz`; call through `Foo` keeps both.
- **Sliced codebases are pure Scala, or Scala calling Java.** Nothing resolves Java reference to Scala definition, so slice of project that calls back will not compile.
- **Java read by text, pruned by file, emitted whole**: no javac SemanticDB, so every pruning rule stops at `.java` boundary. Only Java types are indexed, so no Java definition can be slice root, and edge under Java type's prefix redirects to that type. Java's own references are over-approximated.
- Definitions inside `Type.Refine` not indexed: refinement member is part of type, so pruning it changes type.

## Sliced build tools

- **Emitted build is one module, except modules Scala 2 macros force out**: root holding kept macro implementation, and every root its implementations reach, emitted at its original directory as separate project root `dependsOn`. Nothing else reproduces sliced module graph.
- Emitted mill build must stay **root** module. Named top-level module roots its `sources` at `<name>/`, so every source root resolves to directory that does not exist and mill compiles nothing while reporting success.
- **Emitter writes build from resolved values; nothing copies original build file**, which is code over module graph slice does not have and carries plugins and fatal warnings slice cannot satisfy. No resolvers either: whoever slices project built it first.
- **Scalac options filtered before emission**: slice has emptied bodies, so warning-to-error flags fail correct slice, and semanticdb flags and `-Xplugin:<path>` go too. Entry points hand every module's options over in order and undeduplicated — dropping duplicates orphans value of value-taking flag, and scalac reads bare path as source file.
- **Toolchain's own libraries are not dependencies**; emitting them asks for artifacts like `scala3-library_native0.5_3`, which do not exist.
- **Neither plugin may depend on platform plugins**, so each entry point reads platform off what its build already resolved: sbt off library platform's plugin adds, mill off module type it is mixed into.
- **`Dependency.platformed` means artifact resolves for platform, not that it was written with platform operator.** mill carries platform per dependency; sbt 2 carries it per project, where `%%` already resolves platform artifact and spelling prefix out doubles it, and sbt 1 is reverse.
- **Generated sources copied, never regenerated** — slice carries no generator, and copies keep package path they were generated into.

## Language & build

- **Scala 3, classic syntax**: braces, never significant indentation, enforced by `-no-indent` and `.scalafmt.conf`.
- Before any commit: `sbt commitCheck` must pass.
- Type compared as whole gets `given Eq[T] = Eq.fromUniversalEquals` in companion.
- Fatal warnings on: discard non-Unit result with `: Unit` ascription, not trailing `()`, and keep matches exhaustive with unguarded final case rather than guard.
- `conflictWarning` off for mill plugin because mill classpath legitimately mixes `_3` and `_2.13` copies of scala-xml and scala-collection-compat.

## Style

- **Analysis and emission never print.** Errors travel as `Either[SliceFailure, A]`, unreadable inputs go into index warnings, and only picker and two plugins write to console.
- Vocabularies get own type, never strings or bare `Int` cursor into parallel `Vector` of labels.
- **Functional by default**: expression-oriented, immutable collections, no early returns.
- **Name says what thing does**, readable without opening it: verb + object. Bare noun or participle names situation, not action.
- **One top-level definition per file, file named after it.** Companion shares its class's file. Corpuses keep multi-definition files.
- **Public API is entry points and `SbtLayoutzApp` only**: `SlicerPlugin`, `SlicerModule` and what their signatures expose. Everything else is `private[slicer]`, or narrower where one package holds it.
- **Full-word variables** — `index`, not `idx`. Established short idioms stay: `sym`, `tpe`, `pos`, `out`.
- **No comments in `.scala`, `.sbt`, `.sh` or `.yml` sources**: the name carries the explanation, and scalafix directives are the only exception. Comments in corpus are data, not documentation — suites assert on them, leave alone.
- **`README.md` and `CONTRIBUTING.md` are human-written**: say what belongs there and let the maintainer write it. Rules meant for you live here.

## Verification

- **Corpuses built by their own script, never from inside sbt**: suite only checks output is there and fails with the build command to run, and no sbt task builds one. Shelling `sbt` or `./mill` out of a running sbt server nests daemon in daemon — inner thin client races its own `active.json`, inner mill daemon steals the CPU suite timeouts are measured against. **Rebuild mode skips nothing, and CI uses it**: corpus SemanticDB are symlinks into sbt's shared cache, and pruned cache leaves links reading as no file rather than stale one.
- **Plugins stay out of corpuses**: `mill-build` dependency on unpublished plugin, or triggered `SlicerPlugin` changing `semanticdbOptions`, breaks suites.
- **Both tools emit identical Scala SemanticDB, so mill builds a corpus only where mill's own output is read** — its layout, its flags, its javac output. Slice content is tool-independent: emit suites index the sbt build and pass the other tool's values in.
- **One corpus carries every build tool whose output a suite reads**, one platform per corpus — sbt reads every `*.sbt` in directory as one build. Tool output directories must not collide, and staleness check must skip **all** of them: mill writes generated `.scala` under its own output, so skipping only output it stamps rebuilds forever.
- **Nothing drives compiler or mill subprocess from inside `test` body**: munit 30s timeout under parallel load on CI runner. **Timeouts stay where they are** — suite that outgrows one is work to move out of munit, not number to raise.
- **Rule change that narrows slice is correct only if `sbt corpusCheck/checkCorpusSlices` still passes** — it slices every definition of both corpuses, compiles each standalone in stages slice declares — one run over a Scala 2 macro slice fails way sliced project would — and runs outside `commitCheck`.
- **Harness emits through emitter it is testing.** Second emit loop in harness makes every corpus suite and corpus check pass on code nothing ships.
- **Suites test library, not harness**; harness helpers are proved by suites that use them, library helpers carry own suites.
- **No test may depend on local publish**: it would test last `publishLocal`, not working tree.
- **No test may run `slice` itself**: opens picker, blocks until killed.
- **`slicer-mill/` holds only plugin-shaped suites** — test whose subject is emission belongs in `slicer-core/`, whatever build tool it drives, except the emitted-build suites.
- **Suite that forks a build tool runs alone**: `emitted-build-check` runs its suites serially, and it and `slicer-mill` carry `forksABuildTool`, limited to one at a time. Two Scala Native builds on a CI runner starve each other past the munit timeout, and the timeout is not the thing to raise.
- **`testCheck` runs `suiteModules`, `checkEmittedBuilds` runs `emitted-build-check`**: module left out of that list has its suites run nowhere. CI gives each emitted-build suite a job of its own.
- **One emitted-build suite per build tool**, and the only place slice is compiled through its own generated build. Keep them symmetric: same root, same assertions, only file names and syntax differ. Assert class files came out, never just exit code — build whose sources resolve to nothing exits 0.
- **Build tool is run once per distinct emitted build shape**, every other case asserting on emitted text: shape is scala version, platform, module split and source roots, so same shape twice is a subprocess bought nothing. Emitted build carries no code but the slice's, so an empty one proves nothing — source roots need a file under them.
- New rule gets named test in reachability or emit suites, Scala-2 shaped ones included. Compile check says *something* broke; those say *what*.
- Re-baseline golden suites by deleting **every** baseline directory and rerunning; diff is review.
- **Never reformat or edit corpus to make slice pass.** Their awkward corners are the point, and their formatting is part of emitter's expected input.

## Finding code

- To find Scala code, use `scala-semantic` MCP tools (`ToolSearch` them first — they load deferred), not Read/Grep: they answer from compiler-emitted SemanticDB, so they see renames, re-exports, implicits text search misses. Their classpath is generated per machine by `scripts/gen-semantic-classpath.sh`, and must be regenerated after every dependency or Scala-version change.
- `.mcp.json` and `.claude/scala-semantic-classpath.txt` gitignored, holding machine-specific absolute paths: `${HOME}` / `${CLAUDE_PROJECT_DIR}` **not** expanded in `.mcp.json`, and config using them connects without error, returns empty results for every query.
- Answers describe last compile, not working tree.
