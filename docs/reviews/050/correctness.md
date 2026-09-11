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
