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

## Round 2
### Verdict
Ship. No source or test file changed after round 1 (`git diff a320523 -- src`
is empty), so every round-1 non-finding holds verbatim. The round-2 deltas are
doc and agent-prompt wording only, and they read cleanly: no broken phrasing,
no leftover package-`model` wording, no stale cross-references.

### Findings
None.

### Non-findings
- **Round-1 non-findings still hold.** The changed Kotlin tree is identical to
  the state reviewed in round 1 — imports all used, package declarations match
  paths, `model/` and `capture/RegionExt.kt` gone with no empty directory or
  dangling reference, and `FaweSelectionSource` self-explanatory without the
  removed guard comment. Nothing in the doc/prompt edits touches a source file,
  so none of that reasoning needs revisiting.
- **The package-tree edit reads cleanly.** `docs/architecture.md:22-24` now lists
  `UiChest.kt` under `export/` above `JsonExporter.kt`, and the description
  `UiChest -> JSON string/file (atomic write; single ordering authority)` names
  the real input instead of the removed `model` layer. The tree stays aligned
  with the actual package and with the seam prose below it.
- **The purity sentences read cleanly.** `docs/design.md:68` ("Pure logic (JSON
  export/ordering) stays Bukkit-free where possible.") and the
  `docs/architecture.md:55-64` reword keep their grammar and scope while naming
  the surviving pure layer; `export` is used consistently as a package name.
- **The agent-prompt edits are single-token and grammatical.**
  `.opencode/agent/implementor.md:27` ("pure `export`"),
  `.opencode/agent/architecture.md:21` ("No Bukkit leakage into `export`"), and
  `.opencode/agent/tester.md:24` ("The `export` package should be Bukkit-free")
  each read naturally and leave the surrounding rules intact.
- **No leftover package-`model` wording anywhere I looked.** A grep over
  `docs/architecture.md`, `docs/design.md`, `docs/data-format.md`,
  `docs/fcp.md`, `docs/manual-test.md`, `AGENTS.md`, and `.opencode/agent/*.md`
  finds only generic English uses — "capture-side model"
  (`docs/architecture.md:67`), "## Kotlin model" (`docs/data-format.md:48`),
  "## Orchestration model" (`AGENTS.md:37`) — none of which refers to the
  removed package. `RegionExt` appears in none of them.
- **Prior round-2 reports.** Concur with `docs/reviews/090/tester.md`,
  `docs/reviews/090/correctness.md`, and `docs/reviews/090/architecture.md`
  `## Round 2`: all three confirm the same clean-tree state and report no
  finding in my scope; the architecture report owns the doc-staleness
  resolution, which I do not re-report.
