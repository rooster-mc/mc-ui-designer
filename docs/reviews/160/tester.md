# Tester review — 160 (Import a design file as named chest scaffolds)

## Round 1

### Verdict
The reader, placer, command and config layers are each tested at the right
level and the five `ScaffoldOutcome` branches, path resolution and suggestions
are all pinned. Two automatable gaps remain — the rendered scaffold messages
(including the "names the first blocked position" criterion) and the production
`JsonImporter` + `MaterialResolver` wiring — plus a thin manual assertion for the
anchor fallback and no console-sender guard. Ship with those added.

### Findings

#### 1. Scaffold failure/warning messages are never rendered in a test
- Location: `src/test/kotlin/dev/cypdashuhn/uidesigner/commands/UiDesignerCommandTest.kt:932-1002`
  (assert outcome data classes only), `src/test/kotlin/dev/cypdashuhn/uidesigner/util/MessagesTest.kt:236-269`
  (`allMessages()` omits every scaffold function), message bodies
  `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/UiDesignerCommand.kt:377-407`
- Problem: The `Obstructed`, `ParseFailure`, `IoFailure` and `NoTarget` tests
  assert only the `ScaffoldOutcome` value. Nothing calls `scaffoldMessage`, so the
  four non-`Placed` branches and the four failure-message bodies are unexercised.
  Two acceptance criteria are specifically about the rendered message —
  "message names the first blocked position" and "reports the offending entry" —
  and neither is asserted: a regression that dropped the coordinates, the reason,
  the file, or the trailing period would pass the whole suite. Because
  `allMessages()` omits them, the shared prefix/palette/period invariants in
  `MessagesTest` do not cover scaffold either.
- Suggested fix: add body tests — `scaffoldObstructedMessage(3, BlockPos(4,5,6))`
  contains the count, `(4, 5, 6)` and "nothing placed" (plus the singular
  `1 target block is`), `scaffoldParseFailureMessage(path, "bad rows")` contains
  the path and reason, `scaffoldIoFailureMessage(path, null)` falls back to the
  hint, `scaffoldNoTargetMessage()` wording — and register all four in
  `allMessages()`. Alternatively mirror the save dispatch tests with
  `plainMessage()` for the `Obstructed`/`ParseFailure` outcomes to pin the
  outcome→message wiring as well.

#### 2. The production importer matcher is never exercised by any test
- Location: `src/test/kotlin/dev/cypdashuhn/uidesigner/commands/UiDesignerCommandTest.kt:1063-1100`
  (both helpers default `importer` to a fake lambda), command default at
  `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/UiDesignerCommand.kt:41-42`,
  `src/main/kotlin/dev/cypdashuhn/uidesigner/place/MaterialResolver.kt:6`,
  `src/test/kotlin/dev/cypdashuhn/uidesigner/export/JsonImporterTest.kt:175-179`
- Problem: Every command test injects a fake `(Path) -> List<UiChest>`, so the
  production default `JsonImporter.read(it, MaterialResolver::isKnown)` is never
  invoked on the scaffold path (the constructor-default tests at `:452-671` only
  call `save`/`reload`). `MaterialResolver.isKnown` has no test at all, so the
  contract between the exporter's id format (`stack.type.key.toString()` →
  `"minecraft:stone"`, `DoubleChestGrouper.kt:192`) and `Material.matchMaterial`
  is unguarded automatically; only MT-014 (manual) proves a written file can be
  scaffolded. A broken/renamed matcher keeps every test green while scaffold
  fails closed in production.
- Suggested fix: add a small `MaterialResolverTest` asserting a known id
  (`minecraft:stone`) is true and an unknown id is false, and/or one command test
  that leaves `importer` at its default, writes a real `design.json` (via
  `JsonExporter.toJson` + `Files.writeString`) and asserts the injected placer
  receives those chests. Use the injected placer — the default player-entry
  placer cannot run under MockBukkit.

#### 3. Console sender is not tested for `scaffold`
- Location: `src/test/kotlin/dev/cypdashuhn/uidesigner/commands/UiDesignerCommandTest.kt:686-700`
  (`console save does not export`) versus the scaffold dispatch tests at
  `:1004-1061`; guard at
  `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/UiDesignerCommand.kt:123-135`
- Problem: `save` has a console no-op test, but `scaffold` has no analogue even
  though its no-arg and file `onExecute` bodies rely on the same
  `playerOrNull ?: return@onExecute` guard. A regression that dropped the guard
  (NPE) or imported/placed for a console sender would not be caught.
- Suggested fix: add `console scaffold does not import or place`, asserting the
  injected importer and placer are never called for `server.consoleSender` for
  both `uidesigner scaffold` and `uidesigner scaffold <file>`.

#### 4. The anchor fallback is gated but its result is not asserted
- Location: `docs/manual-test.md:24-26` (MT-013/MT-015),
  `src/main/kotlin/dev/cypdashuhn/uidesigner/place/ScaffoldPlacer.kt:106-112`
  (`anchorOf`)
- Problem: The fallback (targeted block, else the block in front of the player's
  feet) is only reachable through `place(player, ...)`, which MockBukkit cannot
  run — I confirmed `LivingEntityMock.getTargetBlockExact` and
  `EntityMock.getFacing` both throw `UnimplementedOperationException`. MT-013/015
  correctly gate the player entry point, but they only assert names, sizes,
  emptiness and the obstruction cases; none says where the row starts when no
  block is targeted, so a wrong fallback origin or axis could still pass.
- Suggested fix: extend MT-013 (or MT-015) to say: aim at open air (no targeted
  block) and confirm the row starts in the block directly in front of the
  player's feet at the anchor's Y, running across the view.

### Non-findings
- **Reader layer is the right layer and well covered.**
  `JsonImporterTest.kt` is Bukkit-free and injects the matcher, covering rows
  outside 1..6 (`:34-45`), a row above `rows` (`:47-68`), slot outside 1..9
  (`:70-87`), an unknown item (`:89-110`), blank (`:112-123`) and
  case/whitespace duplicate names (`:125-144`), and every message assertion
  checks both the file name and the entry label. Missing file (`:146-153`) and
  malformed JSON (`:155-163`) are separated. I deliberately did not ask for
  lower-bound cases (`rows=0`, `slot=0`): Kotlin range semantics make those
  near-free to reason about and the tests add no product confidence.
- **Placer layer is integration-tested through MockBukkit, not over-mocked.**
  `ScaffoldPlacerTest.kt` places into a real `WorldMock` and asserts the placed
  block type, `Chest.facing`, `Chest.type` and name (`:142-154`), covering the
  single row (`:34-41`), the `RIGHT`/`LEFT` pair (`:43-50`), the following chest
  after a double (`:52-60`), a non-NORTH view axis (`:62-75`), non-6 rows
  (`:77-83`), atomic obstruction with count + first (`:85-107`), the occupied
  predicate (`:109-124`) and replaceable overwrite (`:126-134`). The `null` name
  assertion on the `LEFT` half is an intentional pin of the MockBukkit
  limitation rather than a product claim; the real both-halves naming lives in
  `ChestNamerTest`/MT-013.
- **Command outcomes are complete.** All five `ScaffoldOutcome` branches are
  asserted (`UiDesignerCommandTest.kt:915-1002`), plus default file for
  `null`/blank (`:841-874`), relative resolution (`:876-895`), absolute
  pass-through (`:897-913`), an unresolvable path (`:989-1002`) and the
  importer→placer handoff (`:915-930`).
- **Suggestions are exact.** `scaffold suggests json files in the data folder`
  (`:1041-1061`) and `json files lists only json files sorted`
  (`UiDesignerConfigTest.kt:139-150`) both use equality assertions, so the
  `.txt` and `nested.json` directory exclusions are genuinely verified
  (`assertSuggestionEquality` uses `assertEquals`).
- **No excessive tests found.** The two obstruction tests are distinct (atomic
  no-placement + single first, then multi-count + first); the `Obstructed`
  outcome test and the dispatch tests pin different seams. I would not cut any.
- **The manual gate names ticket 160 and the right checks.** MT-013 (real double
  + single, both named and empty), MT-014 (save-after-scaffold round trip) and
  MT-015 (obstruction, missing/malformed file) are the non-automatable criteria,
  and the stated MockBukkit limits (no linked `DoubleChest`, throwing
  `getTargetBlockExact`/`getFacing`) are the real restrictions. Only the
  fallback *location* assertion is thin (finding 4).

## Round 2

### Verdict
Ship. All four round-1 findings are fixed with tests at the right layer, and the
tests added for the UX fixes pin observable contracts rather than internals. I
found no new test-quality or harness-fidelity issue.

### Findings
None.

### Non-findings
- **Concur with round-1 tester #1; it is fully addressed and then some.**
  `MessagesTest.kt:125-181` now covers the success body (both numbers and the
  double-chest caveat), both obstruction variants (count, position, block vs
  player recovery), the parse-failure path+entry, the IO-failure path+fallback
  (and the null-path hint), and the no-target anchor wording. Every scaffold
  message is registered in `allMessages()` (`:310-318`), so the shared
  prefix/palette/period invariants now apply. The two dispatch tests
  (`UiDesignerCommandTest.kt:1133-1171`) add the outcome→message wiring I asked
  for, and `scaffoldObstructedMessage(3, BlockPos(4,5,6), firstIsPlayer=false)`
  (`:138`) directly asserts the acceptance criterion "message names the first
  blocked position". The signature change to carry `firstIsPlayer` is likewise
  pinned at the body, outcome and placer layers (`MessagesTest.kt:148-156`,
  `UiDesignerCommandTest.kt:937-970`, `ScaffoldPlacerTest.kt:91-107,120-124`).
- **Concur with round-1 tester #2; the production matcher seam is now covered.**
  `MaterialResolverTest.kt:8-22` proves namespaced, legacy and unknown/blank ids
  resolve through `Material.matchMaterial`, and
  `scaffold imports an exported file through the production importer`
  (`UiDesignerCommandTest.kt:1061-1094`) omits the `importer` override, writes a
  real `JsonExporter.toJson` file, and asserts the injected placer receives the
  decoded names/rows — so the default
  `JsonImporter.read(it, MaterialResolver::isKnown)` and the exporter's
  `minecraft:*` id contract are exercised end to end. This is the right layer
  and does not need the un-runnable player placer.
- **Concur with round-1 tester #3; the console path is pinned.**
  `console scaffold does not import or place` (`UiDesignerCommandTest.kt:1172-1197`)
  drives both the no-arg and file forms through `server.consoleSender` and
  asserts zero importer/placer calls, matching `console save does not export`.
- **Concur with round-1 tester #4; the fallback is now asserted in the manual
  gate.** MT-013 (`docs/manual-test.md:24`) now instructs the tester to aim at
  open air so the fallback fires and to confirm "the row starts in the block
  directly in front of your feet at the anchor's Y and runs across your view",
  which is exactly the path `anchorOf` cannot be driven through under
  MockBukkit.
- **The new UX-fix tests are observably layered, not over-specified.**
  `an empty design fails naming the file` (`JsonImporterTest.kt:146-158`) asserts
  the exception's `file`/`detail`, `scaffold reports a missing file without
  duplicating the path` (`UiDesignerCommandTest.kt:1029-1040`) asserts
  `IoFailure(file, null)` rather than the exception's raw path text, and
  `scaffold translates a malformed JSON failure` (`:1042-1059`) asserts the fixed
  player-facing phrase — each checks the contract, not a message copy of the
  library exception. No new over-mocking, brittle assertions or redundant tests.
- **No new harness limit, so no new manual entry is owed.**
  `MaterialResolverTest` runs without MockBukkit because `Material.matchMaterial`
  is a static lookup and is green; the player-entry placer remains the only
  untestable path and is still gated by MT-013/MT-015. MT-015's claim that the
  missing/malformed paths are also unit-tested is now accurate.
- **One residual branch deliberately not requested.**
  `scaffoldParseFailureMessage(path, null)` (the `SCAFFOLD_PARSE_HINT` fallback,
  reachable from a generic exception with a null message) is not asserted; the
  same `reasonOrDefault` fallback is already covered for three other messages in
  `failure messages fall back to a hint when the reason is blank`
  (`MessagesTest.kt:142-150`) and for the IO path at `:165-173`, so the only
  untested piece is one literal hint string. I do not think that earns a test.
