# readability review — 140 (Abort export on a double chest clipped by the selection)

## Round 1

### Verdict

Ship with two small fixes: a shadowed function/variable name in `group` and a
duplicated `DoubleChest` half-position mapping. Everything else in the changed
source reads cleanly and is ktlint-shaped.

### Findings

#### 1. Local `clippedHalf` shadows the function `clippedHalf`

- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/capture/DoubleChestGrouper.kt:48`
- Problem: `val clippedHalf = clippedHalf(region, content.position, byPosition)`
  gives the local the same name as the function it calls. The initializer still
  resolves to the function, but after this line `clippedHalf` means the
  `ClippedHalf?` value, so a reader (and any later edit that calls the function
  again in the same iteration) has to disambiguate two different things with one
  name.
- Suggested fix: rename the function to `findClippedHalf(...)` (or the local to
  `clipped`), leaving one name for one thing.

#### 2. `DoubleChest` half-to-`BlockPos` mapping is duplicated

- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/capture/DoubleChestGrouper.kt:95-97`
  and `src/main/kotlin/dev/cypdashuhn/uidesigner/capture/DoubleChestGrouper.kt:156-157`
- Problem: `listOfNotNull(leftSide as? Chest, rightSide as? Chest).map { BlockPos(it.x, it.y, it.z) }`
  now appears in both `partnerOf` and `DoubleChest.halvesIn`. Two copies of the
  same side-extraction mean a future change (e.g. handling a missing side) has
  to be made twice or silently drifts.
- Suggested fix: add one helper, e.g.
  `private fun DoubleChest.positions(): List<BlockPos> = listOfNotNull(leftSide as? Chest, rightSide as? Chest).map { BlockPos(it.x, it.y, it.z) }`,
  then use `holder.positions()` in `partnerOf` and `positions().filter { ... }` in
  `halvesIn`.

### Non-findings

- No new comments were added; the only comment inside the changed hunk is the
  pre-existing block in `DoubleChestGrouper.kt:27-31`, which explains a
  non-obvious *why* (why the geometry fallback exists) and is left alone.
- `GroupResult`, `ClippedHalf`, `partnerInsideSelection`, `GroupResult.clipped`,
  and `SaveOutcome.ClippedChests` are all clear in context; `SaveOutcome`'s new
  case follows the existing sealed-interface shape.
- `clippedChestsMessage` pluralisation and the `BlockPos.coords()` extension
  (defined next to its only callers) are easy to follow; `coords()` is not
  redundant with `BlockPos`'s data-class `toString()`.
- The clipped-vs-single branch in `group` (`DoubleChestGrouper.kt:40-56`) reads
  top-to-bottom as match → clipped → single thanks to the early `continue`s; I
  could follow it without backtracking.
- `geometryPartner` removes the duplicated offset maths the old `geometryMatch`
  carried, and `containsBlock` is justified because `Region.contains` only
  accepts a `Location`, so the bounds test avoids a Location round-trip.
- No line exceeds the 100-column limit; trailing commas and wrapping match the
  surrounding ktlint style.
- The four new grouper tests are mirrored near-duplicates; that is test-quality
  territory (tester), not flagged here.
