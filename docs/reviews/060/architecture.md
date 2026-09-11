# Architecture review — 060 (`/uidesigner save` export command)

## Round 1

### Verdict

Ship with fixes. The integration point is in the right package and the seams are
the right ones: `UiDesignerCommand` composes `ChestCapture` → `DoubleChestGrouper`
→ naming → the injected exporter, `model`/`export` stay pure, FAWE stays behind
`SelectionSource`, and the command is dispatchable in tests without a live
server. The next features (more container types, import, alternate output
formats) do not force a rewrite. The fixes are small and mostly documentation:
`architecture.md` does not record the non-obvious lazy FAWE wiring, and the
command reaches into `Region.world` to unpack coordinates that `Region` could
encapsulate. The naming step living in the command is acceptable for one
consumer; I would not extract it yet.

### Issues

#### 1. `architecture.md` does not record the lazy FAWE wiring (severity: low) — fix now

- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/UiDesignerPlugin.kt:38-44`,
  `docs/architecture.md:33-40`, `docs/architecture.md:106-112`
- Problem: The production `SelectionSource` is not `FaweSelectionSource`; it is an
  anonymous `SelectionSource` that delegates to it, purely so that FAWE (which is
  `compileOnly` and absent from the test classpath) is not touched when
  `onEnable` runs under MockBukkit. That is a real, non-obvious architectural
  decision, but the new `UiDesignerCommand` bullet only says a `SelectionSource`
  is injected and the `SelectionSource` seam bullet still reads as if
  `FaweSelectionSource` is the implementation wired in. A future reader can
  "simplify" `faweSelectionSource()` back to `selectionSource =
  FaweSelectionSource` and break `UiDesignerPluginTest`, with the only warning
  being a code comment.
- Suggested fix: add one sentence to the `SelectionSource` seam bullet (or the
  `UiDesignerCommand` bullet) recording that the plugin wires a lazy delegating
  `SelectionSource` because FAWE is `compileOnly` and must not load during
  `onEnable` in tests. Keep the code comment; make the doc the authority.

#### 2. The command unpacks `Region.world` by hand; `Region` should expose the block (severity: low) — fix now

- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/UiDesignerCommand.kt:93-94`,
  `src/main/kotlin/dev/cypdashuhn/uidesigner/capture/Region.kt`,
  `src/main/kotlin/dev/cypdashuhn/uidesigner/capture/DoubleChestGrouper.kt:45,76`
- Problem: `nameAt` writes `region.world.getBlockAt(position.x, position.y,
  position.z)`. That is the third site (after `DoubleChestGrouper.match` and
  `isComplementaryHalf`) doing the same `BlockPos` → coordinate-unpack →
  `World.getBlockAt` dance, and it makes `commands/` depend on `Region`'s
  internal `world` field. It is exactly the kind of rule that drifts if the
  lookup ever needs to change (e.g. a bounds/chunk check), and by the project's
  own "extract at the third consumer" policy this is the trigger.
- Suggested fix: add `fun blockAt(position: BlockPos): Block =
  world.getBlockAt(position.x, position.y, position.z)` to `Region` and use it in
  `nameAt` and the grouper's two sites. The command then reads
  `ChestNamer.nameOf(region.blockAt(it))` and no longer touches `region.world`.
  Purely mechanical; if the implementor considers it out of scope, carry it over
  to 070 rather than losing it.

#### 3. Naming step is in the command, not a `naming` component (severity: low) — carry over

- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/UiDesignerCommand.kt:75-78,93-94`,
  `docs/architecture.md:96-102`
- Problem: The command owns the "grouped chests → named chests" map
  (`chest.copy(name = …)`) and the per-position block lookup. `ChestNamer` is the
  naming authority but only exposes per-`Block` reads; the batch enrichment lives
  in the command. This is the one place where the ticket's "keep orchestration
  thin and delegate logic to the packages" is only mostly honoured, and the doc
  now enshrines the command as a pipeline stage (`architecture.md:96-102`,
  `:131-134`).
- Suggested fix: Acceptable as-is for a two-line map with one consumer. When a
  second consumer of batch naming appears (import/apply, a preview command, or
  barrel/shulker support), add a `ChestNamer` (or new `naming/`) function such as
  `nameOf(region, chests): List<UiChest>` so the command is pure orchestration.
  Note the cost of doing it now: it introduces a `naming → capture` package edge
  (`ChestNamer` would take `Region`), so it is not worth it for one caller. Carry
  over with the "second consumer" trigger.

#### 4. `SaveOutcome` + messages will collide with 070's message centralisation (severity: low) — carry over

- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/UiDesignerCommand.kt:26-39,96-120`,
  `docs/tasks/070-ux-polish.md:13-19,31-32`
- Problem: 070 explicitly moves messages into `util/Messages.kt`, but the
  outcome-to-component mapping and the `SaveOutcome` sealed type are nested
  private-ish members of the command. `Messages` will either import a
  command-owned type (`util → commands`, an awkward direction) or take
  primitives, which will force the mapping apart again.
- Suggested fix: Not a 060 defect, but plan the seam now: when 070 lands, either
  move `SaveOutcome` to a neutral type in `commands/` (e.g. its own file) that
  `Messages` can consume, or have `Messages` accept primitives and keep the
  `when` in the command. No change needed in this ticket.

#### 5. The command will grow into a multi-subcommand god-class when import lands (severity: low) — carry over

- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/UiDesignerCommand.kt:41-86`
- Problem: `register()` already wires `save`/`reload`/`help` and `save()` carries
  the whole pipeline. Import/apply (backlog, `docs/design.md:32`) would add a
  fourth subcommand plus a loader seam into the same class, mixing two pipelines
  and two outcome types.
- Suggested fix: Fine for the current three subcommands. When import lands,
  split per-subcommand handlers (as `ChestEditCommand` is its own class) and keep
  `UiDesignerCommand` as the root registrar. No change now.

#### 6. Only exporter failures are caught (severity: low) — carry over

- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/UiDesignerCommand.kt:70-86`
- Problem: The `try/catch` wraps only `exporter`, so a failure in
  `ChestCapture`/`DoubleChestGrouper` (e.g. the grouper's `rowCount` `require`)
  propagates as an uncaught exception on the main thread. Today the scanner
  guarantees 27/54 items so the `require` is unreachable, so this is latent, not
  a live bug.
- Suggested fix: Leave it; the invariant failure is arguably correct to surface
  loudly, and 070 owns the "no stack traces" pass. Flagging so it is a decision,
  not an oversight.

### Non-issues

- **Package placement is exactly right.** `UiDesignerCommand.kt` lands in
  `commands/` beside `ChestEditCommand`, the plugin is the composition root, and
  no new package was invented. Matches `docs/architecture.md:25-27`.
- **The injected seams all earn their keep.** `selectionSource` is required for
  testability; `configProvider: () -> UiDesignerConfig` (rather than a config
  instance) is what makes `reload()` observe a re-read config
  (`UiDesignerCommand.kt:88-91`); `reloadAction` keeps the command generic over
  the plugin; `exporter: (List<UiChest>, Path) -> Unit` is the side-effect
  boundary tests replace. Four parameters is not over-engineering for a class
  that has four distinct collaborators, and each is exercised in
  `UiDesignerCommandTest`.
- **A function seam is the right call for alternate output formats.** A
  `(List<UiChest>, Path) -> Unit` parameter is lighter than an `Exporter`
  interface and lets a future `MarkdownExporter`/`NbtExporter` be passed without
  touching the command. The `SaveOutcome.Exported(chests, outputFile)` payload is
  format-neutral. No premature format abstraction was added.
- **Naming after grouping is correct for double chests.** The map runs over
  `grouped`, so a merged double is named once at its canonical (lower) position
  via `ChestNamer.nameOf`, which already reads both halves' names
  (`ChestNamer.kt:29,45-50`). Naming before grouping would have produced two
  names for one design. `architecture.md:96-102` describes this accurately.
- **`ChestCapture.capture` reuse closes the 030 round-2 seam.** The command calls
  `ChestCapture.capture(selectionSource, player)` and gets back the
  `CapturedSelection(region, contents)` that the grouper needs, so there is no
  second selection read and no bypass of the production entry point
  (`UiDesignerCommand.kt:71-74`). The `null` / empty-contents distinction maps
  cleanly to `NoSelection` / `NoChests`.
- **FAWE does not leak past the seam.** `model` and `export` remain
  Bukkit/FAWE-free; `FaweSelectionSource` is referenced only from the plugin's
  private factory; the command sees only `SelectionSource`. The lazy anonymous
  wrapper is an acceptable pragmatic technique for the `compileOnly` test
  classpath, not a seam violation — I only ask that it be documented (issue 1).
  A no-op/test `SelectionSource` would not help production; a `capture`-level
  `SelectionSources.fawe()` factory is the natural next step if a second
  FAWE-backed source ever appears, and can wait.
- **`UiDesignerCommand` is a legitimately thin orchestrator, not a logic dump.**
  It owns sequencing, the empty check, outcome classification, and messages —
  all integration concerns — and delegates capture/grouping/naming/export. The
  `save()` method returning `SaveOutcome` (instead of sending messages directly)
  is what makes the pipeline unit-testable without CommandAPI dispatch, matching
  the `ChestEditCommand` pattern.
- **Config wiring is correct and reflects reload.** `configProvider = { uiConfig }`
  and `reloadAction = { reloadConfiguration() }` (`UiDesignerPlugin.kt:32-33`)
  keep the command pointed at the live config; `reloadConfiguration()` assigns a
  fresh `UiDesignerConfig`, so `reload()` returns the new path.
- **Synchronous file IO on the main thread is documented, not hidden.**
  `architecture.md:111-112` and the ticket both call it an MVP trade-off. Nothing
  to change until large selections land.
- **No over-generalisation for the backlog.** No container/reader/importer/format
  abstractions were added; the command takes concrete collaborators. The design
  extends to more container types (scanner/grouper/namer changes only), import (a
  new subcommand plus a loader seam), and alternate formats (swap the exporter
  function) without a rewrite.
- **Permission default is correct.** `.withPermission("uidesigner.save")` on the
  `save` subcommand defaults to op, satisfying the ticket; `reload` getting its
  own `uidesigner.reload` node is a sensible extra, not scope creep.

## Round 2

### Verdict

Ship with fixes, and the fixes are documentation-only. Both round-1 fix-now
issues genuinely landed: the lazy FAWE wiring is recorded and accurate, and
`Region.blockAt(BlockPos)` now backs the command and both grouper lookup sites.
The `depend = listOf("FastAsyncWorldEdit")` and the per-sender executors are the
right architectural calls. The only problem is that the `Region.blockAt` refactor
left two stale `region.world.getBlockAt(...)` snippets in `architecture.md`, and
the new hard plugin dependency is not recorded anywhere. Neither affects code,
extensibility, or boundaries; both are one-line doc corrections.

### Issues

#### 1. `architecture.md` still shows the pre-`blockAt` call in two places (severity: low) — fix now

- Location: `docs/architecture.md:73` and `docs/architecture.md:137`
- Problem: The round-1 fix added `Region.blockAt` and correctly updated the seam
  prose (`architecture.md:47-49`), but two older references still read
  `region.world.getBlockAt(position.x, position.y, position.z)`:
  - the grouper input-contract bullet at `:70-74` ("so it can reach each block
    via `region.world.getBlockAt(...)`"), and
  - the data-flow diagram at `:136-137`
    (`-> region.world.getBlockAt(position.x, position.y, position.z)`).
  The code now calls `region.blockAt(position)` at `DoubleChestGrouper.kt:45,76`,
  so the doc is internally inconsistent (it declares the helper three lines
  earlier and then shows the raw call), and it re-teaches the exact
  hand-unpacking pattern issue 2 asked to remove. A reader trusting the diagram
  would re-introduce `region.world` in `commands/`/`capture/`.
- Suggested fix: replace both snippets with `region.blockAt(position)` (or with
  prose: "via `Region.blockAt`"). No code change.

#### 2. The new hard FAWE dependency is not recorded (severity: low) — fix now

- Location: `build.gradle.kts:69`; `docs/design.md:52-56`;
  `docs/architecture.md:40-43`
- Problem: `depend = listOf("FastAsyncWorldEdit")` makes FAWE a hard `plugin.yml`
  dependency — the server refuses to enable UiDesigner without it. That is the
  correct production counterpart to the test-only lazy delegating
  `SelectionSource` (`UiDesignerPlugin.kt:38-44`), but neither the FAWE decision
  bullet (`design.md:52-56`, which describes only the `compileOnly`/run-paper
  build side) nor the `SelectionSource` seam bullet (`architecture.md:40-43`,
  which explains only the MockBukkit class-loading reason) mentions it. As
  written, the docs explain why the wrapper exists but not why it is not simply
  the whole FAWE story, and a reader cannot tell from the docs that FAWE is
  required at runtime.
- Suggested fix: extend the `SelectionSource` seam sentence (or the
  `design.md` FAWE bullet) with one clause: the plugin declares FAWE as a hard
  `depend` in `plugin.yml`, so a real server refuses to enable without it while
  the lazy wrapper keeps `FaweSelectionSource` from class-loading under
  MockBukkit. No code change.

### Non-issues

- **Round-1 fix #1 landed and is accurate.** `architecture.md:40-43` records that
  the plugin wires a lazy delegating `SelectionSource` because FAWE is
  `compileOnly` and absent from MockBukkit's classpath, and that the delegation
  keeps `FaweSelectionSource` from class-loading during `onEnable`. That matches
  `UiDesignerPlugin.kt:38-44` exactly; the only missing half is the `depend`
  (issue 2).
- **Round-1 fix #2 landed as scoped.** `Region.blockAt(BlockPos)` exists
  (`Region.kt:12`) and is used by the command (`UiDesignerCommand.kt:100-101`,
  via the new `nameAt` helper) and both grouper sites
  (`DoubleChestGrouper.kt:45,76`); `commands/` no longer touches `Region.world`.
  The one remaining inline lookup is `ChestScanner.kt:16`, and that is fine: the
  scanner iterates raw `x`/`y`/`z` ints rather than a `BlockPos`, and it needs
  `region.world.isChunkLoaded` (`:15`) anyway, so routing through
  `blockAt(BlockPos)` would allocate a value per scanned block for no gain.
  `architecture.md:47-49` scopes the helper to "the grouper and the command",
  which matches the code. Carry over only if a bounds/chunk guard is ever added
  to `Region.blockAt`: the scanner would then need the same guard.
- **`depend` is the right architectural fit, not a new boundary.** FAWE is
  already the plugin's core capability (`docs/design.md:25`), so a hard runtime
  dependency is honest, and it closes the correctness #2 gap where `save` would
  otherwise throw `NoClassDefFoundError` on a FAWE-less server. `FastAsyncWorldEdit`
  is the correct Bukkit plugin name and `run-paper` loads the matching jar
  (`build.gradle.kts:101-108`), so the dev server is unaffected. It does not
  change the code seam: `model`/`export` stay pure and FAWE stays behind
  `SelectionSource`.
- **Console-capable executors are correct and documented.** `reload` and `help`
  (and the bare root) use `CommandExecutor` and accept any `CommandSender`;
  `save` stays on `executesPlayer` because it needs a player's FAWE selection
  and block context (`UiDesignerCommand.kt:50-63`). That matches
  `architecture.md:117-120` and is pinned by `console can reload` /
  `console can print help`. No player-state code runs on the console paths.
- **The round-1 fixes introduced no duplication or boundary violation.**
  `Region.blockAt` is a single shared helper, not a second implementation of the
  lookup; the grouper/command now depend on the method rather than the `world`
  field. The `depend` and executor changes are config/registration only. The
  `nameAt` extraction (`UiDesignerCommand.kt:100-101`) is a net readability win
  and does not move the batch-naming responsibility out of the command.
- **Carry-overs are unchanged and none was made worse.** #3 (naming step in the
  command) is slightly better contained by `nameAt` but still command-owned; #4
  (`SaveOutcome`/`Messages` seam) still needs planning for 070 but `SaveOutcome`
  is a non-private nested type 070 can consume; #5 (per-subcommand split) is
  untouched; #6 (only exporter failures caught) actually improved, since
  correctness #1 moved `configProvider().outputFile` inside a `try`
  (`UiDesignerCommand.kt:78-89`), so config-resolution failures now classify as
  `WriteFailed` while capture/grouping remain outside it as before.
- **Deferred correctness #3 is documented.** The geometry-merged-double name loss
  is now recorded at `docs/design.md:79-82` next to the one-half-selected note,
  with "carrying both merged positions out of the grouper is deferred", so the
  limitation is a decision rather than a hidden bug. ux #2 remains 070's.
- **The next-feature walkthrough still holds.** More container types change only
  the scanner/grouper/namer and reuse `Region.blockAt`; import adds a subcommand
  plus a loader seam (the #5 split); alternate formats swap the
  `(List<UiChest>, Path) -> Unit` exporter. Nothing in the round-1 fixes
  generalises prematurely or blocks those paths.
