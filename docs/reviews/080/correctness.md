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

## Round 2
### Verdict
Ship. Round-1 finding 1 is resolved correctly: `FaweSelectionSource` now rejects
a selection whose world is not the player's current world before converting it,
so the stale cross-world selection is reported as no selection again. The
`RegionExt.blockAt` extension also restores the `BlockPos`-keyed lookup seam
without changing behaviour. I found no new correctness issues.

### Findings
#### No new findings.

### Non-findings
- **Round-1 finding 1 is fixed and the guard is correct.**
  `src/main/kotlin/dev/cypdashuhn/uidesigner/capture/FaweSelectionSource.kt:10-14`
  reads the selection once, returns `null` when
  `selection.world?.name != player.world.name` (line 13), and only then converts
  with `toRegion(player.world)`.
  - World comparison: `selection.world` is the region's WorldEdit world; for a
    `BukkitWorld`, `getName()` returns the Bukkit world's name (verified via
    `javap` on FAWE-Bukkit 2.15.3: `BukkitWorld.getName()` calls
    `getWorldChecked().getName()`, and `BukkitWorld.equals` already falls back to
    name equality for a generic `World`). For the wrapper case, `WorldWrapper.getName()`
    delegates to its parent's name (verified via `javap`), so the wrapper cannot
    produce a false mismatch. Names are unique per server, so this matches the
    old `isSelectionDefined(playerWorld)` outcome.
  - Null handling: `selection.world?.name` is null-safe; a null world yields
    `null != player.world.name`, so the function returns `null` rather than
    converting. `player.world` is never null.
  - Normal path: a selection made in the player's current world has matching
    names, so the guard passes and `toRegion(player.world)` produces exactly the
    same `Region` as the round-1 one-liner; there is no behaviour change when the
    selection is valid.
  - Cross-world path: selection in world A, player in world B -> names differ ->
    `null`, which is the pre-swap `NoSelection` behaviour and stops the
    coordinate reinterpretation I reported in round 1.
- **`RegionExt.blockAt` is a faithful restore of the removed helper.**
  `capture/RegionExt.kt:7-8` is `world.getBlockAt(position.x, position.y, position.z)`,
  and `DoubleChestGrouper.kt:46,77` / `UiDesignerCommand.kt:141` call it; the
  scanner's int-triple loop (`ChestScanner.kt:17`) stays inline as before. No
  `BlockPos`-keyed site lost the bounds/accessor semantics, and `internal`
  visibility covers both `capture/` and `commands/` (same Gradle module).
- **The joml `doLast` check is sound.** `build.gradle.kts:134-149` opens the
  finished `archiveFile` and asserts no entry starts with `org/joml/`; it is part
  of `shadowJar`, which `tasks.build` depends on, so `just build` runs it. I
  concur with tester round-2 non-finding that this closes round-1 finding 1.
- **Concur with tester round-2 finding 1.** The new guard is not reachable under
  MockBukkit (FAWE is `compileOnly`) and `docs/manual-test.md` has no 080 row, so
  the fix's regression protection is the manual gate; that is a real tracked gap
  and a tester/process matter, not a code-correctness defect. The code path
  itself is correct as analysed above.
- **The `settings.gradle.kts` sibling `check` changes no runtime behaviour.**
  It fails settings evaluation with a named path when `../rooster-region` is
  absent; that is build topology (architecture finding 1), and the runtime
  contract is unaffected.
