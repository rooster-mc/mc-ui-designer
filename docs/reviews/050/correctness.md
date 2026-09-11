# Correctness review — 050 (/chest-edit naming)

## Round 1
### Verdict
Ship with fixes. The single-chest path is technically sound (`state` +
`customName` + `update` is the right Paper idiom, persistence and permission
registration are real), but the naming API is not double-chest aware, so both
export-related acceptance criteria can silently fail depending on which half is
looked at or treated as canonical. Blank names and the 5-block reach are minor.

### Issues
#### 1. `ChestNamer` only touches the looked-at half of a double chest (severity: high)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/naming/ChestNamer.kt:19-29`;
  `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/ChestEditCommand.kt:51-59`
- Problem: A double chest is two independent `ChestBlockEntity`s and the custom
  name lives per block entity (`CraftContainer.customName(Component)` writes the
  state snapshot's `name` field). `setName`/`clear` mutate only `block.state`;
  `nameOf` reads only the given block. Consequences:
  - Name the +X half, then ticket 040 groups the double and reads the canonical
    (lowest-x) half -> `nameOf` returns `null`, so the JSON `name` is `""` and
    the AC "renaming then exporting produces the name" fails.
  - Clear while looking at the half that carries no name -> the other half keeps
    its name, so the export still contains it and the AC "clearing removes it
    from subsequent exports" fails.
  - The GUI title is per-half too, so opening the other half shows "Chest".
- Repro: place a double chest; look at the right half; `/chest-edit Shop`; look
  at the left (canonical) half; `ChestNamer.nameOf(left)` -> `null`. Or name one
  half, look at the other, `/chest-edit clear`, then `nameOf` the first half ->
  still `"Shop"`.
- Suggested fix: resolve the double chest inside `ChestNamer`
  (`(block.state as? Chest)?.inventory?.holder as? DoubleChest`, then
  `getLeftSide()`/`getRightSide()`; verified that `CraftChest.getInventory()`
  returns a `DoubleChestInventory` when placed and paired) and apply
  `setName`/`clear` to both halves; make `nameOf` return the first non-null name
  across both halves. If instead the fix lives in 040, document the contract and
  still make `clear` cover both halves.

#### 2. Blank/whitespace names are stored as real custom names (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/naming/ChestNamer.kt:19-23`
- Problem: `/chest-edit ""` (Brigadier accepts a quoted empty string) or
  `/chest-edit "   "` sets `Component.text("")`/spaces as the custom name.
  `nameOf` then returns `""`/whitespace rather than `null`, the chest GUI shows
  an empty title, and the exporter emits `"name": ""` or a whitespace name
  instead of the unnamed form. The player gets a success message for what is
  effectively a no-op.
- Repro: `ChestNamer.setName(chest, "")`; `ChestNamer.nameOf(chest)` -> `""`
  (not `null`).
- Suggested fix: treat `rawName.isBlank()` as `clear` (or reject it with
  feedback) before calling `setName`.

#### 3. `REACH = 5` exceeds the player's survival block reach (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/ChestEditCommand.kt:12,16`
- Problem: The ticket says "within the player's reach / line of sight", but the
  hardcoded 5 is above vanilla survival block interaction range (4.5). A
  survival player can name a chest slightly out of normal reach; creative's 5
  is fine. Harmless for op design work, but it is not the player's reach.
- Suggested fix: derive it from the player, e.g.
  `player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE)?.value ?: 4.5`, or
  document that 5 is intentional tooling reach.

#### 4. Permission denial has no explicit feedback (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/ChestEditCommand.kt:30`
- Problem: `.withPermission("uidesigner.chest-edit")` is applied as a Brigadier
  `requires` predicate (`CommandAPIHandler.permissionCheck`). A denied player
  does not receive the ticket's "no permission" feedback; the command simply is
  not found and the server replies with the vanilla unknown/incomplete-command
  text. The ticket lists "no permission" feedback in scope. CommandAPI does
  register the node with `PermissionDefault.OP` (`platform.registerPermission`),
  so ops still work.
- Suggested fix: acceptable to defer to 070 (as the ux report does), but if kept
  in 050, register without `withPermission` and check
  `player.hasPermission("uidesigner.chest-edit")` in the executor to send a
  clear denial.

#### 5. Export acceptance criteria cannot be exercised on this branch (severity: low, defer)
- Location: `docs/tasks/050-chest-naming.md:25-26`
- Problem: `model`/`export` (020) and the save pipeline (060) do not exist yet,
  so "renaming then exporting produces the name in JSON" and "clearing removes
  it from subsequent exports" are unverifiable and untested. `ChestNamer.nameOf`
  is the only seam.
- Suggested fix: ensure 060's fixture names a chest via `ChestNamer` and adds a
  double-chest case (issue 1); nothing to add in 050.

### Non-issues
- **`state` + `customName` + `update(true)` is correct.** Verified against the
  Paper 1.21.10 server bytecode: `CraftContainer.customName(Component)` sets the
  snapshot's `name` field, and `CraftBlockEntityState.update` -> `applyTo` ->
  `copyData` (`saveWithFullMetadata` + `loadWithComponents`) copies the snapshot
  (name and items) back to the live block entity. Persistence across restarts
  follows from the block-entity NBT save; `applyPhysics` is harmless here.
- **Trapped chests.** `Material.TRAPPED_CHEST` maps to the same
  `org.bukkit.block.Chest` state (paper-api has no separate `TrappedChest`
  type), so `isChest` + `as? Chest` is consistent for both.
- **`getTargetBlockExact` nullability.** It can return `null` (air/out of
  range); `apply` handles `null` and non-chests as `NotAChest`. The player-only
  executor is right for a "look at" command.
- **`onLoad`/`onEnable` ordering.** `CommandAPI.onLoad(...)` in `onLoad`,
  `CommandAPI.onEnable()` then registration in `onEnable`, and
  `CommandAPI.onDisable()` in `onDisable` match the 11.2.0 API (`onLoad` guards
  against a second call; `onDisable` resets the handler). MockBukkit calls
  `onLoad`, and `unmock()` disables plugins, so tests do not leak CommandAPI
  state between `UiDesignerPluginTest` and `ChestEditCommandTest`.
- **Test classpath swap.** Excluding `commandapi-paper-shade` from the test
  configurations and adding `commandapi-paper-core` + `-test-toolkit` is
  coherent; the suite is green (5 + 5 + 1 tests, 0 failures).
- **JSON casing/ordering.** No exporter exists in this ticket; `nameOf` returns
  plain text via `PlainTextComponentSerializer`, matching the plain-text `name`
  field. Returning `String?` (rather than a `Component?`) is a workable seam for
  060.
- **Reserved `clear` word.** The collision is inherent to the ticket's
  `/chest-edit clear` syntax and is documented in the code comment; "Clear" and
  "CLEAR" are settable names. Case-sensitivity and the missing escape hatch are
  covered in the ux report.

## Round 2
### Verdict
Ship. The round-1 high finding is genuinely fixed: `ChestNamer` now resolves the
`DoubleChest` and writes/clears both halves, `nameOf` returns the first non-blank
name across both halves, and blank/`clear` handling in `ChestEditCommand.apply`
is case-insensitive. I traced the Paper implementation and the write path is
sound, so no new functional defect was found. The remaining items are low: the
production double-detection path (`chestsOf(block)`) has no test, a
partially-loaded double is only named on one half, permission-denial feedback is
still deferred, and the public `setName` still accepts blank.

### Issues
#### 1. The production double-chest detection path is never exercised (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/naming/ChestNamer.kt:36-39`;
  `src/test/kotlin/dev/cypdashuhn/uidesigner/naming/ChestNamerTest.kt:105-129`
- Problem: Every double-chest test calls the internal overload
  `chestsOf(chest, holder)` with a hand-built `DoubleChest` proxy; no test calls
  `ChestNamer.nameOf`/`setName`/`clear` on a real placed double chest, and
  MockBukkit does not form one. The path that actually discovers the double in
  production, `chest.inventory.holder`, is therefore unguarded: replacing
  `chestsOf(block)` with `return listOf(chest)` keeps the suite green and
  silently re-introduces round-1 issue 1. I verified against Paper 1.21.10
  bytecode that `CraftChest.getInventory()` returns a `CraftInventoryDoubleChest`
  when paired and `CraftInventoryDoubleChest.getHolder()` returns a `DoubleChest`
  whose `getLeftSide()`/`getRightSide()` are `CraftChest` snapshots, so the code
  is correct today; the gap is regression protection, not a live bug.
- Repro: change `ChestNamer.chestsOf(block)` to skip the holder lookup; the whole
  suite still passes while `/chest-edit` names only the looked-at half.
- Suggested fix: add a test that drives `chestsOf(block)` (not the holder
  overload) with a `Block` whose `state` is a fake `Chest` whose
  `inventory.holder` is a `DoubleChest` (Mockito/`Proxy`), asserting both halves
  are returned. If MockBukkit can place a real double, prefer that.

#### 2. A partially-loaded double chest is only named/cleared on the looked-at half (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/naming/ChestNamer.kt:38,41-46`
- Problem: `chest.inventory.holder` is a `DoubleChest` only when `ChestBlock` can
  combine both halves; if the other half is in an unloaded chunk, it is a single
  and `chestsOf` returns only the looked-at half. `/chest-edit clear` then leaves
  the other half's old name in NBT, and once that chunk loads `nameOf` (which
  reads both halves) returns the stale name, so the export still contains it.
  This is the one case where the round-1 "clearing removes it from subsequent
  exports" criterion can still fail.
- Repro: name a double chest; move so only one half is loaded; `/chest-edit
  clear` on the loaded half; load the other half; `ChestNamer.nameOf` returns the
  old name.
- Suggested fix: document the limitation in `docs/design.md`, or detect the
  double from block data (facing + `Chest.Type.LEFT`/`RIGHT`, as 040's notes
  allow) so both halves are reached regardless of chunk load. Low priority: 040
  and the exporter also only see loaded halves.

#### 3. Permission denial still has no explicit feedback (severity: low, deferred)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/ChestEditCommand.kt:31`
- Problem: `.withPermission("uidesigner.chest-edit")` is a Brigadier `requires`
  predicate, so an unauthorised player gets the vanilla unknown-command text
  rather than the ticket's "no permission" feedback; the AC "Player feedback ...
  on failure (not a chest, no permission)" is not met. Round 1 accepted deferral
  to 070; it is still open.
- Suggested fix: keep deferring to 070, or register without `.withPermission` and
  check `player.hasPermission("uidesigner.chest-edit")` in the executor to send a
  denial.

#### 4. `ChestNamer.setName` still stores blank custom names when called directly (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/naming/ChestNamer.kt:27-30,48-51`
- Problem: `ChestEditCommand.apply` guards blank input, but the public
  `setName(block, name)` does not. `ChestNamer.setName(chest, "   ")` stores a
  whitespace `Component` (blank GUI title) while `nameOf` normalises it to `null`
  (unnamed in JSON), so the GUI and the eventual export disagree. No current
  caller passes blank, but `setName` is the documented seam for the
  exporter/command.
- Repro: `ChestNamer.setName(chest, "   ")`; open the chest -> blank title;
  `ChestNamer.nameOf(chest)` -> `null`.
- Suggested fix: make `setName` treat `name.isBlank()` as `clear`, or reject
  blank at the API boundary.

### Non-issues
- **Round-1 issue 1 is resolved.** `chestsOf` now returns `listOf(left, right)`
  for a double and `nameOf` uses `firstNotNullOfOrNull(::readName)`, so naming
  either half (via the write path) is visible from the canonical half and
  clearing from either half removes both. Both round-1 failure scenarios no
  longer reproduce.
- **No recursion.** `chestsOf(chest, holder)` does not call `chestsOf(block)`;
  the `DoubleChest` lookup terminates in `BlockEntity.getOwner()`.
- **No stale-state / double-update bug.** `chestsOf(block)` uses the
  `block.state` snapshot only for the fallback; for a double it returns fresh
  `getOwner()` snapshots of each half. Each half is written exactly once
  (`customName` + `update`), and `CraftContainer.customName` writes the
  snapshot's `name` field, which `update` -> `copyData` applies to the live block
  entity. The two halves are distinct block entities, so one update cannot revert
  the other.
- **Fallback is safe.** When the holder is not a `DoubleChest`, or one side fails
  to cast to `Chest`, `chestsOf` returns `listOf(chest)` — the looked-at half —
  which is the conservative choice.
- **040 interaction is fine.** Because `setName`/`clear` write both halves,
  040's canonical-half choice no longer determines whether the name is seen.
  `nameOf` order (`DoubleChest.leftSide`/`rightSide`) is deterministic, and the
  halves can only diverge through external NBT edits.
- **Blank/whitespace handling is consistent.** `apply` maps `isBlank()` to
  `Cleared`, and `readName` maps a blank serialised name to `null`, so
  `docs/data-format.md`'s unnamed form is reached either way.
- **Case-insensitive `clear`.** `rawName.equals("clear", ignoreCase = true)`
  clears for `Clear`/`CLEAR`; the literal name "clear" is unreachable by design,
  now documented in `docs/design.md`.
- **`REACH = 5`.** Still above the 4.5 survival block reach, but the code now
  documents it as intentional op tooling reach, which was round-1's accepted
  alternative; not re-raised.
- **`onLoad`/`onEnable` ordering.** `CommandAPI.onLoad` in `onLoad`,
  `CommandAPI.onEnable()` + registration in `onEnable`, `CommandAPI.onDisable()`
  in `onDisable` are the 11.2.0 lifecycle; the command executor and all block
  access stay on the main thread.
- **Trapped chests.** `Material.TRAPPED_CHEST` maps to the same
  `org.bukkit.block.Chest` state, so `isChest` + `as? Chest` and the double path
  are consistent; now covered by a round-trip test.
- **No JSON output in this ticket**, so `docs/data-format.md` casing/ordering and
  IO-failure/partial-write concerns do not apply here; `nameOf` returning
  `String?` remains a workable seam for 060.
