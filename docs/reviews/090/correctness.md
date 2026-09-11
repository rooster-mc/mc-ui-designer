# Correctness review — 090 (Use rooster-region BlockPos and consolidate model)

## Round 1
### Verdict
Ship. The refactor is behaviour-preserving: the adopted library value type and
region accessor are source-identical to the deleted ones, the dropped
`FaweSelectionSource` guard is subsumed by the world-scoped library function, and
the package move leaves the JSON contract untouched. No correctness findings.

### Findings
None.

### Non-findings
- **`dev.rooster.region.BlockPos` is behaviourally identical to the deleted
  `model/BlockPos.kt`.** Both are `data class BlockPos(val x: Int, val y: Int,
  val z: Int) : Comparable<BlockPos>` with the same canonical `x` then `y` then
  `z` `compareTo` (`rooster-region/core/.../BlockPos.kt:3-17` vs the deleted
  `model/BlockPos.kt`). `equals`/`hashCode`/`toString` are generated the same
  way, so `Map`/`Set` keying (`DoubleChestGrouper.kt:20,21,43`) and the
  `sortedBy { position }` ordering in `JsonExporter.kt:32-35` are unchanged.
- **The JSON shape is unchanged.** `UiChest.position` is still
  `@Transient val position: BlockPos? = null` (`export/UiChest.kt:19`), and the
  library `BlockPos` is a plain data class with no `@Serializable`/`@SerialName`,
  so no `position` field is (or was) emitted. `DesignJson` is byte-for-byte the
  same config (`export/UiChest.kt:8-12`: `prettyPrint = true`,
  `encodeDefaults = false`), and `@Serializable` emits property names, not
  packages, so the `model/` → `export/` move cannot change the output.
- **`Region.blockAt` matches the deleted `capture/RegionExt.blockAt` exactly.**
  Library `Region.kt:103` is `world.getBlockAt(position.x, position.y,
  position.z)`; the deleted extension was the same body. All three former call
  sites now resolve to the member with the same argument type and semantics:
  `DoubleChestGrouper.kt:46`, `DoubleChestGrouper.kt:77`,
  `UiDesignerCommand.kt:140`. No `import ...capture.blockAt` remains, so there is
  no shadowing/ambiguity.
- **Dropping the `FaweSelectionSource` cross-world guard loses no case.** The
  library `worldEditSelection()` (`rooster-region/worldedit/.../Adapter.kt:32-41`)
  already returns `null` when `localSession.selectionWorld` is null (line 37) or
  differs from `BukkitAdapter.adapt(player.world)` (line 38) before returning a
  selection. `selection.world` is necessarily `selectionWorld`, so the old
  `selection.world?.name != player.world.name` check could only fire in a case
  the library now rejects first; null-selection-world and undefined-selection
  cases are also handled there. The build consumes this exact checkout via the
  `includeBuild` substitution (`settings.gradle.kts:12-18`), and the rooster
  checkout is at the merged 050 state. The only residual (name comparison vs
  world-object identity) is a library concern owned by MT-005, already recorded
  as unverified; nothing new is lost in this plugin.
- **Tester's findings:** concur with `docs/reviews/090/tester.md`. Its
  non-findings about ordering coverage, `Region.blockAt` coverage and the
  dropped guard match what I traced above; I add no finding of my own.

## Round 2
### Verdict
Ship. The source and test trees are byte-identical to the state I reviewed in
round 1 (`git diff a320523 -- src` is empty; `HEAD` is `a320523`, the only
working-tree change is `docs/reviews/090/tester.md`), so every round-1
non-finding still holds verbatim. The round-2 edits are documentation and
agent-prompt wording only and introduce no contract inconsistency.

### Findings
None.

### Non-findings
- **Round-1 non-findings are unchanged.** The library `BlockPos` still matches
  the deleted value type, `Region.blockAt` still matches the deleted extension,
  the dropped `FaweSelectionSource` guard is still subsumed by the world-scoped
  `worldEditSelection()`, and the JSON shape/`@Transient position`/`DesignJson`
  config are untouched. No source file changed after `a320523`, so none of the
  round-1 reasoning needs revisiting.
- **The round-2 doc edits do not contradict any data/integration contract.**
  `docs/architecture.md:22-24` places `UiChest.kt` in `export/` beside
  `JsonExporter.kt`, matching the actual package; `docs/architecture.md:55-64`
  and `docs/design.md:68` restate that `export` is Bukkit-free with `BlockPos`
  sourced from the library, which is true — `dev.rooster.region.BlockPos`
  (`rooster-region/core/.../BlockPos.kt`) imports no Bukkit types, and
  `export/UiChest.kt:3` imports only that pure value type. The prompt edits
  (`.opencode/agent/{implementor,architecture,tester}.md`) drop the now-deleted
  `model` package from their seam descriptions and match the shipped layout.
- **Tester's round 2:** concur with `docs/reviews/090/tester.md` `## Round 2`;
  the source/tests are unchanged, so its round-1 test-scope conclusions remain
  valid and no new manual-test entry is warranted.
