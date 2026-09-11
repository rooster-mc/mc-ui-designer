# Tester review — 080 (Adopt rooster-region for capture)

## Round 1
### Verdict
Ship with one fix. The swap is behaviour-preserving at the test level: the three
`region(...)` helpers were mechanically re-pointed at `dev.rooster.region.Region`,
`RegionTest` was correctly deleted (the library's `RegionTest`/`AdapterTest` own
that coverage), and the existing capture/command suites still exercise the same
branches through the new type. The one acceptance criterion with no verification
at all is the shaded-jar joml exclusion, which the JUnit harness cannot see and
which is not recorded in `docs/manual-test.md`.

### Findings

#### 1. The "shaded jar contains no `org/joml/**`" criterion is unverified
- Location: `build.gradle.kts:129-133`; acceptance criterion at
  `docs/tasks/080-adopt-rooster-region.md:40`; `docs/manual-test.md` (no 080 row).
- Problem: the only thing keeping joml out of the artifact is
  `exclude("org/joml/**")` (`build.gradle.kts:132`). Nothing fails if that line is
  dropped: the JUnit suite runs before `shadowJar` and cannot inspect the
  artifact, and `just build` succeeds either way. The dependency is real, not
  hypothetical — `rooster-region/core/build.gradle.kts:21` declares
  `api("org.joml:joml:1.10.9")`, so joml is on the runtime classpath and would be
  bundled without the exclude. The criterion is therefore unverified, and the
  failure it guards (shipping a second joml next to Paper's) is exactly the kind
  of runtime classpath clash that only shows up on a real server. There is a
  precedent for the right fix in this family: `rooster-region/core/build.gradle.kts:39-94`
  `verifyCoreDependencies`.
- Suggested fix: add a cheap Gradle verification rather than a manual row, e.g.
  a `doLast` on `shadowJar` (or a task wired into `check`/`build`) that opens
  `archiveFile` and `check(...)`s that no entry starts with `org/joml/`. If the
  team prefers not to add build logic, record a `docs/manual-test.md` row
  (ticket 080) with the explicit check
  `unzip -l build/libs/*.jar | grep -c 'org/joml/'` expected to be `0`; the
  harness cannot exercise this path, so it must be tracked somewhere.

### Non-findings
- **The test changes are the correct minimum.** `ChestScannerTest.kt:165-169`,
  `DoubleChestGrouperTest.kt:423-427` and `UiDesignerCommandTest.kt:585-589` only
  re-point the existing fixtures at the library's `Region(Location, Location)`;
  no assertions changed, so the "existing behaviour is unchanged" criterion is
  still pinned by the same cases as before. No new tests are warranted for a
  type swap.
- **Deleting `RegionTest` is right, not a coverage loss.** The two deleted cases
  (inverted corners, mixed per-axis corners) are covered by
  `rooster-region/core/src/test/.../RegionTest.kt:20-32`, and the library also
  pins size/volume/chunk-index semantics. Keeping a local copy would duplicate
  the library's suite and re-couple this repo to internals the ticket is removing.
- **`FaweSelectionSource` staying untested is still acceptable, and the harness
  limit is already tracked.** It is now a one-liner over
  `player.worldEditSelection()?.toRegion(player.world)`; the library's
  `AdapterTest` covers `toRegion` for both `World` and `Player`
  (`AdapterTest.kt:41-84`), and the only uncovered piece, the live WorldEdit
  session lookup, cannot run under MockBukkit (FAWE is `compileOnly`,
  `build.gradle.kts:44-45`, and absent from the test classpath). This is the same
  conclusion 030/060 reached. The real FAWE selection → `Region` path is already
  owned by `MT-001` in `docs/manual-test.md`, which does the full in-game
  `/uidesigner save` walkthrough; 080 does not need a duplicate row for it.
- **The `DoubleChest` holder path remains a harness limit, already recorded.**
  `DoubleChestGrouperTest` reaches the holder branch only through the hand-built
  `DoubleChestInventory` proxy (`DoubleChestGrouperTest.kt:348-374`); MockBukkit
  cannot form a linked double chest, so the production holder route is only
  covered in-game. `MT-003` already tracks this for 040, and this ticket does not
  change that logic, so no new entry is needed.
- **No brittle or excessive tests introduced.** The new helpers assert nothing
  themselves and use the library's public constructor; there is no snapshotting
  of library internals and no test that merely re-asserts `rooster-region`. The
  mild duplication of a four-line `region(...)` helper across three suites is
  acceptable — extracting a shared fixture is a readability preference, not a
  test-quality problem.

## Round 2
### Verdict
Ship with one fix. The round-1 joml finding is resolved by a real build-time
check (`build.gradle.kts:134-149`), and the mechanical test changes are
unchanged and still correct. The only remaining test-quality gap is the new
cross-world stale-selection guard in `FaweSelectionSource`: the harness cannot
exercise it (FAWE is `compileOnly`) and it is not recorded in
`docs/manual-test.md`, so the fix for correctness finding 1 is currently
untracked.

### Findings

#### 1. The new cross-world selection guard has no test and no manual-test entry
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/capture/FaweSelectionSource.kt:10-14`;
  `docs/manual-test.md` (no 080 row).
- Problem: this commit adds a production branch the suite cannot reach:
  `selectionOf` now returns `null` when `selection.world?.name` differs from the
  player's world (`FaweSelectionSource.kt:13`). `FaweSelectionSource` calls the
  library's `worldEditSelection()`, which needs a live WorldEdit session, and
  FAWE is `compileOnly` and absent from the test classpath
  (`build.gradle.kts:47-57`); the `SelectionSource` fake used by
  `ChestScannerTest`/`UiDesignerCommandTest` bypasses it entirely, so no test
  exercises the guard. Correctness's round-1 fix direction included tracking this
  path in `docs/manual-test.md`, but the commit did not touch that file (checked
  `git show 6dc2450 --name-only`), and none of `MT-001`–`MT-004` switches worlds
  mid-session. The guard therefore has no regression protection and no recorded
  manual check — if it is deleted or inverted, the suite stays green. (I am not
  re-reporting correctness finding 1, which is fixed; this is only the missing
  verification for the fix.)
- Suggested fix: add a `docs/manual-test.md` row owned by 080 (e.g. `MT-005`):
  "Select a region in world A, teleport to world B without re-selecting, run
  `/uidesigner save`, and confirm the plugin reports no selection (does not scan
  world B at A's coordinates)." A new row is better than folding it into `MT-001`,
  which never changes worlds. Do not attempt a MockBukkit test here; the
  WorldEdit session is exactly what this harness cannot provide.

### Non-findings
- **Round-1 finding 1 is resolved.** `build.gradle.kts:134-149` adds a `doLast`
  on `ShadowJar` that opens `archiveFile` and `check(...)`s that no entry starts
  with `org/joml/`, which is the automated form I asked for. It runs as part of
  `just build` (`tasks.build` depends on `shadowJar`, `build.gradle.kts:152-154`)
  and the failure message names the count and sample entries. No manual row is
  needed for joml.
- **The test suite is unchanged and still the right minimum.** `6dc2450` did not
  add or alter any test beyond the round-1 mechanical `region(...)` re-points
  (`ChestScannerTest.kt:165-169`, `DoubleChestGrouperTest.kt:423-427`,
  `UiDesignerCommandTest.kt:585-589`); no assertions moved, so "existing
  behaviour is unchanged" remains pinned by the same cases.
- **`RegionExt.blockAt` needs no dedicated test.** The new extension
  (`RegionExt.kt:7-8`) is a one-line `world.getBlockAt(...)` delegate; a direct
  test would only re-assert MockBukkit. It is exercised at all three call sites
  through the existing grouper and command suites, the same conclusion 060's
  tester reached for the identical helper.
- **`settings.gradle.kts`'s sibling-check is build config, not a test gap.** It
  fails fast with a named path before settings evaluation; there is nothing for
  the JUnit harness to assert, and `just build` green is the right gate.
- **No new brittleness or excess.** The added build check inspects the artifact
  by entry prefix (not a hash or a fixed entry list), and no test snapshots the
  library type or the shaded jar's contents.
