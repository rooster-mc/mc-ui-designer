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
