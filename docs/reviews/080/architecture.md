# Architecture review — 080 (Adopt rooster-region for capture)

## Round 1
### Verdict
Ship with fixes. The swap is the right structural move: FAWE stays behind
`SelectionSource`, `model`/`export` remain library- and Bukkit-free, and no
WorldEdit session code survives in this repo. Four items need attention: an
undocumented hard dependency on the sibling checkout, a stale "Single Gradle
module" claim, a small seam regression where 060's `Region.blockAt`
centralisation is undone by three inlined lookups, and a design decision that
drops the toolchain rationale it reverses.

### Findings

#### 1. The composite build hard-couples the build to an undocumented sibling checkout
- Location: `settings.gradle.kts:7`; `docs/design.md:39-44`;
  `docs/architecture.md:41-44`.
- Problem: `includeBuild("../rooster-region")` is unconditional and relative. A
  fresh clone or CI checkout without a sibling `rooster-region` fails during
  settings evaluation, before any project code runs, with a generic "included
  build ... does not exist" error. The docs describe the composite build but
  never state the prerequisite, so nothing tells a new contributor or CI where
  the sibling must live. (The workflow's worktree layout
  `../mc-ui-designer--<id>` happens to keep `../rooster-region` a valid sibling,
  so worktree isolation is fine; clones outside the shared parent are not.)
- Suggested fix: state the requirement in `docs/design.md` (and/or the
  `AGENTS.md` stack/commands section): "requires `../rooster-region` checked out
  beside this repo." Optionally guard it in `settings.gradle.kts` before
  `includeBuild` (`check(file("../rooster-region").isDirectory) { "..." }`) so
  the failure names the missing sibling. No need to move to a published
  artifact; the ticket chose composite and the library README endorses it.

#### 2. `architecture.md` still calls this a single Gradle module
- Location: `docs/architecture.md:3` ("Single Gradle module.").
- Problem: the build is now a composite of `UiDesigner` plus the included
  build's `:core` and `:worldedit`. The opening line is the first thing a reader
  sees and now misstates the build topology; it contradicts the seam bullet at
  `docs/architecture.md:41-44` that describes the composite.
- Suggested fix: rewrite to something like "One Gradle module in this repository
  (`UiDesigner`); the region/selection types come from the `rooster-region`
  composite build (`:core`, `:worldedit`)." Keep the package tree that follows
  as this repo's own.

#### 3. Three inlined `region.world.getBlockAt(...)` sites undo the `Region.blockAt` seam 060 established
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/capture/DoubleChestGrouper.kt:46,77`;
  `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/UiDesignerCommand.kt:140`;
  scanner variant at `src/main/kotlin/dev/cypdashuhn/uidesigner/capture/ChestScanner.kt:17`;
  `docs/architecture.md:55-56,80-81`.
- Problem: 060 deliberately centralised the `BlockPos -> world.getBlockAt`
  translation in `Region.blockAt` (see `docs/reviews/060/architecture.md`
  finding 2), and 080 deletes that helper, leaving the same coordinate-unpack
  expression at three `BlockPos` call sites across two packages. The library
  `Region` owning no `blockAt` is not a reason to let the rule drift across
  `capture/` and `commands/`: the next change to block lookup (bounds/chunk
  guard, a different accessor) must touch all three, and `commands/` again
  reaches into `Region.world`. The scanner's int-triple loop is a genuinely
  different shape and can stay inline (consistent with 060's reasoning that a
  per-block `BlockPos` allocation there buys nothing).
- Suggested fix: add a one-line extension in `capture/` (e.g.
  `capture/RegionExt.kt`, or beside `ChestContent.kt`):
  `internal fun Region.blockAt(position: BlockPos): Block = world.getBlockAt(position.x, position.y, position.z)`,
  and use it at the three `BlockPos` sites. Update
  `docs/architecture.md:55-56,80-81` to name the capture-local `Region.blockAt`
  extension as the lookup seam rather than inlining the expression.

#### 4. The `rooster-region` decision reverses the toolchain-coupling objection without recording how it was resolved
- Location: `docs/design.md:39-44` (new decision) versus the deleted text and
  `docs/design.md:47-50`.
- Problem: the previous decision refused `rooster-*` because pulling it in
  "couples us to older Paper/JDK toolchains." That rationale is gone, but the
  new decision is silent on the delta that remains: `rooster-region`'s modules
  compile against `paper-api:1.21.4` with `jvmToolchain(21)`
  (`rooster-region/core/build.gradle.kts`, `worldedit/build.gradle.kts`) while
  this plugin targets Paper `26.2` / Java `25`. The coupling is acceptable (the
  used `Region`/`World`/`Location` surface is stable), but a reader cannot tell
  whether it was considered or overlooked, and the non-obvious joml handling
  (`build.gradle.kts:129-133` excludes `org/joml/**` because Paper supplies it)
  is documented only in a code comment.
- Suggested fix: one sentence in the decision: the library targets Paper 1.21.4
  / Java 21 but the used API surface is stable on 26.2, and its `api` joml is
  excluded from the shaded jar because Paper provides it at runtime. Keep the
  code comment.

### Non-findings
- **`SelectionSource` still isolates FAWE and stays testable.** It imports only
  `dev.rooster.region.Region` (core, WorldEdit-free), and the WorldEdit adapter
  (`toRegion`, `worldEditSelection`) is confined to `FaweSelectionSource.kt`
  behind the lazy delegating wrapper (`UiDesignerPlugin.kt:41-45`). The seam the
  ticket promised to keep is intact.
- **`model`/`export` remain pure.** No `dev.rooster` or Bukkit imports in
  `model/` or `export/` (`UiChest.kt`, `BlockPos.kt`, `JsonExporter.kt`), and no
  WorldEdit session code remains anywhere in `src/main`. The library type is
  confined to `capture/`, `commands/`, and the plugin entry point, all of which
  were already Bukkit-coupled.
- **Dependency scopes are right for a shaded plugin.** `implementation` for both
  library modules is correct (no downstream compile consumers), the composite
  substitution maps `rooster-region`→`:core` and `rooster-region-worldedit`→
  `:worldedit` as the library README prescribes, and `rooster-region-worldedit`'s
  `api(core)` makes the explicit second declaration redundant but harmless.
- **Concur with tester finding 1 / correctness's agreement.** The joml exclusion
  is sound but unverified by the suite; that is a verification gap owned by
  tester/correctness, and I add nothing to it.
- **Concur with correctness finding 1.** The cross-world stale-selection
  regression is real and its fix is a `rooster-region` follow-up plus a
  manual-gate entry; not an architecture finding to duplicate.
- **The deleted `RegionTest` and the mechanical test re-points leave no dangling
  references.** The library now owns `Region` semantics, which is the point of
  the ticket; nothing in this repo still expects a local `Region` type.

## Round 2
### Verdict
Ship. All four round-1 findings are resolved in `6dc2450`: the composite build
now fails fast with a named path when the sibling is missing and `design.md`
records the prerequisite; `architecture.md` describes the composite topology;
`RegionExt.blockAt` restores the `BlockPos` lookup seam and the docs follow it;
and the `design.md` decision records the Paper/JDK delta and the joml exclusion.
I found no new architecture or doc-staleness issues.

### Findings
#### No new findings.

### Non-findings
- **Round-1 finding 1 (undocumented sibling checkout) is resolved.**
  `settings.gradle.kts:7-10` checks `rootDir.resolve("../rooster-region")` and
  fails settings evaluation with the canonical path in the message, and
  `docs/design.md:41-42` states the prerequisite ("requires `../rooster-region`
  checked out beside this repository"). The path still resolves correctly under
  the workflow's `../mc-ui-designer--<id>` worktree layout, so isolation is not
  affected.
- **Round-1 finding 2 ("Single Gradle module") is resolved.**
  `docs/architecture.md:3-5` now says "One Gradle module in this repository
  (`UiDesigner`); the region and WorldEdit-selection types come from the
  `rooster-region` composite build (`:core`, `:worldedit`)", which matches the
  seam bullet at `:44-47`.
- **Round-1 finding 3 (inlined `getBlockAt` drift) is resolved.**
  `capture/RegionExt.kt:7-8` adds `internal fun Region.blockAt(position: BlockPos)`,
  and the three `BlockPos` sites use it (`DoubleChestGrouper.kt:46,77`,
  `UiDesignerCommand.kt:141`); the scanner's int-triple loop stays inline as
  intended. The docs keep pace: the package tree lists `RegionExt.kt`
  (`docs/architecture.md:18`), the seam bullet explains the extension
  (`:59-62`), the grouper contract names it (`:85`), and the data-flow diagram
  shows `region.blockAt(position)` (`:159`). No remaining `region.world.getBlockAt`
  reference for a `BlockPos` lookup in source or docs.
- **Round-1 finding 4 (toolchain/joml rationale) is resolved.**
  `docs/design.md:42-45` records that the library targets Paper 1.21.4 / Java 21
  while this plugin targets 26.2 / Java 25, that the exposed
  `Region`/`Location`/`World` surface is accepted as stable, and that its `api`
  joml is excluded because Paper supplies it.
- **Concur with tester round-2 finding 1.** The new cross-world guard
  (`FaweSelectionSource.kt:10-14`) is not reachable under MockBukkit (FAWE is
  `compileOnly`) and `docs/manual-test.md` still has no 080 row; that is a
  tester/process gap for the guard's regression protection, and I add nothing to
  it.
- **The cross-world guard lives on the right side of the library boundary.**
  It sits in `FaweSelectionSource` — the one file the ticket reserves for adapter
  code — rather than leaking WorldEdit world logic into `capture/` or `commands/`.
  The library's `worldEditSelection()` returning the session's selection world is
  a general-purpose behaviour, so no `rooster-region` change or follow-up ticket
  is required for this fix to stand; I do not consider the guard a workaround
  awaiting removal.
- **Concur with correctness round-2.** The guard's world-name comparison and the
  `RegionExt.blockAt` restore are behaviour-preserving; no architecture change
  follows from those analyses.
