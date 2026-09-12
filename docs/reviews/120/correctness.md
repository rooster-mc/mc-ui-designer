# Correctness review — 120 (consume rooster-region lazy block enumeration)

## Round 1
### Verdict
Ship. The new `ChestScanner.scan` enumerates exactly the same block set as the
deleted triple loop, reads the same per-block inventory, clones the same way,
and the only change (x/y/z-major -> chunk-major order) is unobservable by any
production consumer. No findings.

### Findings
None.

### Non-findings
- **Enumeration completeness is identical.** Old loop (`ChestScanner.kt` before
  the diff) visited every `(x, y, z)` in `minX..maxX × minY..maxY × minZ..maxZ`
  and skipped when `world.isChunkLoaded(x shr 4, z shr 4)` was false. New code
  iterates `Region.loadedBlockPositions` (`../rooster-region/core/.../Region.kt:120-139`),
  which visits `chunkX in minXChunk..maxXChunk`, `chunkZ in minZChunk..maxZChunk`
  (both `floorDiv`), skips unloaded chunks, and intersects each chunk with the
  region via `fromX = maxOf(minX, chunkX shl 4)` / `toX = minOf(maxX, chunkX shl 4 + 15)`
  (same for Z), then `y in minY..maxY`. For every region coordinate `x`,
  `floorDiv(x, 16) == x shr 4` (holds for negatives: `-1 shr 4 == -1 == floorDiv(-1,16)`,
  `-17 shr 4 == -2 == floorDiv(-17,16)`), and its chunk is in the swept
  `minXChunk..maxXChunk`, so the union is the same set. Conversely `fromX..toX`
  is always clamped into the region, so no extra positions are yielded. No
  duplicates: x/Z chunk ranges partition the coordinates.
- **Chunk-load semantics equivalent.** Both paths call `World.isChunkLoaded`
  with the same chunk coordinates, evaluated before any `getBlockAt`, so neither
  forces a chunk load. `Region.blockAt` (`Region.kt:104`) just wraps
  `world.getBlockAt(position.x, position.y, position.z)`.
- **Chunk-major ordering is unobservable.** `jsonExporter` is the ordering
  authority and sorts by `BlockPos` natural order (`JsonExporter.kt:32-45`), and
  two chests can never share a canonical position from this pipeline, so the
  stable-sort tie behaviour documented in `docs/data-format.md:41-44` never
  depends on scanner order. The only other consumer, `DoubleChestGrouper.group`
  (`DoubleChestGrouper.kt:24-40`), is order-independent: it indexes by position
  (`associateBy`, line 25), grounds both routes in the deterministic partner
  offset, uses `match.positions.min()` for the canonical position (line 33),
  and `orderedItems` normalises right-half-first by `type` rather than input
  order (lines 100-102). The grouper tests already pin reversed-input equality
  (`DoubleChestGrouperTest.kt:306,326-327`). If anything, adjacent double-chest
  halves are still yielded in the same relative order in both schemes.
- **Position source change is a no-op.** `position = position` (the enumerated
  `BlockPos`) equals the old `BlockPos(block.x, block.y, block.z)` because
  `blockAt` resolves the block at exactly `position.x/y/z`; the unselected
  enumerated value is never re-derived from the block.
- **Filter and cast unchanged.** `block.type !in CHEST_MATERIALS` then
  `block.state as? Chest` is byte-for-byte the same predicate/cast as before,
  in the same order, so non-chest blocks and non-`Chest` states are still
  dropped identically.
- **Contents cloning unchanged.** `chest.blockInventory.contents.map { it?.clone() }`
  (own 27-slot block inventory, not the shared double inventory) is preserved,
  so double halves stay independent and captured stacks are detached.
- **Negative coordinates and region bounds.** `minXChunk`/`maxXChunk` use
  `floorDiv` (`Region.kt:64-67`) and `chunkX shl 4` is the correct chunk floor
  for negative chunk indices, so a region on the `-17`/`-16` boundary maps to
  chunk `-2` and enumerates exactly the region intersection.
- **Contract/acceptance.** `ChestScanner.kt` contains no TODO; the
  `docs/architecture.md` scan-loop description is updated consistently with the
  implementation. Return type remains `List<ChestContent>` consumed unchanged by
  `ChestCapture.kt:12-13`, so no integration seam moved.

## Round 2
### Verdict
Ship. The round-1 fixes landed in `852c8e6` and none of them touches the
production path; `src/main/kotlin/.../ChestScanner.kt` is byte-identical to the
revision I reviewed in round 1, so every round-1 correctness check still holds.
No new findings.

### Findings
None.

### Non-findings
- **Concur with round-1 tester finding 1 (duplicate detection), now fixed.**
  `ChestScannerTest.kt:74-75` asserts both set equality and
  `expected.size == positions.size`, so a duplicated enumeration is caught even
  if the set still matches. The assertion stays order-independent, consistent
  with the ticket allowing the enumeration order to change.
- **Concur with round-1 tester finding 2 (manual gate), now fixed.**
  `docs/manual-test.md` `MT-010` names ticket 120 and covers the live-server
  parity path (double chest across a chunk border + unloaded-neighbour chunk)
  that MockBukkit cannot model. This is a test-coverage/tracking concern, not a
  production data-contract change, so it does not alter my verdict.
- **Concur with round-1 architecture finding (doc rewording), now fixed.**
  `docs/architecture.md` attributes enumeration to the library
  (`Region.loadedBlockPositions`) and only scanning/inventory reads to
  `ChestScanner`; the wording matches the code, so the documented scan contract
  remains accurate.
- **No production regression from the fixes.** The commit's diff to
  `ChestScanner.kt` is exactly the round-1 version (filter/cast/cloning and
  `loadedBlockPositions` iteration unchanged), and the other three files are
  docs/tests. Enumeration completeness, chunk-major order being unobservable,
  `isChunkLoaded` equivalence, `BlockPos` sourcing, and the unchanged clone
  semantics from Round 1 all still stand.
- **No new integration seam or data-format change.** `ChestCapture.kt` still
  consumes `List<ChestContent>` unchanged, and `docs/data-format.md` is
  untouched; the exporter remains the ordering authority.
