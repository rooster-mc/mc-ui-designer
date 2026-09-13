# Architecture review — 160 (Import a design file as named chest scaffolds)

## Round 1
### Verdict
Ship with three small fixes. The `place/` package fits the established
Bukkit-coupled/`export`-pure split, `JsonImporter` keeps `export` free of Bukkit
behind an injected matcher, layout ownership stays in the world/placer, and
`UiDesignerCommand` remains the integration point rather than the implementation.
Two `docs/architecture.md` statements are stale/untruthful, and one path branch in
the command duplicates config rather than delegating to it.

### Findings
#### 1. `resolveScaffoldFile` re-implements `UiDesignerConfig.resolve`'s absolute/normalise rule
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/UiDesignerCommand.kt:208-213`
- Problem: `resolveScaffoldFile` branches on `Path.of(rawFile).isAbsolute` and calls
  `path.normalize()` itself, then delegates the relative case to
  `config.resolvePath(rawFile)`. `UiDesignerConfig.resolve`
  (`config/UiDesignerConfig.kt:50-53`) already owns exactly that rule:
  `(if (path.isAbsolute) path else dataFolder.resolve(path)).normalize()`. So there
  are now two copies of the "absolute passes through, relative joins the data
  folder" contract, and they will drift: a change to `resolvePath` (e.g. a new
  relativisation or symlink rule) silently leaves the command's absolute branch
  behind. The stated purpose of `resolvePath` in `docs/architecture.md:189-195`
  ("exposes the same data-folder-normalising rule as `outputFile`") is undercut
  when the caller only uses it for the relative case.
- Suggested fix: keep only the blank guard and delegate everything else:
  `if (rawFile.isNullOrBlank()) config.outputFile else config.resolvePath(rawFile)`.
  `Path.of`/`InvalidPathException` handling is already covered by `config.resolve`,
  and `scaffold`'s surrounding `try` still maps it to `IoFailure`.

#### 2. Package tree describes `MaterialResolver` as returning a `Material`, but it returns a `Boolean`
- Location: `docs/architecture.md:35`
- Problem: the listing says
  `MaterialResolver.kt  item id -> Bukkit Material (reader's injected matcher)`.
  The code (`place/MaterialResolver.kt:6`) is
  `fun isKnown(id: String): Boolean = Material.matchMaterial(id) != null`, and the
  seam `JsonImporter.read(file, materialMatcher: (String) -> Boolean)`
  (`export/JsonImporter.kt:12`) takes a predicate. The package tree therefore
  misnames both the object's output type and, implicitly, the shape of the matcher
  seam a reader would go looking for.
- Suggested fix: describe it as the predicate it is, e.g.
  `item id -> Boolean (reader's injected matcher; production = Material.matchMaterial != null)`.

#### 3. Scaffold data-flow diagram places the placer's writes after `PlacementResult` and derives importer failures from it
- Location: `docs/architecture.md:264-284` (the `Scaffold is the reverse
  direction` block, specifically lines 275-283)
- Problem: two things are misrepresented. First, the arrow after
  `PlacementResult` reads `│ place empty chests, then ChestNamer.setName`, which
  puts the placement side effect in `UiDesignerCommand` after it receives the
  result. The placing and naming happen *inside* `ScaffoldPlacer.place`
  (`place/ScaffoldPlacer.kt:70-73`) before `Placed` is returned; the command only
  maps the result to a message. Second, the same arrow fans out to
  `ScaffoldOutcome (... ParseFailure ... IoFailure)`, implying all five outcomes are
  produced by the placer. `ParseFailure`/`IoFailure` are produced by the importer
  step (`UiDesignerCommand.kt:181-199`) and never reach the placer, while the top
  of the diagram already notes the `IOException`/parse branch. A reader tracing the
  flow is told the wrong component owns both concerns.
- Suggested fix: move `place empty chests, then ChestNamer.setName` up into the
  `ScaffoldPlacer.place` block (above `PlacementResult`), and label the final arrow
  as the command-level mapping of both the import step and `PlacementResult` onto
  `ScaffoldOutcome`, so `ParseFailure`/`IoFailure` visibly come from
  `JsonImporter`/`resolvePath`, not the placer.

### Non-findings
- **`place/` is the right package and the seams hold.** `ScaffoldPlacer` is the
  only new Bukkit-coupled placement unit, takes `World`/anchor/view explicitly with
  an injected `occupied: (Block) -> Boolean`, and its `Player` overload is a thin
  adapter; `place/` depends on the pure `export/UiChest` and `naming/ChestNamer`,
  never the reverse. No Bukkit type leaked into `export` or the command tree.
- **`JsonImporter` keeps `export` pure.** It imports only
  `kotlinx.serialization` and `java.nio.file`; the Bukkit-only `Material` check
  enters as the injected `(String) -> Boolean` matcher with the production
  implementation supplied by the command default, so the package's "pure Kotlin"
  seam in `docs/architecture.md:68-70` is intact. The `materialMatcher` parameter is
  a genuinely small earner (one caller, one injected test predicate), not
  premature generalisation.
- **`MaterialResolver`'s placement in `place/` is defensible.** It is a three-line
  Bukkit translation, and `place/` is already the Bukkit side; putting it in
  `export/` would break purity and in `commands/` would bury a reusable mapping in
  the integration file. It only answers `isKnown`, but ticket 170 will need the
  actual `Material` to build `ItemStack`s and can extend the same object with a
  `resolve(id): Material?` without touching the reader seam — an extension, not a
  rewrite.
- **World-owns-layout holds.** `JsonImporter` returns `List<UiChest>` with
  `position` left `null` (`@Transient`) and never computes coordinates; all physical
  layout (row along the view, facing, `RIGHT`/`LEFT` pairing) lives in
  `place/ScaffoldPlacer.layout`. The JSON format is untouched, matching
  `docs/design.md:45` and the 170 ticket's dependence on the same split.
- **`UiDesignerConfig.resolvePath` is cohesive; `jsonFiles()` is a mild but
  acceptable role expansion.** `resolvePath` is just the public form of the
  existing private `resolve` used by `outputFile`, so exposing it adds no new
  concept. `jsonFiles()` reads the filesystem, which stretches "typed view over
  config.yml", but it lists the data folder that config already owns and resolves
  against, and it is the completion source 170's `status`/`sync` will reuse. A new
  `DesignFiles` abstraction for a directory listing would not earn its keep; no
  change needed.
- **`UiDesignerCommand` is still the integration point, not the owner.** `scaffold`
  orchestrates (resolve path → importer → placer → outcome) and owns only message
  bodies, matching the existing `save`/`reload` shape; the injected
  `(Path) -> List<UiChest>` importer and `(Player, List<UiChest>) -> PlacementResult`
  placer keep the pipeline unit-testable without CommandAPI dispatch, and the
  plugin wiring in `UiDesignerPlugin.onEnable` needs no new arguments because both
  defaults are production. The only blemish is the duplicated resolve branch in
  finding 1.
- **Seams for 170 are present, nothing is over-generalised for it.** Ticket 170
  can reuse `JsonImporter.read`, the injected matcher pattern, `resolvePath`, and
  `jsonFiles` for `/uidesigner status [file]` and `/uidesigner sync [file]`;
  scaffold's `ScaffoldPlacer` is correctly not reused there (sync writes into
  existing chests). No scaffold-shaped abstraction was forced onto the sync path.
- **Manual-test entries are complete and correctly attributed.** MT-013 covers the
  live double/single placement, MT-014 the `scaffold` → `save` round trip, and
  MT-015 the anchor/obstruction plus missing/malformed-file rejections; each names
  ticket 160 and explains why MockBukkit cannot cover it, covering all
  non-automatable acceptance criteria in `docs/tasks/160-scaffold.md:45-53`.
- **`docs/design.md` records the decisions.** The new "Scaffold reads purely and
  places thinly" entry (`docs/design.md:143-156`) states the pure-reader/matcher
  seam, the 6→double vs other-rows→single approximation, the atomic pre-check, and
  the non-idempotence deferral, consistent with the code and the ticket.
- **`docs/data-format.md` needs no change.** It already documents `rows` 1..6,
  required/unique names, and row/slot ranges; the importer enforces exactly those,
  and the export-only `rows`-derives-from-3/6 sentence is not contradicted by the
  read path's accepted range.

## Round 2
### Verdict
Ship with two small doc fixes. All three round-1 findings are fixed and the docs
now match the code on the points I raised: `resolveScaffoldFile` delegates to
`UiDesignerConfig.resolvePath` (`UiDesignerCommand.kt:218-222`), the package tree
calls `MaterialResolver` a boolean predicate (`architecture.md:35`), and the
scaffold data-flow block puts placement/naming inside `ScaffoldPlacer.place` and
branches parse/IO failures off the importer step (`architecture.md:275-294`). The
`place/` seams, the pure `export` boundary, world-owns-layout, and the 170 seams
are all unchanged; only `docs/data-format.md` is now missing a rule the reader
enforces, plus one stale clause in the reader seam bullet.

### Findings
#### 1. `docs/data-format.md` does not record the reader's new non-empty rule
- Location: `docs/data-format.md:30-50` (Rules), `src/main/kotlin/dev/cypdashuhn/uidesigner/export/JsonImporter.kt:21-23`
- Problem: the empty-file no-op fix added a new reader-level constraint — an empty
  array is an invalid design and is rejected up front (design.md:147 and
  architecture.md:164 both say so). `docs/data-format.md` is the authoritative
  schema, and its Rules still describe only `name`/`rows`/slot and ordering
  invariants, so a hand-authored or reviewed `[]` reads as a valid file there
  while `scaffold` refuses it. This refines my round-1 "needs no change" note: the
  rule did not exist when round 1 was written.
- Suggested fix: add one Rules bullet, e.g. the array must contain at least one
  chest; an empty design is rejected (naming the file), so `scaffold` never reports
  a green no-op.

#### 2. The `InvalidDesignException` purity clause in the reader seam is stale and unclear
- Location: `docs/architecture.md:166` ("`InvalidDesignException` is not a `Path`-only concern, so the reader never names a Bukkit type.")
- Problem: the exception now carries `file: Path` and `detail: String`
  (`JsonImporter.kt:7-10`), so "not a `Path`-only concern" states the opposite of
  what the type is, and the sentence is the only explanation of why the pure
  reader is still allowed to validate materials. A reader cannot tell which
  invariant it protects or why the class lives in `export`.
- Suggested fix: state the invariant directly, e.g. "`InvalidDesignException`
  carries only `Path`/`String` fields, so the reader names no Bukkit type even
  though it validates item ids through the injected matcher."

### Non-findings
- **Round-1 finding 1 is fixed.** `resolveScaffoldFile` (`UiDesignerCommand.kt:218-222`)
  is now just the blank guard plus `config.resolvePath(rawFile)`, and
  `UiDesignerConfig` has a single public `resolvePath` used by both `outputFile`
  and the command (`UiDesignerConfig.kt:11-17`); no second copy of the
  absolute/relative rule remains.
- **Round-1 finding 2 is fixed.** `docs/architecture.md:35` now reads
  `item id -> Boolean (reader's injected matcher; production = Material.matchMaterial != null)`,
  matching `MaterialResolver.isKnown` and the `(String) -> Boolean` seam.
- **Round-1 finding 3 is fixed.** The scaffold data-flow block
  (`docs/architecture.md:275-294`) places and names inside the `ScaffoldPlacer.place`
  step, branches `InvalidDesignException`/`SerializationException` and
  `NoSuchFileException`/`IOException` off the importer step, and labels the final
  arrow as the command's mapping of both the import step and `PlacementResult`.
- **The fixes introduced no seam gap and no new abstraction.** `firstIsPlayer`
  threads through `PlacementResult.Obstructed` (`ScaffoldPlacer.kt:19-23`) and the
  private `Obstruction` helper stays internal to `place/`; `InvalidDesignException`
  still only carries `Path`/`String`; `export` has no Bukkit import and `place/`
  still depends inward on `export/UiChest` and `naming/ChestNamer`.
- **Seams for 170 hold.** `JsonImporter.read(file, matcher)`, the injected matcher
  pattern, `resolvePath`, and `jsonFiles` remain reusable by
  `/uidesigner status [file]` and `/uidesigner sync [file]`, and the boolean-only
  `MaterialResolver` can be extended with an actual `Material` resolver without
  touching the reader seam. The reconcile path still needs no scaffold-shaped
  abstraction.
- **`docs/design.md` matches the code.** The "Scaffold reads purely and places
  thinly" entry (`design.md:143-158`) carries the empty-design rejection, the
  pure-reader/matcher seam, the 3/6-vs-other-rows approximation, the atomic
  pre-check, and the player-vs-block obstruction distinction the code now
  implements.
- **`docs/manual-test.md` MT-013/MT-014/MT-015 are truthful and attributed to
  160.** They cover the live double/single placement (MT-013, now including the
  no-target fallback origin), the `scaffold` → `save` round trip (MT-014), and the
  anchor/obstruction plus missing/malformed-file rejections (MT-015); each names
  ticket 160 and its MockBukkit limitation.
