# Tester review — 030 (FAWE selection capture and chest scan)

## Round 1

### Verdict
Ship with fixes. The ordering, "only chests", and contents acceptance criteria
are covered by focused MockBukkit tests, and `RegionTest` covers normalization
well. But the "no selection" test is vacuous (it exercises the test helper and
the fake, not production), and two deliberate production behaviours — item
cloning and the negative-coordinate chunk check — are unprotected. None of these
are large; the suite stays small and behaviour-shaped rather than padded.

### Issues

#### 1. The "no selection" test never exercises production code (severity: medium)
- Location: `src/test/kotlin/dev/cypdashuhn/uidesigner/capture/ChestScannerTest.kt:39-45`;
  helper at `:111-114`.
- Problem: `capture(source)` does `source.selectionOf(player) ?: return emptyList()`,
  so `assertTrue(capture(FakeSelectionSource(null)).isEmpty())` is satisfied
  entirely inside the test helper. The only other assertion,
  `assertNull(source.selectionOf(player))`, tests the `FakeSelectionSource` the
  test itself wrote. No production code in `capture/` maps a null selection to
  an empty result — `SelectionSource`/`FaweSelectionSource` are not referenced
  anywhere else in `src/main` — so the ticket's "friendly failure when the
  player has no selection" has no implementation to test and the AC is only
  nominally met. When 060 wires the pipeline, this test will not catch a missing
  null check.
- Suggested fix: add a thin production entry point and test that, e.g.
  `fun capture(source: SelectionSource, player: Player): List<ChestContent> =
  source.selectionOf(player)?.let(ChestScanner::scan).orEmpty()` in `capture/`,
  used by 060. If that is judged premature for 030, drop this test and move the
  no-selection AC to 060's error-path tests — but do not keep a test that only
  asserts a test helper.

#### 2. Double-chest half independence is untested, and MockBukkit cannot distinguish it (severity: medium)
- Location: `docs/architecture.md:45-48`; `src/main/kotlin/dev/cypdashuhn/uidesigner/capture/ChestScanner.kt:22`;
  `src/test/kotlin/dev/cypdashuhn/uidesigner/capture/ChestScannerTest.kt:91-102`.
- Problem: the scanner deliberately reads `Chest.getBlockInventory()` so each
  chest block gets its own 27-slot inventory and double-chest halves stay
  independent until 040 merges them. Nothing places two adjacent chests, and in
  MockBukkit 4.116.1 `ChestStateMock.getBlockInventory()` simply returns
  `getInventory()` (`ChestStateMock.java:93-96`), so even a two-chest test could
  not detect `getInventory()` being used instead — the test environment would
  mask exactly the bug the architecture note guards against. The existing
  `assertEquals(27, content.items.size)` covers only the single-chest case.
- Suggested fix: if feasible, add a test with two adjacent `CHEST` blocks
  asserting two entries of 27 slots each and no shared items. If MockBukkit
  cannot form a real double chest (likely, given the hand-built
  `DoubleChestInventory` proxy in `ChestNamerTest.kt:132-147`), record the
  limitation in the ticket/architecture note the way 050 did, so the
  `blockInventory` choice is known to be verified only on the dev server / by
  040. Do not add a fake double chest that only re-tests the fake.

#### 3. The scanner's defensive item clone is not pinned (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/capture/ChestScanner.kt:22`.
- Problem: `items = chest.blockInventory.contents.map { it?.clone() }` is a
  deliberate copy so `ChestContent` does not alias live inventory stacks, but no
  test would notice if `.clone()` were dropped: the contents test only checks
  `type` and display name, which survive aliasing.
- Suggested fix: one assertion in the existing contents test, e.g.
  `assertNotSame(chest.blockInventory.getItem(5), content.items[5])`, or mutate
  the captured stack and assert the chest inventory is unchanged.

#### 4. Negative-coordinate chunk handling is untested (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/capture/ChestScanner.kt:15`;
  `src/test/kotlin/dev/cypdashuhn/uidesigner/capture/ChestScannerTest.kt:104-109`.
- Problem: the skip check uses `x shr 4, z shr 4` (arithmetic shift = floor
  division). Every test coordinate is non-negative, so a refactor to `x / 16`
  would misidentify chunks for negative block coordinates and no test would
  fail, even though negative coordinates are common in real worlds.
- Suggested fix: add one case placing a chest at e.g. `(-1, 0, 0)` with chunk
  `(-1, 0)` loaded and assert it is captured, which pins the floor semantics. If
  negative coordinates are deliberately out of scope, say so instead.

#### 5. The unloaded-chunk test relies on an undocumented MockBukkit quirk (severity: low)
- Location: `src/test/kotlin/dev/cypdashuhn/uidesigner/capture/ChestScannerTest.kt:104-109`.
- Problem: the test sets a block at `x = 16` without loading chunk `(1, 0)` and
  assumes `WorldMock.getBlockAt` does not load it. That holds today (blocks live
  in a map independent of `loadedChunks`), but it is a MockBukkit implementation
  detail and the premise "the chunk is unloaded" is implicit. A MockBukkit
  upgrade that loads chunks on `getBlockAt` would turn this test red for reasons
  unrelated to the scanner.
- Suggested fix: assert the premise explicitly before scanning, e.g.
  `assertFalse(world.isChunkLoaded(1, 0))`, so a MockBukkit behaviour change
  fails loudly at the right place. Keeping the test is right — it covers a real
  production branch (skip rather than force-load).

### Non-issues
- **Deterministic ordering AC is well covered.** `several single chests are
  captured in position order` (`ChestScannerTest.kt:55-73`) places chests at
  `(2,0,0)`, `(0,0,2)`, `(0,2,0)`, `(0,0,0)` and asserts the exact x-then-y-then-z
  order, exercising all three tie-break levels plus the inclusive `max` boundary.
- **"Only chest blocks" AC is well covered.** `only chest blocks are captured`
  (`:76-89`) mixes `CHEST`, `TRAPPED_CHEST`, `BARREL`, `SHULKER_BOX` and `STONE`,
  so both the material filter and the `as? Chest` fallback are exercised. The
  no-chests test (`:47-53`) also uses a real container (`BARREL`).
- **Contents AC is covered at the right depth.** `item material and custom name
  are read from the slot they occupy` (`:91-102`) writes an item into slot 5,
  asserts the 27-slot inventory, a `null` empty slot, the material, and the
  plain display name. Asserting the full 27-slot list (with nulls) pins the
  `List<ItemStack?>` representation, but that is the deliberate capture-side
  contract that 040 consumes, so it is not incidental structure.
- **`Region` normalization is covered.** `RegionTest.kt:27-41` covers fully
  inverted corners and per-axis mixed corners; equal corners (single block) are
  covered indirectly by the scanner's single-block region at
  `ChestScannerTest.kt:96`.
- **Trapped chest is exercised.** It appears as a captured block in both
  `several single chests...` and `only chest blocks...`; its contents follow the
  same `Chest` state path, so a separate trapped-chest contents test would
  duplicate coverage.
- **No excessive tests.** Eight tests across two classes, each mapped to an AC
  or a concrete branch; nothing is snapshot noise or implementation-shaped.
  The only removable assertion is the tautological `assertNull(source.selectionOf(player))`
  in issue 1.
- **`FaweSelectionSource` being untested is acceptable.** WorldEdit's static
  session manager is not bootstrapped in MockBukkit, so a meaningful unit test
  would need heavy faking; the adapter is thin and its null contract is better
  verified once 060 owns the pipeline. The caveat is issue 1: today the null
  contract has no production consumer and no test.
- **No dedicated `BlockPos.compareTo` test is fine.** It moved in this ticket,
  but `JsonExporterTest.kt:44-71` sorts positions `(0,0,0)`, `(0,0,1)`,
  `(0,1,0)`, `(1,0,0)` and asserts the resulting order, exercising x-then-y-then-z
  directly; a comparator-only test would duplicate that.
- **The `capture` helper itself is a reasonable fixture.** It removes
  `selectionOf`→`scan` boilerplate from six tests; the problem is only the
  null-selection test relying on its short-circuit (issue 1), not the helper's
  existence.

## Round 2

### Verdict

Ship. All five round-1 issues are resolved: the null-selection path now runs
through a production entry point, the clone and floor-shift behaviours are
pinned, the unloaded-chunk premise is explicit, and the MockBukkit double-chest
limitation is documented. The suite stays small (10 tests) and behaviour-shaped;
the only remaining items are two low-severity, optional refinements.

### Issues

#### 1. Negative-coordinate coverage only pins the x-axis (severity: low)
- Location: `src/test/kotlin/dev/cypdashuhn/uidesigner/capture/ChestScannerTest.kt:128-136`;
  `src/main/kotlin/dev/cypdashuhn/uidesigner/capture/ChestScanner.kt:15`.
- Problem: the test pins `x shr 4` (`-17 → -2`) but the scanner also uses
  `z shr 4`. A z-only refactor to `z / 16` would misidentify chunks for negative
  z and no test would fail. The two axes are symmetric, so this is a small gap,
  not a defect.
- Suggested fix: place the chest at `(-17, 0, -17)` and load chunk `(-2, -2)`
  in the existing test, or state explicitly that x coverage is representative.
  One coordinate change, no new test needed.

#### 2. `assertNotSame` in the adjacent-chest test is trivially true (severity: low)
- Location: `src/test/kotlin/dev/cypdashuhn/uidesigner/capture/ChestScannerTest.kt:125`.
- Problem: the two source stacks are distinct objects written to separate slots,
  and MockBukkit's `getContents()` returns a distinct mirror per slot, so the
  assertion passes even if `.clone()` is dropped. The clone pin already lives at
  `:105-106`, so this line is redundant rather than wrong.
- Suggested fix: drop it, or replace it with a check that earns its place, e.g.
  each captured list has exactly one non-null entry, or mutating one captured
  stack leaves the other chest's slot untouched.

### Non-issues

- **Round-1 issue 1 is closed.** `ChestCapture.capture` (`ChestCapture.kt:6-7`)
  is a real production consumer of `SelectionSource`, and
  `assertNull(ChestCapture.capture(FakeSelectionSource(null), player))`
  (`ChestScannerTest.kt:43`) asserts the null mapping in production code. The
  null vs empty distinction is now covered on both sides (`:43` null, `:51-54`
  empty list).
- **Round-1 issue 2 is closed, and the adjacent test still earns its place.**
  I confirmed in the MockBukkit 4.116.1 sources that `ChestStateMock.getBlockInventory()`
  returns `getInventory()` (`ChestStateMock.java:93-96`) and each `ChestStateMock`
  creates its own 27-slot `ChestInventoryMock` with no automatic double-chest
  linking, so the test cannot distinguish `blockInventory` from `inventory`. It
  still pins a real behaviour: two adjacent chest blocks yield two separate
  27-slot entries with independent contents, and it would start catching a
  shared-inventory mistake if MockBukkit gained real double-chest support
  (Paper's `blockInventory` still returns the half). The limitation is now
  recorded in `docs/architecture.md` and `docs/design.md`, exactly as the
  round-1 fix asked. Keeping the test is right.
- **Round-1 issue 3 is closed.** `ChestScannerTest.kt:105-106` mutates the
  captured stack and asserts the chest's stored amount is still `1`. MockBukkit
  `InventoryMock.getContents()` returns write-through `ItemStackMirror`s
  (`InventoryMock.java:321-326`, `ItemStackMirror.setAmount` delegates to the
  backing stack), so dropping `.clone()` would make this assertion fail. The
  clone is genuinely pinned.
- **Round-1 issue 4 is addressed for x, and `-17` is a good choice.** `-17 shr 4
  == -2` while `-17 / 16 == -1`; the test loads chunk `(-2, 0)`, so a `/`
  refactor would check the unloaded `(-1, 0)` and skip the chest, failing the
  test. Using `-17` rather than `-1` also avoids the `-1 / 16 == 0` coincidence
  with the `(0, 0)` chunk loaded in `setUp`. Only the missing z-axis is issue 1.
- **Round-1 issue 5 is closed.** `assertFalse(world.isChunkLoaded(1, 0))`
  (`ChestScannerTest.kt:142`) states the premise explicitly. `WorldMock.getBlockAt`
  reads a `blocks` map independent of `loadedChunks` (`WorldMock.java:457-467`)
  and does not load chunks, so the premise is real and the test covers the
  intended skip branch rather than an accident.
- **The `checkNotNull` helper is a strict fixture.** `ChestScannerTest.kt:146-147`
  turns an unexpected `null` into a test failure instead of silently passing,
  which is the right behaviour for the non-null cases.
- **No excessive tests.** Ten tests across `ChestScannerTest`/`RegionTest` for
  the ticket's branches. `adjacent chests`, `only chest blocks`, and `several
  single chests` overlap on the material filter but each covers a distinct
  scenario (adjacency, filtering, ordering); only the single assertion in issue 2
  is removable.
- **`FaweSelectionSource` remaining untested is still acceptable.** Unchanged
  from round 1: it needs a live WorldEdit session manager, the adapter is thin,
  and its null contract is now at least consumed and pinned through
  `ChestCapture`. A unit test here would be heavy faking for little value.
- **The 27-slot `items` assertion is contract, not incidental structure.**
  `ChestScannerTest.kt:100-103` pins the `List<ItemStack?>` capture shape that
  040 consumes, so it is deliberate and worth keeping.
