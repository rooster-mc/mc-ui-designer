# Readability review — 090 (Use rooster-region BlockPos and consolidate model)

## Round 1
### Verdict
Ship. The refactor reads cleanly: every changed import is used, package
declarations match their paths, the removed guard comment leaves
`FaweSelectionSource` self-explanatory, and no empty `model/` directory or
dangling reference survives. No readability findings.

### Findings
None.

### Non-findings
- **Imports are clean and all used.** `grep -rn "uidesigner\.model\|capture\.blockAt\|model\.BlockPos" src/`
  returns nothing, and no changed file carries an unused import. The moved
  `export/UiChest.kt` gained exactly the one import it needs
  (`dev.rooster.region.BlockPos`, line 3); `JsonExporter.kt` dropped
  `DesignJson`/`UiChest`/`UiRow` imports because they are same-package
  (`export/JsonExporter.kt:16,32,50`), and its remaining `BlockPos` import is
  used at `:47`. `commands/UiDesignerCommand.kt:15` uses `BlockPos` at `:139`.
  The removed `capture.blockAt` import is gone from the command file.
- **Import ordering is ktlint-clean to the eye.** The `dev.cypdashuhn` →
  `dev.jorel` → `dev.rooster` ordering in `UiDesignerCommand.kt:3-16` and the
  `export` → `dev.rooster` ordering in `DoubleChestGrouper.kt:3-7` and
  `UiDesignerCommandTest.kt:6-14` are lexicographic; the tree is stated green
  and I see nothing that contradicts it.
- **Package declarations match paths.** `export/UiChest.kt:1` and
  `export/UiChestTest.kt:1` are both `dev.cypdashuhn.uidesigner.export`; the
  moved test needs no imports for `UiChest`/`UiRow`/`UiSlot`/`DesignJson`
  because they share its package (`UiChestTest.kt:28`). `capture/` and
  `commands/` files now consistently import the payload from `export`.
- **No file-hygiene residue.** `find src -type d -empty` returns nothing; the
  filesystem has no `model/` directories under either `src/main` or `src/test`,
  and `src/main/.../model/BlockPos.kt` and `capture/RegionExt.kt` are deleted
  with no dangling files or mixed package names.
- **`FaweSelectionSource` is still self-explanatory without the removed guard
  comment.** The object is 13 lines (`capture/FaweSelectionSource.kt:8-13`): the
  null-elvis at `:10` and the `toRegion(player.world)` call at `:11` read
  directly, and the world-scoping rationale the old comment carried now lives in
  the library function the file delegates to. Adding a comment here would
  restate the code, which the no-comments convention forbids.
- **Comments in touched files are warranted "why" comments, not stale.** The
  only comments in any file the diff touches are
  `UiDesignerCommandTest.kt:163` (why the inventory is filled after naming) and
  `:578` (why the empty data folder is safe); both explain a non-obvious reason,
  neither was changed by this ticket, and both still hold.
- **The moved `export/UiChest.kt` reads as one unit.** Its package line and the
  new `BlockPos` import sit at the top (`:1-6`) and the payload declarations are
  unchanged, so the move costs a reader nothing. `JsonExporter.kt` now resolves
  `UiChest`/`UiRow` locally rather than through a cross-package import, which is
  slightly less indirection, not more.
- **Prior reports.** Concur with `docs/reviews/090/tester.md` and
  `docs/reviews/090/correctness.md`; their non-findings concern coverage and
  behaviour, outside my scope, and add no readability work. The doc-staleness
  findings in `docs/reviews/090/architecture.md` are architecture-owned; I do
  not re-report them.
