# Tester review — 050 (/chest-edit naming)

## Round 1
### Verdict
Ship with fixes. The core logic is covered at the right layers (ChestNamer via
MockBukkit, command decision logic via `apply`, one real dispatch integration
test), but the permission gate is untested and the trapped-chest path is only
checked for `isChest`, not for name round-trip. Both are explicit ticket scope.

### Issues
#### 1. Permission gate is never exercised (severity: medium)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/ChestEditCommand.kt:30`; `src/test/kotlin/dev/cypdashuhn/uidesigner/commands/ChestEditCommandTest.kt:73`
- Problem: The only dispatch test sets `player.isOp = true`, which satisfies any
  permission node. The ticket scope explicitly includes "no permission"
  feedback, and the whole point of `.withPermission("uidesigner.chest-edit")` is
  unverified: deleting the line (or typo'ing the node) keeps the suite green.
- Suggested fix: Add a dispatch test with a non-op, non-permitted player and
  assert the command does not succeed and the chest name is unchanged. Ideally
  also one with `player.addAttachment(plugin, "uidesigner.chest-edit", true)` to
  pin the exact node string rather than relying on op.

#### 2. Trapped chests are never named or read back (severity: medium)
- Location: `src/test/kotlin/dev/cypdashuhn/uidesigner/naming/ChestNamerTest.kt:56-59`
- Problem: `chest blocks are chests` only asserts `isChest(TRAPPED_CHEST)`.
  `nameOf`/`setName`/`clear` cast `block.state as? Chest`; if that cast silently
  failed for a trapped chest, `ChestEditCommand.apply` would still return
  `Named("Shop")` and report success while storing nothing. The trapped chest is
  called out explicitly in the ticket and is a real failure mode, not incidental
  structure.
- Suggested fix: Add a `setName`/`nameOf` round-trip on a `TRAPPED_CHEST` block
  (and, ideally, a clear on it) in `ChestNamerTest`, or a trapped-chest variant
  of the command `apply names a chest` test.

#### 3. The reserved `"clear"` sentinel is not exercised through dispatch (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/ChestEditCommand.kt:49-53`; `src/test/kotlin/dev/cypdashuhn/uidesigner/commands/ChestEditCommandTest.kt:68-78`
- Problem: The code carries a comment that `clear` cannot be a subcommand and is
  therefore reserved as a name sentinel, but the only dispatch test runs
  `chest-edit Shop`. If the argument wiring changed such that `clear` were
  parsed elsewhere, no test would catch it.
- Suggested fix: Add a dispatch test that pre-names a chest, runs
  `chest-edit clear`, and asserts the name is gone. This is cheap and guards the
  documented risk.

#### 4. Persistence across restart is asserted by the ticket but untested (severity: low, reasonable to defer)
- Location: `docs/tasks/050-chest-naming.md:27`; `src/test/kotlin/dev/cypdashuhn/uidesigner/naming/ChestNamerTest.kt:37-43`
- Problem: The round-trip test proves `setName` → `nameOf` through the public
  API, which is a reasonable proxy, but it does not prove the name reaches
  block-entity NBT (it may pass on an in-memory state cache). MockBukkit cannot
  perform a real restart, so a true persistence test is not feasible here.
- Suggested fix: Keep the automated round-trip, but record in the ticket/report
  that restart persistence is deferred to a manual dev-server check (place,
  name, `/stop`, start, read back), and note it must be done before 050 is
  marked done. Do not add a fake serialization test that only re-tests MockBukkit.

#### 5. The rename→export acceptance criterion has no integration test (severity: low, defer to 060)
- Location: `docs/tasks/050-chest-naming.md:25`; `docs/tasks/060-export-command.md:5`
- Problem: "Renaming then exporting a selection produces the name in the JSON
  `name`" cannot be tested yet: `model`/`export` (020) and the save pipeline
  (060) do not exist on this branch. That is legitimate, but the criterion is
  currently only half-covered (`nameOf` returns the name).
- Suggested fix: Defer the JSON assertion to 060, which `depends-on: [050]`, and
  ensure that ticket's fixture test includes a chest named via `ChestNamer` so
  the seam is actually connected. Nothing to add in 050.

### Non-issues
- Layer choice is right: `ChestNamer` is a Bukkit/block-state concern tested
  with MockBukkit, while `ChestEditCommand.apply` is a pure decision function
  (`Block?`, `String`) → `Outcome` and needs no Bukkit command harness. The
  injected `targetResolver` seam keeps `getTargetBlockExact` out of unit tests,
  which is the right call.
- Happy-path round-trip, not-a-chest rejection, null target, and clear are all
  covered at both the `ChestNamer` and `apply` levels
  (`ChestNamerTest.kt:31-67`, `ChestEditCommandTest.kt:32-65`).
- Asserting on the `Outcome` sealed values rather than message text is correct;
  exact feedback strings would be brittle and belong to the `ux` review.
- No snapshot or incidental-structure assertions; the single CommandAPI
  dispatch test earns its heavier test-toolkit dependency because it validates
  registration, argument parsing, and permission wiring end to end.
- `stone is not a chest`'s extra `assertNull(nameOf(stone))` is mildly redundant
  with `unnamed chest has no name`, but it is cheap and documents the cast
  fallback — not worth removing.

## Round 2
### Verdict
Ship. Every round-1 tester finding is addressed: the permission gate is now
exercised both denied (non-op) and granted (explicit attachment pinning the
node), trapped chests round-trip and clear, and the reserved `clear` sentinel
runs through real dispatch. The double-chest proxy genuinely exercises the
`DoubleChest` resolution path rather than passing vacuously. Only residual,
non-blocking gaps remain (plugin→command wiring, and an unrecorded manual
restart check).

### Issues
#### 1. Plugin→command registration is not covered (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/UiDesignerPlugin.kt:16`;
  `src/test/kotlin/dev/cypdashuhn/uidesigner/UiDesignerPluginTest.kt:10-21`
- Problem: `ChestEditCommandTest` registers the command itself with
  `MockCommandAPIPlugin`, and `UiDesignerPluginTest` only asserts the plugin
  enables. If `ChestEditCommand(this).register()` were removed from `onEnable`
  (or renamed), the feature would be unreachable yet the suite stays green. The
  dispatch tests validate `ChestEditCommand.register()` in isolation, not that
  the plugin wires it.
- Suggested fix: if the test toolkit can drive the real plugin, add one test
  that `MockBukkit.load(UiDesignerPlugin::class.java)` then dispatches
  `chest-edit Shop`; otherwise extract registration behind a small seam the
  plugin test can assert on, or explicitly note that this one-line wiring is
  covered only by review. Not a blocker.

#### 2. Restart-persistence deferral is still unrecorded (severity: low)
- Location: `docs/tasks/050-chest-naming.md:27`
- Problem: Round 1 asked for the manual restart check (place, name, `/stop`,
  restart, read back) to be recorded before 050 is marked done, and for no fake
  serialization test. The round-trip is covered, but nothing in the ticket or
  docs records the manual check, so the acceptance criterion can be closed by
  accident.
- Suggested fix: add a note to the ticket (or a follow-up task) that restart
  persistence is verified manually on the dev server before `done`. Do not add
  a MockBukkit test that only re-tests its in-memory state cache.

### Non-issues
- **Permission gate (round-1 #1).** `command dispatch is denied without
  permission` asserts a non-op is rejected (`CommandSyntaxException`) and the
  chest stays unnamed; `command dispatch succeeds with the permission node on a
  non-op` grants `uidesigner.chest-edit` via `addAttachment` and asserts success.
  The pair pins the node string — a renamed node breaks the granted test.
  Asserting the exception type rather than the message is the right level of
  brittleness.
- **Trapped chests (round-1 #2).** `trapped chest round-trips a name` names,
  reads back, clears and re-reads a `TRAPPED_CHEST`, so the `state as? Chest`
  cast is exercised for the trapped variant (MockBukkit's `ChestStateMock`
  accepts both `CHEST` and `TRAPPED_CHEST`).
- **Reserved `clear` (round-1 #3).** `command dispatch clears via the reserved
  sentinel` pre-names the chest, runs `chest-edit clear` through the real
  dispatcher, and asserts the name is gone; `apply clears case-insensitively`
  covers `CLEAR`. This guards the documented argument-wiring risk.
- **Double-chest proxy is meaningful, not vacuous.** I traced `doubleChestOf`
  against the actual API: `DoubleChest.getLeftSide()` calls
  `inventory.getLeftSide().getHolder()`, the proxy returns
  `left.inventory`/`right.inventory`, and MockBukkit's `ContainerStateMock`
  builds its inventory with `this` as the holder (`ContainerStateMock.java:48`,
  `ChestStateMock.java:108`), so `leftSide`/`rightSide` resolve to the same
  `left`/`right` states and the `as? Chest` casts succeed. The assertions then
  discriminate: if either cast fell back to `listOf(chest)`,
  `name is read from either half` would read the unnamed left and fail, and
  `setting and clearing a name writes both halves` would fail on the right half.
  It is white-box but genuinely exercises the resolution branch.
- **Residual limitation (not a defect).** MockBukkit has no `DoubleChest`
  implementation at all, so the production `chestsOf(block)`
  block→`inventory.holder`→`DoubleChest` wiring cannot be exercised here; the
  tests necessarily call the `chestsOf(chest, holder)` overload directly. The
  `chest.inventory.holder as? DoubleChest` hop relies on real Paper behaviour and
  is only verified by the correctness review. Documenting this is enough; a fake
  block-level double chest would only re-test the fake.
- **No excessive or brittle tests.** `stone is not a chest` and `a non-chest
  block has no chest halves` overlap slightly, but both are one line and cover
  different entry points (`nameOf`/`isChest` vs the internal `chestsOf`).
  `a blank custom name reads as no name` covers the `ifBlank { null }` invariant
  of the public `ChestNamer` API even though the command short-circuits blanks;
  cheap and legitimate. No snapshot or incidental-structure assertions.
- **Export JSON deferral (round-1 #5) is reasonable.** `model`/`export` and 060
  do not exist on this branch; `nameOf` returning plain text is the correct
  seam, and 060 `depends-on: [050]`.
