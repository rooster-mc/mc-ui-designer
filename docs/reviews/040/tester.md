# Tester review — 040 (Double-chest grouping)

## Round 1
### Verdict
Ship with fixes. Acceptance-criteria coverage is genuinely there and the
holder-vs-geometry routes are properly distinguished, but the four orientation
tests only ever drive the `LEFT` branch of the fallback's offset table, and the
fallback is the route these tests exist to pin.

### Issues
#### 1. Orientation tests never exercise `partnerOffset(RIGHT, …)` (severity: medium)
- Location: `src/test/kotlin/dev/cypdashuhn/uidesigner/capture/DoubleChestGrouperTest.kt:195-211` and `src/main/kotlin/dev/cypdashuhn/uidesigner/capture/DoubleChestGrouper.kt:91-98`
- Problem: `assertOrientationMerges` always passes `listOf(content(left, …), content(right, …))`, i.e. the `LEFT`-typed block first. `group` merges on the first half, so `partnerOffset` is only ever called with `ChestData.Type.LEFT`. The `RIGHT` table (impl:91-98) is dead in the suite. Under the real scanner order (`x`, then `y`, then `z`), `SOUTH` and `WEST` doubles are encountered `RIGHT`-first (e.g. `SOUTH`: right at `(-1,0)`, left at `(0,0)`), so exactly the untested branch is the one production reaches first for half the orientations. A transposed/wrong `RIGHT` entry would pass all four green orientation tests.
- Suggested fix: make the orientation helper take the input order, or pass `listOf(content(right, …), content(left, …))` for at least `SOUTH`/`WEST` (a `rightFirst: Boolean` parameter or a small parameterized case). Assert the same merged result and canonical position.

#### 2. No test that adjacent non-double chests are not merged (severity: low)
- Location: `src/test/kotlin/dev/cypdashuhn/uidesigner/capture/DoubleChestGrouperTest.kt` (whole file); impl `DoubleChestGrouper.kt:52-73`
- Problem: The `a double chest and an unrelated single chest` test (`:156`) uses a chest five blocks away, so it never exercises the grouper with two *adjacent* blocks whose holder is not a `DoubleChest` and whose block data is `SINGLE`. That is exactly the arrangement MockBukkit produces for adjacent chests and the scenario that must not over-merge. `ChestScannerTest.kt:109` pins scanner-level separation, but nothing pins the grouper's no-over-merge behaviour. The acceptance criterion only mentions under-emission ("no chest emitted twice"); the dual failure mode (over-merge) is unguarded.
- Suggested fix: add one test with two adjacent `plainChest` blocks in `contents`, asserting two entries of 3 rows each (not one 6-row entry).

#### 3. Double row/slot mapping is only tested on a fully-filled 54-slot inventory (severity: low)
- Location: `DoubleChestGrouperTest.kt:70-97`
- Problem: `filledItems(54)` means every slot is non-empty, so the merged path never exercises the "empty slots omitted" rule and never crosses the half boundary with a gap. The natural off-by-one in `rows()` is index 27 (`27 / 9 + 1 = 4`, slot 1); the current test only checks index 53 (row 6 slot 9), index 9 (row 2 slot 1) and index 8 (row 1 slot 9). `rows()` is shared with singles, so risk is limited, but this is the one boundary unique to doubles.
- Suggested fix: leave `items[26]`/`items[27]` empty and assert slot `(3,9)` present, `(4,1)` present, `(3,8)`/`(4,2)` absent — or simply one sparse double case asserting the omitted slots and the row-4 mapping.

#### 4. Orientation expectations have no independent geometry anchor (severity: low)
- Location: `DoubleChestGrouperTest.kt:117-131`, `:195-211`; impl `DoubleChestGrouper.kt:81-100`
- Problem: The expected `(dx, dz)` values are hand-written literals that mirror the implementation's own table, so the tests pin "the code uses this table" but cannot validate it against vanilla `Chest` geometry. Because MockBukkit cannot form a real double (documented in `docs/architecture.md:70-81`), a swapped clockwise/counter-clockwise rule would still be four green tests. This is a known limitation, not a defect, but the suite currently reads as if the fallback geometry is verified.
- Suggested fix: either derive the expected partner from Bukkit's `BlockFace.getClockWise()`/`getCounterClockWise()` in the test so the intent is explicit and reviewable, or add a line to the ticket/architecture notes that the fallback's real-world geometry is dev-server-verified only. Do not add a test that reads the same table from the implementation.

### Non-issues
- Acceptance criteria are covered: single chest (`:43`), both halves (`:70`), all four orientations (`:117-131`), one half only (`:133`), exactly one 6-row/54-slot entry (`:86-96`), and no double emission (`:100`, `:157`).
- The two detection routes are genuinely distinguished, not accidentally both taken: `installDouble` (`:213`) uses default `SINGLE` block data with a fake `DoubleChest` holder, so the holder route is required; `geometryChest` (`:241`) provides an inventory whose holder is the `FakeChestState`, so `holder as? DoubleChest` is null and the geometry fallback is required. A test that only proved one route would fail if the other were removed.
- The reflective `DoubleChestInventory` proxy (`:213-239`) matches the established `ChestNamerTest.kt:132-147` pattern and is an acceptable workaround for MockBukkit's inability to form doubles.
- Reversed-input determinism is covered (`:109`), and the canonical position is asserted as the lower half in both orders.
- `UiSlot` mapping is pinned: namespaced ids (`minecraft:*`), custom display name (`:60-67`), 1-based row/slot, and empty-slot omission for singles (`:56-59`).
- `name = null` on the merged single is asserted (`:54`); naming is 060's scope, so its absence on doubles is fine.
- No integration test is expected here: wiring `grouper → JsonExporter` is explicitly 060's scope (`docs/tasks/060-export-command.md:15-16`), and `DoubleChestGrouper.group` is not yet called from production.
- No excessive tests. The four orientation tests and the two canonical-position tests are cheap, each pins a named acceptance criterion, and `slotItems()` (`:287`) is a reasonable assertion helper. Duplicate-slot masking by `toMap()` is backstopped by the `sumOf { it.slots.size } == 54` assertion at `:178`.
- Tests follow repo conventions: backticked sentence names, JUnit 6, MockBukkit `mock`/`unmock` per test.

## Round 2
### Verdict
Ship with fixes. All four round 1 issues are genuinely resolved and the two new
correctness regression tests fail against the old behaviour; the one remaining
gap is that the new right-first orientation tests assert aggregate counts rather
than which half lands in rows 1-3, so the fallback's half ordering is still
unpinned.

### Issues
#### 1. Right-first orientation tests still don't pin the merged half ordering (severity: low)
- Location: `src/test/kotlin/dev/cypdashuhn/uidesigner/capture/DoubleChestGrouperTest.kt:289-314` (assertions at `:309-313`); impl `DoubleChestGrouper.kt:85-89`.
- Problem: The round 1 fix correctly drives the RIGHT branch for SOUTH/WEST, but
  `assertOrientationMerges` only asserts entry count, `rows`, total slot count
  and canonical position. `orderedItems` orders the two halves by position, so
  for SOUTH/WEST the RIGHT half (lower position) becomes rows 1-3, while the
  holder route reads the real shared inventory (LEFT half first). The tests
  cannot tell "both halves combined" from "the same half twice" or a swapped
  order; a regression in the fallback ordering for right-lower orientations
  stays green. The geometry fallback test (`:199-225`) pins ordering only for
  NORTH, where the LEFT half is lower.
- Suggested fix: put distinct items at the first/last slot of each half in the
  right-first case and assert they land at `(1,1)/(3,9)` vs `(4,1)/(6,9)`, or
  add a fallback case where the RIGHT half is at the lower position.

### Non-issues
- **Round 1 issue 1 resolved.** `:191-197` pass `rightFirst = true` for
  SOUTH/WEST; `group` processes the RIGHT-typed block first, so
  `partnerOffset(RIGHT, SOUTH/WEST)` (`impl:110-117`) runs, `isComplementaryHalf`
  passes and the merged result is asserted (1 entry, 6 rows, 54 slots, canonical
  position). Reverting the RIGHT table or the right-first path fails these.
- **Round 1 issue 2 resolved.** `adjacent chests without a double holder stay
  separate singles` (`:71-85`) uses two adjacent default-`SINGLE` chests and
  asserts two 3-row entries with their own items; this is exactly the MockBukkit
  arrangement that must not over-merge.
- **Round 1 issue 3 resolved.** `a sparse double chest omits empty slots around
  the half boundary` (`:134-155`) nulls indices 26/27 and asserts `(3,9)`/`(4,1)`
  absent, `(3,8)`/`(4,2)` present, 52 slots. The fallback test also crosses the
  index 26/27 boundary.
- **Round 1 issue 4 resolved by documentation.** `docs/architecture.md:88-91`
  now states the fallback geometry is hand-checked against vanilla and cannot be
  verified by MockBukkit. No test reads the implementation table back, which was
  the round 1 caution.
- **Correctness issue 1 regression is genuine.** `the geometry fallback combines
  both halves' captured slots` (`:199-225`) drives the holder-less `geometryChest`
  route and asserts the concatenated 27+27 items at `(1,1)`,`(3,9)`,`(4,1)`,`(6,9)`.
  Under the old fallback (current half's empty 27-slot inventory, hardcoded 6
  rows) `slots.size` would be 0, so it fails.
- **Correctness issue 2 regression is genuine.** `a single chest is not re-merged
  by a mismatched adjacent half` (`:87-103`) has the `SINGLE` chest consumed
  first, then a `LEFT` half pointing at it; `partner in consumed` (`impl:66`)
  rejects the merge and two 3-row entries are asserted. The old code merged to
  one entry.
- **Route separation is still real.** `installDouble`/`fakeChest` leave block
  data `SINGLE`, so only the holder route can merge; `geometryChest` leaves the
  holder as the state itself, so only the fallback can merge. Holder mechanics
  check out: `DoubleChest.getLeftSide()` unwraps `inventory.getLeftSide().getHolder()`,
  so the proxy's `ChestInventoryMock(left, 27)` resolves to the `FakeChestState`.
- **No excessive tests.** 16 tests, each tied to an acceptance criterion or a
  named regression; the canonical/reversed pair and the four left-first
  orientation tests remain cheap and distinct. No test reads the `partnerOffset`
  table back.
- **Conventions held.** Backticked sentence names, JUnit 6, MockBukkit
  `mock`/`unmock` per test, no unused imports. The trailing commas
  (`geometryChest`, `:344`) are fine: `.editorconfig` disables both ktlint
  trailing-comma rules.
