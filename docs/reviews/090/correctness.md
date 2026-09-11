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
