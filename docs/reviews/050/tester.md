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
