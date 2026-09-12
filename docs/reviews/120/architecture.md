# Architecture review — 120 (Consume rooster-region lazy block enumeration)

## Round 1
### Verdict
Ship with one doc fix. The scanner now consumes the library enumeration and
resolves positions through `Region.blockAt`, so the library seam is used
correctly and the last direct `World`/`getBlockAt` access in `capture/` is gone;
`loadedBlockPositions` is the right choice over `blockPositions`. One sentence in
`docs/architecture.md` still attributes block enumeration to `ChestScanner`,
which this change moved into the library.

### Findings
#### 1. Architecture doc still says `ChestScanner` owns block enumeration
- Location: `docs/architecture.md:69` (contrast with the updated lines 73-76)
- Problem: The bullet still opens "`ChestScanner` is the only place that
  *enumerates* blocks and reads inventories". After this change the coordinate
  enumeration lives in the library (`Region.loadedBlockPositions`,
  `../rooster-region/core/src/main/kotlin/dev/rooster/region/Region.kt:120-139`);
  the plugin only consumes that sequence and resolves each position. The claim
  now contradicts the clause the diff added two sentences later ("It iterates
  `Region.loadedBlockPositions`") and misattributes exactly the responsibility
  this ticket moved. A reader looking in `ChestScanner` for the chunk-major
  walk / unloaded-chunk skip finds neither.
- Suggested fix: Reword to "`ChestScanner` is the only place that *scans*
  blocks (consuming the library's `Region.loadedBlockPositions`) and reads
  inventories", or equivalent that keeps enumeration attributed to the library.

### Non-findings
- **`loadedBlockPositions` is the right seam, not `blockPositions`.** The old
  loop skipped unloaded chunks (`isChunkLoaded(x shr 4, z shr 4)`) rather than
  forcing a load; `blockPositions` (`Region.kt:106-116`) does not, so choosing
  it would have silently changed behaviour. `loadedBlockPositions` is the exact
  library equivalent and is single-sourced now, so the chunk logic can no longer
  drift between plugin and library.
- **No direct `World` access remains in the scanner.** `ChestScanner.kt:10-20`
  touches only `region.loadedBlockPositions` and `region.blockAt`; the removed
  `region.world.getBlockAt` is the last raw-World seam leak in the scan path.
  The scanner still imports Bukkit (`Material`, `Chest`), which is correct: it
  is the Bukkit-coupled capture-side reader, and `export` stays pure.
- **No new duplication or misplaced responsibility.** The enumeration is not
  re-implemented; `BlockPos` construction is now the library's, so the scanner
  no longer mirrors the library's coordinate walk. The pre-existing
  `CHEST_MATERIALS` duplication with `ChestNamer` is documented as a deliberate
  carry-over (`docs/architecture.md:123-125`) and is outside this ticket's scope.
- **Extendable to the obvious next steps.** The enumeration seam is generic over
  `BlockPos`, so supporting more container types would only touch the material
  predicate and the `as? Chest` cast in `ChestScanner`, not the iteration. Import
  and alternative output formats do not intersect this seam at all. No
  over-generalisation was added.
- **The added ordering note is correct and needed.** The diff's
  "returning one entry per chest block in the library's enumeration order;
  `JsonExporter` normalises order" (`docs/architecture.md:75-76`) correctly
  restates that the scanner's order is now the library's and that the exporter
  remains the ordering authority, so the test-order relaxation does not leak
  into the documented contract.
- **Other living docs are not invalidated.** `docs/design.md:98-100` ("040 and
  the exporter likewise only see loaded halves") stays true under
  `loadedBlockPositions`. `docs/data-format.md:41-44` (exporter orders by
  canonical position) is about `JsonExporter` and is unchanged. `docs/fcp.md`
  only uses `class ChestScanner` as a frontmatter example; no coverage claim
  changed. `docs/manual-test.md` cites no scan loop.
