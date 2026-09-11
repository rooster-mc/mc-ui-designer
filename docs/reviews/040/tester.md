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
