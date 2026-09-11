# Correctness review — 080 (Adopt rooster-region for capture)

## Round 1
### Verdict
Ship with one fix. The region-type swap is behaviour-preserving for the normal
path (normalisation, inclusivity, world, and the three inlined
`getBlockAt` call sites all match the deleted local `Region`), but
`FaweSelectionSource` silently drops the old "selection must be in the player's
current world" guard, so a stale selection made in another world is now
interpreted in the player's world instead of being reported as "no selection".

### Findings
#### 1. Cross-world stale selection is now captured instead of reported as no selection
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/capture/FaweSelectionSource.kt:9-10`
  (call `player.worldEditSelection()?.toRegion(player.world)`), enabled by
  `/home/cyp/repos/rooster-region/worldedit/src/main/kotlin/dev/rooster/region/worldedit/Adapter.kt:32-40`
  (`worldEditSelection()` keys off `localSession.selectionWorld`).
- Problem: the old adapter asked `session.isSelectionDefined(playerWorld)` and
  returned `null` when the session's selection was in a different world
  (`git show HEAD:.../FaweSelectionSource.kt:17-18`). The library adapter instead
  returns the selection of whatever world the session selected in:
  `selectionWorld = localSession.selectionWorld`, `isSelectionDefined(selectionWorld)`,
  `getSelection(selectionWorld)`. `FaweSelectionSource` then converts those
  coordinates with `toRegion(player.world)`, so when the selection world differs
  from the player's current world the result is a `Region` in the player's world
  at the other world's coordinates. `selectionWorld` is the selector's world and
  is not cleared on a world change (FAWE has no `PlayerChangedWorldEvent`
  handler), so this is reachable without any new interaction.
  Reproduction: select a region in world A, teleport to world B (do not touch the
  selection wand there), run `/uidesigner save`. Before the swap: `NoSelection`.
  After: the scanner walks B at A's coordinates, so it either reports
  `NoChests` or silently exports unrelated chests that happen to sit there. This
  violates the ticket's "Existing behaviour is unchanged" acceptance criterion
  and is exactly the runtime-only FAWE path that neither the unit suite (FAWE is
  `compileOnly`) nor `MT-001` (which never switches worlds) can catch.
- Suggested fix: per the ticket's out-of-scope note, file a follow-up in
  `rooster-region` to make `Player.worldEditSelection()` world-scoped (return
  `null` when the selection world differs from `player.world`, or take the world
  as a parameter), and extend `docs/manual-test.md` (a new 080 row or `MT-001`)
  with the cross-world case so the gap is tracked. If the team wants the guard
  in this repo meanwhile, compare the returned WE region's world against
  `BukkitAdapter.adapt(player.world)` before converting — a deliberate, small
  FAWE import in `FaweSelectionSource`, which is the one file the ticket already
  reserves for adapter code.

### Non-findings
- **`minX..maxX` / `minY..maxY` / `minZ..maxZ` is the correct translation of the
  old `region.min.x..region.max.x`.** `rooster-region`'s
  `core/.../Region.kt:28-33` normalises each axis with `coerceAtMost`/
  `coerceAtLeast` from `edge1.blockX`/`edge2.blockX`, and the adapter builds
  those locations from exact `BlockVector3` integers, so the int bounds and the
  inclusive iteration order are identical to the deleted `Region.of`. No
  off-by-one and no inverted-corner regression.
- **The three inlined `region.world.getBlockAt(x, y, z)` call sites are
  equivalent to the removed `Region.blockAt`.** `ChestScanner.kt:17`,
  `DoubleChestGrouper.kt:46,77`, and `UiDesignerCommand.kt:140` all resolve
  `world` from `Region.world` (`edge1.world`), which in production is the world
  the adapter passed in; the old `blockAt` was literally
  `world.getBlockAt(position.x, position.y, position.z)`.
- **`toRegion(player.world)` is functionally the ticket's `toRegion(player)`.**
  The `Player` overload (`Adapter.kt:23-27`) just forwards `player.world`, so the
  deviation from the ticket's wording changes nothing.
- **The joml exclusion is sound; the tester's verification gap is real.**
  `exclude("org/joml/**")` (`build.gradle.kts:132`) matches the joml class
  entries, and Shadow 8.3.6's own task configuration already excludes
  `module-info.class` by default, so the exclude leaves no joml class entries
  (only harmless `META-INF/joml.kotlin_module` metadata). I concur with tester
  finding 1: nothing in the suite inspects the artifact, so the criterion needs
  either a `shadowJar` `doLast` check or a `docs/manual-test.md` row.
- **`SessionManager.get` vs `getIfPresent` is benign.** The library's
  `worldEditSelection()` uses `sessionManager.get(actor)`, which creates/loads a
  session where the old code used `getIfPresent`. The default store is
  `VoidStore` (verified in FAWE-Core 2.15.3), a fresh `LocalSession`'s selector
  has a null world, and `selectionWorld ?: return null` handles that, so a player
  with no session still gets `null`; only a short-lived in-memory session object
  is added.
- **The library `Region` same-world `require` introduces no new failure.** Both
  the adapter overloads and all three updated test helpers construct both edges
  from one world, so `require(edge1.world == edge2.world)` cannot trip.
- **`RegionTest` deletion loses no coverage that matters here.** The inverted/
  mixed-corner cases now live in `rooster-region`'s own `RegionTest`; this repo
  has no remaining consumer of raw `edge1`/`edge2` ordering.
