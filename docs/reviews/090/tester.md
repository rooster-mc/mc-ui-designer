# Tester review — 090 (Use rooster-region BlockPos and consolidate model)

## Round 1
### Verdict
Ship. The refactor is import/package mechanical plus one deletion of a
redundant branch, and the existing suites still pin the same behaviour through
the new types. I found no missing, excessive, or brittle test, and no new
harness limit that is not already tracked.

### Findings
None.

### Non-findings
- **The moved `export/UiChestTest.kt` is still the right test.** Only its
  package line changed (`UiChestTest.kt:1`); it still round-trips a chest
  through `DesignJson`. The `@Transient position` exclusion is pinned
  separately by `JsonExporterTest.kt:248-283`, whose snapshot has no `position`
  field while the fixtures set one (`JsonExporterTest.kt:27,32`). No new case is
  warranted for a package move.
- **`BlockPos` ordering coverage survives the swap.** `JsonExporter` sorts by
  `BlockPos` (`JsonExporter.kt:35`) and `JsonExporterTest.kt:40-67` feeds four
  positions that pin x, then y, then z priority in the right order
  (`(0,0,0) < (0,0,1) < (0,1,0) < (1,0,0)`). The library's own
  `BlockPosTest` owns direct `compareTo` cases, so deleting the local value type
  loses no coverage.
- **`Region.blockAt` needs no dedicated test at its new home.** The library
  member is exercised through `DoubleChestGrouper`'s geometry path
  (`DoubleChestGrouper.kt:77` reached from
  `DoubleChestGrouperTest.kt:108-124,220-246`) and through the command's
  `ChestNamer.nameOf(region.blockAt(position))` (`UiDesignerCommand.kt:140`,
  `UiDesignerCommandTest.kt:91-121`). A direct test would only re-assert
  MockBukkit, the same conclusion 060/080 reached for the identical helper.
- **Dropping the `FaweSelectionSource` cross-world guard leaves no new
  automatable gap.** The guard was a duplicate of the library's
  `worldEditSelection()` world check
  (`rooster-region/worldedit/.../Adapter.kt:37-38`), and the live WorldEdit
  session it needs is still unavailable to the harness (FAWE is `compileOnly`).
  The behaviour remains owned end-to-end by `MT-005` in `docs/manual-test.md`,
  which switches worlds without re-selecting and expects no selection; no new
  entry is needed. Worth recording for the orchestrator: the library's own
  `AdapterTest` does *not* test `worldEditSelection`, so `MT-005` is the only
  regression protection for the world-scoping and should stay `unverified`
  until the live check is actually run.
- **The `DoubleChest` holder path is still a harness limit, already tracked.**
  `DoubleChestGrouperTest.kt:348-374` reaches the holder branch through a
  hand-built `DoubleChestInventory` proxy; MockBukkit cannot form a linked
  double chest. This ticket does not touch that logic and `MT-003` already
  records it.
- **No brittle or excessive tests introduced.** The diff touches tests only by
  re-pointing imports/packages (`ChestScannerTest.kt:3`,
  `DoubleChestGrouperTest.kt:3-5`, `UiDesignerCommandTest.kt:6-13`,
  `JsonExporterTest.kt:3`); no assertion changed, no test was added or removed,
  and nothing snapshots library internals or incidental structure. The
  acceptance criteria "no `model` package / imports `dev.rooster.region.BlockPos`"
  are compile-enforced and do not need a test.
