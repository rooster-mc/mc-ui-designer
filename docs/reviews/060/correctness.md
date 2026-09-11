# Correctness review — 060 (`/uidesigner save` export command)

## Round 1

### Verdict
Ship with fixes. The happy path is wired correctly: capture → group → name →
export, names populated *after* grouping at each `UiChest`'s canonical position,
and the existing `JsonExporter` left as the single ordering/formatting authority,
so the emitted JSON matches `docs/data-format.md`. Main-thread access, the
no-selection / no-chests / IO-failure branches, and the op-default permissions
are all right. The findings are edge and robustness issues, not happy-path
breakage: config-path resolution can throw outside the write `try`, the FAWE
lazy wrapper only *defers* a missing-FAWE failure, and a name can be lost for a
geometry-merged double whose name sits on the non-canonical half.

### Issues

#### 1. `configProvider().outputFile` is resolved outside the `try`/`catch` (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/UiDesignerCommand.kt:79-85`
- Problem: `val outputFile = configProvider().outputFile` runs before `try`, so
  failures from `UiDesignerConfig.outputFile` are not classified as
  `WriteFailed`. Two real paths: `Path.of(raw)` throws `InvalidPathException`
  for a configured value the platform cannot parse (e.g. a NUL, expressible as
  `output-file: "\0"` in YAML), and `defaultOutputFile()` throws
  `IllegalStateException` if the packaged `config.yml` default is missing. Both
  propagate out of `save()` and the CommandAPI executor as an uncaught
  exception, so the player gets no "Could not write" line even though the ticket
  asks for errors on IO failure. (Related to `architecture.md` issue 6, but
  reachable through admin config rather than only through an invariant break.)
- Repro: set `output-file: "\0"` in `plugins/UiDesigner/config.yml`, run
  `/uidesigner save` on a loaded selection → `InvalidPathException` escapes
  `save`; no `SaveOutcome` and no feedback.
- Suggested fix: move the resolution inside the `try` (or `runCatching` it) so
  a bad path becomes `WriteFailed`, e.g.
  `return try { val outputFile = configProvider().outputFile; exporter(named, outputFile); ... }`.

#### 2. Missing-FAWE failure is deferred, not handled, and is an `Error` (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/UiDesignerPlugin.kt:40-44`;
  `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/UiDesignerCommand.kt:71-72,83`
- Problem: the anonymous `SelectionSource` wrapper does correctly keep
  `FaweSelectionSource` from being class-loaded during `onEnable`/MockBukkit
  (the reference is inside the method body, so resolution is lazy). But it only
  defers the dependency: invoking `save` loads `FaweSelectionSource` and, if
  WorldEdit is absent, throws `NoClassDefFoundError`. That is an `Error`, not an
  `Exception`, so the `catch (e: Exception)` at `:83` would not catch it even if
  the capture were inside the `try` — and the `try` currently only wraps
  `exporter`, so the whole capture step is outside it. The generated `plugin.yml`
  declares no `depend` on FastAsyncWorldEdit. The workaround therefore buys a
  clean `onEnable`/test run at the cost of a raw linkage error on the first save.
- Repro: load the plugin on a server/classpath without FastAsyncWorldEdit, run
  `/uidesigner save` → `NoClassDefFoundError` from the command executor.
- Suggested fix: declare FAWE as a hard dependency in the `bukkit { }` block
  (`depend = listOf("FastAsyncWorldEdit")`) so the server refuses to enable
  without it, or wrap the capture step and catch `LinkageError` to report a
  friendly message.

#### 3. Name is lost for a geometry-merged double when only the non-canonical half is named (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/UiDesignerCommand.kt:77,93-94`;
  `src/main/kotlin/dev/cypdashuhn/uidesigner/naming/ChestNamer.kt:40-50`
- Problem: the grouper's geometry fallback merges two complementary chest blocks
  that are *not* a linked `DoubleChest` into one 6-row design. `save` then reads
  the name only at the canonical (min) position. For a linked double,
  `ChestNamer.nameOf` resolves the holder and reads both halves, so the canonical
  choice is safe; for the fallback pair the holder is not a `DoubleChest`, so
  `chestsOf` returns only the canonical half. A name on the other half (which
  `/chest-edit` also wrote only to that half, since it too found no holder) is
  silently dropped even though the design is exported as a double.
- Repro: create two adjacent chest blocks with complementary `Chest.Type`
  LEFT/RIGHT and matching facing but no `DoubleChest` holder (the fallback
  scenario), `/chest-edit` the non-canonical half, `/uidesigner save` → the
  exported `name` is omitted.
- Suggested fix: carry both merged positions out of the grouper (or read
  `ChestNamer.nameOf` at both halves and take the first non-blank) instead of
  only the canonical position. If deliberately deferred, record the limitation
  next to the "one half selected" note in `docs/design.md`.

#### 4. `reload` and `help` are player-only, so console cannot reload config (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/UiDesignerCommand.kt:52-64`
- Problem: `reload` and `help` (and the bare root) use `executesPlayer`, so a
  console sender is rejected. The ticket's AC is simply "`/uidesigner reload`
  re-reads config", and reloading an admin-edited config from the console is the
  natural usage; `reload` touches no player state. Only `save` genuinely needs a
  `Player` (FAWE selection and block context).
- Repro: run `/uidesigner reload` from the server console → player-only
  rejection, config not re-read.
- Suggested fix: register `reload`/`help` with `.executes(...)` using a
  `CommandExecutor` that sends to the `CommandSender`, keeping `save` on
  `executesPlayer`.

### Non-issues

- **Pipeline order is correct.** `DoubleChestGrouper` returns `UiChest(name =
  null, position = canonical)`, `save` enriches names over the *grouped* list
  (`UiDesignerCommand.kt:75-78`), and `JsonExporter.normalized` sorts, drops
  blank names and empty rows, and filters last. Naming before grouping would
  have produced two names for one double; naming at the canonical position is
  safe for a linked double because `ChestNamer.nameOf` reads both halves through
  the `DoubleChest` holder.
- **Output matches `docs/data-format.md`.** `DesignJson` has
  `encodeDefaults = false`, so null chest/slot names and the `@Transient`
  `position` are omitted; `UiSlot.item` is `stack.type.key` (`minecraft:stone`);
  rows/slots are sorted by index and empty rows filtered. No casing, ordering, or
  omitted-field mismatch is introduced by 060.
- **A failed write leaves the previous file intact.** `JsonExporter.export`
  writes to a temp in the target directory and moves it with
  `ATOMIC_MOVE`/`REPLACE_EXISTING`; its `catch` deletes the temp and rethrows, and
  `save` maps that to `WriteFailed` (`UiDesignerCommand.kt:80-85`). The only
  partial-write window is the non-atomic `move` fallback, which is a
  same-directory rename in practice and is pre-existing 020 behaviour, not a 060
  regression. (The missing test for this property is `tester.md` issue 1.)
- **Threading is right.** `save`/`reload` run on the CommandAPI player executor,
  i.e. the server main thread, so every `World.getBlockAt`, `Chest` state read,
  and `Inventory` read happens on the main thread. File IO is synchronous but
  tiny and is documented as an MVP trade-off (`docs/architecture.md:111-112`).
- **Permissions and default op are correct.** `uidesigner.save` /
  `uidesigner.reload` are applied via `withPermission`; an unregistered Bukkit
  permission resolves to `PermissionDefault.OP`, so ops pass and non-ops do not,
  and explicit attachments work. `UiDesignerCommandTest` pins all three cases.
- **`UiChest.position` nullability is handled.** The grouper always sets it;
  `save` tolerates a hypothetical null by leaving the name null, and the exporter
  `requireNotNull`s it rather than silently falling back to input order. No path
  in 060 can produce a null position.
- **No-chests / no-selection / air / blank-name handling is correct.**
  `ChestCapture.capture` returning null → `NoSelection`, empty contents →
  `NoChests`; the grouper drops empty stacks and the exporter drops blank names,
  so the unnamed form in `docs/data-format.md` is reached.
- **Reload semantics work.** `reload()` runs the injected `reloadConfiguration`
  (which re-reads `config.yml` and rebuilds `uiConfig`) and then reads the new
  path from the provider; the test mutates the provider's value so the returned
  path can only be post-reload. The "success even when the file could not be
  parsed" wording is a UX matter, tracked as `ux.md` issue 2.
- **Success count is grouped designs, matching the design's double-as-one
  entity.** `Exported(named.size)` is correct for that model; the only concern is
  the ticket's literal "number of chests exported" wording, which `ux.md` issue 3
  already covers, so I am not re-raising it as a defect.

## Round 2

### Verdict

Ship with one low-severity fix. Round-1 #1, #2, and #4 all landed and are
verifiable in the tree, and the `Region.blockAt`/`DoubleChestGrouper` refactor is
behaviour-preserving. The remaining defect is the reload-side twin of round-1
#1: `reload()` resolves `configProvider().outputFile` (and runs `reloadAction()`)
with no guard, so a config whose output path cannot be parsed throws out of the
command executor instead of producing feedback. Deferred #3 (name on the
non-canonical geometry-merged half) and ux #2 (reload false-success) stand as
recorded; the manual FAWE end-to-end remains unverified.

### Issues

#### 1. `/uidesigner reload` resolves the config path outside any guard (severity: low)

- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/UiDesignerCommand.kt:44-45,92-95`;
  `src/main/kotlin/dev/cypdashuhn/uidesigner/config/UiDesignerConfig.kt:10-11,26-33`
- Problem: `reload()` calls `reloadAction()` and then
  `configProvider().outputFile` with no `try`. Round-1 #1 fixed exactly this
  shape for `save` (`UiDesignerCommand.kt:78-83`), but the reload path was left
  unguarded, and round-1 #4 made it reachable from the console (`reloadExecutor`
  uses `.executes`). `outputFile` calls `Path.of(raw)` and `defaultOutputFile()`
  (`error(...)`), so `output-file: "\0"` (a parseable YAML double-quoted NUL, or a
  Windows-invalid path such as `C:/a<b>.json`) throws `InvalidPathException`, and
  a missing packaged default throws `IllegalStateException`. Either escapes the
  `CommandExecutor`, so the sender gets no `Could not ...` line and the server
  logs a trace; `reloadAction()` can throw the same way from
  `writeDefaultOutputIfBlank()` before `reload()` ever returns. This is the one
  path where "errors for IO failure" in the ticket is still not honoured.
- Repro: set `output-file: "\0"` in `plugins/UiDesigner/config.yml`, run
  `/uidesigner reload` (player or console) → `InvalidPathException` escapes the
  executor, no feedback. Running `/uidesigner save` on the same config now prints
  `Could not write the export: ...`, confirming the asymmetry.
- Suggested fix: mirror #1's guard. Minimal: wrap the executor body so the whole
  `reload()` is protected and a failure is reported, e.g.
  `CommandExecutor { sender, _ -> sender.sendMessage(runCatching { reload() }.fold(::reloadMessage) { Component.text("Could not reload config.yml: ${it.message ?: it.javaClass.simpleName}") }) }`,
  or give `reload()` a small sealed result like `SaveOutcome`. If deliberately
  deferred, record it beside ux #2 for 070's error pass rather than leaving it
  silent.

### Non-issues

- **Round-1 #1 resolved.** `configProvider().outputFile` is now inside its own
  `try`/`catch (e: Exception)` and maps to `failure(e)` →
  `WriteFailed(null, reason)` (`UiDesignerCommand.kt:78-83`); the dedicated test
  `save reports an unusable config path without throwing`
  (`UiDesignerCommandTest.kt:206-223`) pins it. `Path.of`/`error()` both throw
  `Exception` subtypes, so they are caught.
- **Round-1 #2 resolved and internally consistent.** The generated and packaged
  `plugin.yml` both declare `depend: [FastAsyncWorldEdit]`
  (`build/generated/plugin-yml/Bukkit/plugin.yml:4-5`,
  `build/resources/main/plugin.yml:4-5`), so a FAWE-less server refuses to enable
  UiDesigner and the `NoClassDefFoundError` repro is unreachable. The lazy
  anonymous `SelectionSource` is still correct and needed for MockBukkit: the
  `FaweSelectionSource` reference remains inside the method body
  (`UiDesignerPlugin.kt:38-44`), so it is not class-loaded during `onEnable` on
  the FAWE-free test classpath. The two changes are complementary, not
  redundant.
- **Round-1 #4 resolved.** `reload` and `help` (and the bare root) use
  `CommandExecutor` and `.executes(...)` (`UiDesignerCommand.kt:44-46,57-63`),
  so console reaches them; only `save` stays `executesPlayer`. Pinned by
  `console can reload` / `console can print help` (`UiDesignerCommandTest.kt:312-321,348-354`).
- **The `Region.blockAt` refactor is behaviour-preserving.** `blockAt` is the
  literal former call `world.getBlockAt(position.x, position.y, position.z)`
  (`Region.kt:12`); both grouper sites (`DoubleChestGrouper.kt:45,76`) and
  `nameAt` (`UiDesignerCommand.kt:100-101`) delegate to it. No coordinate or
  `BlockPos` semantics changed, and the refactor touches no chunk/threading
  behaviour.
- **Main-thread access is unchanged.** `save` still runs on the player executor
  and `reload`/`help` on the command executor, all on the server main thread;
  every `Block`/`Chest`/`Inventory` read and the small synchronous write stay
  there.
- **Name lookup at the canonical position is unchanged, and deferred #3 stands.**
  `save` enriches names over the grouped list at `chest.position`
  (`UiDesignerCommand.kt:72-77`); the linked-double path reads both halves
  through `ChestNamer.chestsOf`, the unlinked geometry fallback reads only the
  canonical half. That limitation is recorded at `docs/design.md:79-82` and is
  explicitly deferred, so it is a decision rather than a hidden regression.
- **Output still matches `docs/data-format.md`.** `JsonExporter` is untouched by
  round 2; `DesignJson.encodeDefaults = false` omits null names and the
  `@Transient` `position`, `normalized` sorts by canonical position and rows/slots
  and filters empty rows, and `UiSlot.item` is `stack.type.key`
  (`minecraft:stone`). Casing, ordering, and omission rules are unaffected.
- **`WriteFailed(outputFile: Path?, reason: String)` nullability is sound.**
  `null` means the path itself could not be resolved (no target is known), a
  non-null `Path` means the write failed at that target; `saveMessage` renders
  both (`UiDesignerCommand.kt:119-122`) and the tests assert each shape
  (`UiDesignerCommandTest.kt:200-222`). `docs/data-format.md` does not govern
  command outcomes, and the ticket only asks for an IO-failure error, which is
  satisfied.
- **No-chests / air / blank-name handling is unchanged.** Empty scanner results
  still map to `NoChests`, empty stacks are dropped by the grouper, and blank
  chest/slot names are dropped by the exporter, so the unnamed form in
  `docs/data-format.md` is still reached.
- **The stale `architecture.md` `region.world.getBlockAt(...)` snippets are
  already `architecture` round-2 issue 1**, and the undocumented `depend` is its
  issue 2; I am not duplicating them here.
- **Manual FAWE end-to-end remains unverified**, as stated in the ticket and the
  other round-2 reports; nothing in round 2 changes that, and it must be recorded
  before `done`.
