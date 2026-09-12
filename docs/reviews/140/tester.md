# tester review — 140 (Abort export on a double chest clipped by the selection)

## Round 1

### Verdict
The new coverage is well aimed: holder and geometry clipping are tested
separately, both partner-location cases are asserted, and the "no export"
outcome is checked directly. Two gaps remain — fail-closed is only proven when
nothing else in the selection is exportable, and the unloaded-chunk wording is
asserted in a loaded chunk — so ship with those fixes.

### Findings

#### 1. Fail-closed is only tested when the selection has nothing exportable
- Location: `src/test/kotlin/dev/cypdashuhn/uidesigner/commands/UiDesignerCommandTest.kt:156-185`
- Problem: The only `save`-abort test puts just the clipped half in the
  selection, so `grouped.chests` is empty. The ticket's core concern is *silent
  truncation*: a selection that also holds a valid chest must still write
  nothing. A regression that aborts only when no chest survives (e.g.
  `if (grouped.chests.isEmpty() && grouped.clipped.isNotEmpty())`) or that
  exports the valid chests before reporting the clip would still pass this test.
  Relatedly, the multi-clip case is only ever fed to `clippedChestsMessage`
  by hand (`UiDesignerCommandTest.kt:218-235`); `DoubleChestGrouper.group`'s
  accumulation of several `clipped` entries is never exercised.
- Suggested fix: Add a command test with one valid chest (single or a complete
  double) plus a clipped half in the same selection, asserting
  `SaveOutcome.ClippedChests`, `exporterCalls == 0` and no file on disk. Put two
  clipped halves on opposite sides in the same setup to pin that `group`
  accumulates them (assert both `ClippedHalf`s via the outcome), so the
  "several are clipped" clause is covered end-to-end rather than only through
  the message formatter.

#### 2. The "not captured" command test asserts the unloaded-chunk wording without an unloaded chunk
- Location: `src/test/kotlin/dev/cypdashuhn/uidesigner/commands/UiDesignerCommandTest.kt:203-216` (setup via `clippedHalfChest`, `:621-628`)
- Problem: The chest is at `(0, 0, 0)` and the region is `(0,0,0)-(1,0,0)`;
  chunk `(0,0)` is loaded and `(1,0,0)` is simply air. The test reaches
  `partnerInsideSelection = true` by absence and then asserts "chunk is not
  loaded" for a chunk that is in fact loaded. It never exercises the production
  cause — the partner lies inside the bounds but was skipped by
  `ChestScanner`/`loadedBlockPositions` because its chunk is unloaded — so it
  would not catch a regression in that interaction. `ChestScannerTest` already
  demonstrates the unloaded-chunk setup is automatable with MockBukkit.
- Suggested fix: Move the half to `(15, 0, 0)` facing NORTH (partner
  `(16, 0, 0)`) and make the region `(15,0,0)-(16,0,0)`. Chunk `(1,0)` is
  unloaded by default in `setUp`, so the scan drops the partner and the
  "chunk is not loaded" branch is reached for the real reason. This gives
  end-to-end coverage of scan-skip → grouper classification → message.

### Non-findings
- Holder vs geometry paths are genuinely separated, not two names for one
  branch: `fakeChest` leaves block data `SINGLE` (`DoubleChestGrouperTest.kt:454-462`),
  so only the `DoubleChest` holder can supply a partner, while `geometryChest`
  sets `LEFT`/NORTH with no holder (`:444-452`). The four clipped tests
  (`:266-332`) therefore cover both paths × both partner-location cases as the
  ticket asked.
- The holder path is synthetic — a reflective `DoubleChest`/`DoubleChestInventory`
  proxy plus `FakeChestState` (`DoubleChestGrouperTest.kt:416-442`) because
  MockBukkit cannot link a real `DoubleChest` — but it does execute the holder
  branch, so this is a covered path, not an untested one. MT-011
  (`docs/manual-test.md:22`) correctly gates the real-server check; no further
  manual entry is needed for it.
- `partnerInsideSelection` boundary is covered: the "inside" tests place the
  partner exactly on `maxX`, exercising the inclusive `containsBlock`.
- The present-but-mismatched neighbour still resolves to a single and is
  asserted (`a single chest is not re-merged by a mismatched adjacent half`,
  `DoubleChestGrouperTest.kt:120-140`).
- Layer choice is right: `clippedChestsMessage` is tested as a pure function
  (`UiDesignerCommandTest.kt:218-235`), command wiring through
  `unregisteredCommand` (outcome + no file) and `MockCommandAPIPlugin`
  (player-visible message). No Bukkit is needed for the message unit test.
- Unchanged behaviour is still pinned: single chests and complete doubles still
  export (`UiDesignerCommandTest.kt:121-154`, plus the existing grouper tests).
- No excessive or brittle tests found. Assertions use substrings for
  user-facing text and data-class equality for outcomes; the four clipped
  grouper tests are the requested matrix, not duplication.
