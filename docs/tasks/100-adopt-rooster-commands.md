---
name: Adopt rooster-commands and drop command-layer accidents
status: todo
parent: Polish
depends-on: ["090"]
reviewers: [correctness, architecture, readability]
---

## Goal
The `2dd20a7` TODO sweep flagged the command layer as the last place still
duplicating rooster concepts. Adopt `rooster-commands` for both commands, drop
the permission concept, and remove the `SelectionSource` seam.

## Scope
- Wire the sibling `../rooster-commands` build into `settings.gradle.kts`
  (composite build, following the `rooster-region` pattern) and add it as a
  dependency in `build.gradle.kts`.
- Rewrite `ChestEditCommand.kt` and `UiDesignerCommand.kt` using
  `rooster-commands` primitives: literals instead of hardcoded strings,
  including the "clear" sentinel literal defined after the first argument, so
  the long comment block becomes unnecessary.
- Remove the permission concept (`uidesigner.save`, `uidesigner.chest-edit`,
  `PERMISSION` constants, `Messages.noPermission`) — this is a local tool.
- Remove `capture/SelectionSource.kt` and `capture/FaweSelectionSource.kt`:
  inline `player.worldEditSelection()?.toRegion(player)` at the single call
  site, with the same lazy-loading containment currently provided by
  `UiDesignerPlugin.faweSelectionSource()` (FAWE must not load during
  MockBukkit tests — reproduce that laziness without the interface).
- Replace the magic strings in `UiDesignerPlugin.kt` (`"config.yml"`,
  the log messages) with named constants; remove the leftover
  `// TODO: Var` / cosmetic-only streak in `reloadConfiguration` where the fix
  is trivial.
- Update `docs/architecture.md` where it names `SelectionSource`.

Then remove every TODO this ticket resolves.

## Out of scope
- `util/Messages.kt` restructuring (ticket 110).
- Any `rooster-region` changes (ChestScanner TODOs are deferred there; each
  TODO note in this repo that points at a sibling repo stays until THAT repo
  ships API for it — see deferral note at bottom).

## Acceptance criteria
- `just build`, `just test`, `just format` pass.
- No `SelectionSource` interface in the tree; `/uidesigner save` and
  `/chest-edit` behave exactly as before (usage, suggestions, messages).
- MockBukkit test suite still passes without FAWE on the classpath.
- No permission checks remain.
- Resolved TODO markers are gone from the touched files.

## Deferrals (record, don't fix)
- "Clear" / command-tree TODOs that require `rooster-commands` API not yet
  shipped there → note them in `../rooster-commands` issue tracker or a
  backlog ticket here, not silently.
- ChestScanner split to `rooster-region` → backlog ticket referencing
  `rooster-region`.
