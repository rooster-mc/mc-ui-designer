# Architecture review — 090 (Use rooster-region BlockPos and consolidate model)

## Round 1
### Verdict
Ship with fixes. The package layout is coherent and the seams hold: `model/` and
`capture/RegionExt.kt` are gone, no source imports `dev.cypdashuhn.uidesigner.model`,
`export/` is Bukkit-free, and the dependency direction `capture`/`commands` →
`export` → (`dev.rooster.region`, kotlinx) has no cycle. The only work is doc
staleness the ticket did not finish: three docs still describe a `model` package.

### Findings
#### 1. `docs/architecture.md` package tree still says `JsonExporter` consumes `model`
- Location: `docs/architecture.md:24`
- Problem: `JsonExporter.kt  model -> JSON string/file` reads as the removed
  `model` package now that the tree no longer lists one and the payload
  (`UiChest`) sits in the same package. The prose below is fixed but this line
  was not, so the tree contradicts the seam prose at `:60-62`.
- Suggested fix: describe the real input, e.g.
  `UiChest -> JSON string/file (atomic write; single ordering authority)`.

#### 2. `docs/design.md` still names `model` as a pure layer
- Location: `docs/design.md:68`
- Problem: "Pure logic (model, JSON, grouping math) stays Bukkit-free where
  possible" lists a `model` layer that no longer exists. "grouping math" is also
  not Bukkit-free (it lives in `capture/DoubleChestGrouper` and reads `Region`/
  `Block`), so the sentence now misstates the purity boundary this ticket
  sharpened.
- Suggested fix: name the surviving pure layer, e.g. "Pure logic (JSON
  export/ordering) stays Bukkit-free where possible."

#### 3. The pipeline's agent prompts still tell workers to keep a `model`/`export` seam
- Location: `.opencode/agent/implementor.md:27`,
  `.opencode/agent/architecture.md:21`, `.opencode/agent/tester.md:24`
- Problem: these instructions are documentation the change invalidated. They
  direct future implementors to preserve "pure `model`/`export`", reviewers to
  police Bukkit leakage into `model`/`export`, and testers to expect a
  `model`/`export` package split — all pointing at a package that no longer
  exists. `AGENTS.md:87` was corrected in this ticket; these three were missed,
  so the operating manual is internally inconsistent and will keep re-teaching
  the old layout.
- Suggested fix: mechanically replace `model`/`export` with `export` in each of
  the three lines (no behavioural change). This is outside the ticket's stated
  doc list but is the same staleness class as the `AGENTS.md` edit; if deferred,
  it needs a named target, otherwise the next implementor is told to preserve a
  removed package.

### Non-findings
- **Package layout is coherent.** `src/main/.../model/` and
  `src/test/.../model/` are gone; `export/` holds only `UiChest.kt` and
  `JsonExporter.kt`; `capture/RegionExt.kt` is deleted. `grep -rn
  "uidesigner\.model" src/` returns nothing, so nothing still depends on the
  removed package.
- **`export/` remains Bukkit-free.** No `org.bukkit`, FAWE, or `com.sk89q`
  import appears under `export/`; the only non-Kotlin imports are the library
  `dev.rooster.region.BlockPos` and `java.nio.file` in `JsonExporter.kt`. The
  purity rule from `AGENTS.md:87` holds.
- **Placing `UiChest` in `export/` is acceptable, not awkward.** It makes
  `capture` (`DoubleChestGrouper.kt:3-5`, `UiDesignerCommand.kt`) depend on
  `export` for the payload, which is a mild arrow into the output package rather
  than a neutral model. There is no cycle (`export` imports nothing from
  `capture`/`commands`), the ticket justifies the placement, and the data flow
  diagram (`architecture.md:144-165`) still reads top-to-bottom. I would not
  introduce a package for a single type today. If an import feature lands and
  starts sharing `UiChest`, revisit whether `export` is still the right owner;
  that is a future ticket, not a 090 blocker.
- **Next-feature walkthrough is clean.** More container types extend `UiChest`/
  capture; another output format adds an exporter in `export/` reading the same
  payload; an importer would consume `UiChest` from `export/`. None require a
  rewrite or a package reshuffle.
- **The seam prose the ticket was meant to update is correct.**
  `architecture.md:50-59` now attributes world scoping to the library's
  `worldEditSelection()` and names the library `Region.blockAt(BlockPos)`;
  `:63-65` retargets the `ChestContent` note from `model/` to `export/`; `:80-84`
  calls `region.blockAt` "the library's member". No reference to `RegionExt` or
  the old "library `Region` has no `blockAt` helper" claim survives anywhere in
  the live docs (`design.md`, `architecture.md`, `data-format.md`, `fcp.md`,
  `manual-test.md`, `AGENTS.md`).
- **`AGENTS.md:87`** correctly drops `model` from the Bukkit-free rule; no
  further stale `model` reference remains there.
- **`docs/data-format.md:65`** still shows `data class BlockPos(...)`. I checked
  it and would leave it: the shape is source-identical to
  `dev.rooster.region.BlockPos`, the section is illustrative Kotlin for the
  schema, and `BlockPos` is `@Transient` and not part of the format. Flagging it
  would be churn, not staleness.
- **Tester (`docs/reviews/090/tester.md`) and correctness
  (`docs/reviews/090/correctness.md`) reports:** concur. Their non-findings are
  about coverage and behaviour, which are outside my scope; nothing there
  overlaps with the doc-staleness work above.

## Round 2
### Verdict
Ship. All three round-1 findings are resolved in `a320523`, the fixes introduce
no new staleness, and the live docs now agree with the shipped layout and with
each other. No source changed after round 1, so every round-1 non-finding still
holds.

### Findings
None.

### Non-findings
- **Round-1 finding 1 resolved.** `docs/architecture.md:24` now reads
  `UiChest -> JSON string/file (atomic write; single ordering authority)`. The
  tree no longer names the removed `model` package, and the line is consistent
  with the payload now listed at `:23` and the purity prose at `:60-62`.
- **Round-1 finding 2 resolved.** `docs/design.md:68` now reads "Pure logic
  (JSON export/ordering) stays Bukkit-free where possible." It drops both the
  dead `model` layer and the Bukkit-coupled grouping math, so the stated purity
  boundary matches `export/`.
- **Round-1 finding 3 resolved.** `.opencode/agent/implementor.md:27`,
  `.opencode/agent/architecture.md:21`, and `.opencode/agent/tester.md:24` now
  say `export` only. Combined with `AGENTS.md:87` ("Keep `export` free of
  Bukkit imports") and the architecture doc, the four places that describe the
  purity seam are now mutually consistent; no prompt still teaches a `model`
  package.
- **No new staleness introduced by the fixes.** The `docs/architecture.md`
  reword did not disturb the neighbouring entries or the seam prose; the
  `design.md` reword keeps the sentence's grammar and scope; the prompt edits are
  single-token substitutions that leave the surrounding rules intact.
- **No `model` package references remain in live docs.** A grep across
  `docs/architecture.md`, `docs/design.md`, `docs/data-format.md`, `docs/fcp.md`,
  `docs/manual-test.md`, `AGENTS.md`, and `.opencode/agent/*.md` finds only
  generic English uses: "capture-side model" (`architecture.md:67`), the
  "## Kotlin model" heading (`data-format.md:48`), and "## Orchestration model"
  (`AGENTS.md:37`). `RegionExt` appears in none of them, and
  `grep -rn "uidesigner\.model" src/` is still empty. Historical tickets and
  earlier-round review reports are correctly left untouched.
- **Tester's and correctness's round-2 reports:** concur. They confirm the same
  clean-tree state (`git diff a320523 -- src` is empty) and reach no finding that
  overlaps this scope.
