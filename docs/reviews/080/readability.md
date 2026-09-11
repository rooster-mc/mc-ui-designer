# Readability review — 080 (Adopt rooster-region for capture)

## Round 1
### Verdict
Ship. The swap is small, mechanical and easy to follow: the deleted local
`Region` is replaced by the library type, `FaweSelectionSource` collapses to a
one-liner, and the only edits elsewhere are re-pointed types and three
coordinate unpackings. I have no readability findings of my own.

### Findings
None.

### Non-findings
- **`FaweSelectionSource` reads cleanly after the collapse.**
  `src/main/kotlin/dev/cypdashuhn/uidesigner/capture/FaweSelectionSource.kt:8-10`
  is now a single expression, the two `dev.rooster.region.worldedit` extension
  imports (`:4-5`) make the call self-describing, and no leftover
  `IncompleteRegionException`/`BlockPos` scaffolding remains.
- **The scanner's new bounds read as well as the old ones.**
  `ChestScanner.kt:13-15` uses `region.minX..region.maxX` etc., which is the
  library's public surface and needs no local helper to interpret.
- **Concur with architecture finding 3 (the three inlined
  `region.world.getBlockAt(position.x, position.y, position.z)` sites).** This is
  the one place the diff is *less* readable than before — the coordinate
  unpacking is duplicated at `DoubleChestGrouper.kt:46,77` and
  `UiDesignerCommand.kt:140` where 060 had a named `Region.blockAt` — but
  architecture already reported it with the right fix (a capture-local
  `Region.blockAt` extension). I add nothing and do not re-report it.
- **The three test `region(...)` helpers are not worth extracting.** They look
  duplicated (`ChestScannerTest.kt:165`, `DoubleChestGrouperTest.kt:423`,
  `UiDesignerCommandTest.kt:585`) but have different shapes: the grouper helper
  takes only x/z and pins y=0, while the other two take all six bounds. A shared
  fixture would have to absorb both forms and would couple three otherwise
  independent suites for ~8 lines. Leaving them per-suite is the clearer choice;
  tester's "acceptable duplication" note stands.
- **The one new comment is a `why`, not a `what`.**
  `build.gradle.kts:131` explains why joml is excluded (Paper supplies it at
  runtime), which a reader cannot infer from `exclude("org/joml/**")` alone. It
  conforms to the repo's no-comments-unless-why rule; no other comments were
  added.
- **No dead code or dangling references.** `grep` finds no remaining
  `Region.of`, `region.blockAt`, `.min.`/`.max.`, or
  `dev.cypdashuhn.uidesigner.capture.Region` in `src/`; the deleted `Region.kt`
  and `RegionTest.kt` leave no imports behind in the files that referenced them.
- **Import ordering and formatting are clean.** Every new import sits in
  alphabetical position (`dev.rooster` after `dev.jorel`, before `net.kyori`), no
  changed line exceeds the 100-column limit, and the trailing-comma convention
  matches `.editorconfig`.

## Round 2
### Verdict
Ship with one small fix. The round-1 findings are resolved and the new code —
`RegionExt.blockAt`, the `FaweSelectionSource` world guard, the
`settings.gradle.kts` sibling check and the `shadowJar` joml verification — reads
clearly and follows the repo's comment/format rules. The one thing I would change
is the duplicated `../rooster-region` literal in `settings.gradle.kts`, so the
check and the `includeBuild` cannot drift apart.

### Findings

#### 1. The sibling path is written twice in `settings.gradle.kts`
- Location: `settings.gradle.kts:7` and `settings.gradle.kts:12`.
- Problem: `val roosterRegionDir = rootDir.resolve("../rooster-region")` (`:7`)
  exists solely to validate the directory that `includeBuild("../rooster-region")`
  (`:12`) then re-specifies as a raw literal. A reader has to confirm the two
  strings are the same path, and nothing forces them to stay in sync: change one
  and the check can guard a different directory than the build includes, or the
  include can point at a path the check never validated.
- Suggested fix: pass the already-resolved value to the include —
  `includeBuild(roosterRegionDir) { ... }` — so there is a single source of truth
  for the sibling location (Gradle's `includeBuild` accepts a `File`). The
  `check` and its named error message stay as they are.

### Non-findings
- **`RegionExt.kt` is the right size and shape.**
  `capture/RegionExt.kt:7-8` is a single `internal` extension with a descriptive
  name and no comment; the delegate body is self-explanatory, and both
  `capture/` and `commands/` can see `internal` within the one Gradle module. No
  file-hygiene or naming issue.
- **The cross-world guard reads well and its comment is a justified `why`.**
  `FaweSelectionSource.kt:9-15` reads the selection once, bails on
  `selection.world?.name != player.world.name` (`:13`), and converts only then.
  The two-line comment (`:11-12`) explains the non-obvious reason the check
  exists (the session's selection world survives a teleport), which the code
  alone cannot convey; it is not a `what` restatement. Nullable `world` is
  handled in one glance.
- **The `shadowJar` joml check is followable.**
  `build.gradle.kts:134-149` opens the finished archive, collects `org/joml/`
  entries and `check`s emptiness; the failure message names the jar, the count
  and sample entries, so it documents itself without a comment. The preceding
  `exclude` comment (`:132`) still supplies the reason. No line exceeds the
  column limit, and the `java.util.zip.ZipFile` import is correctly placed.
- **No dead code or dangling references introduced by the round-2 edits.**
  `grep` finds the three `BlockPos` sites now going through `region.blockAt`
  (`DoubleChestGrouper.kt:46,77`, `UiDesignerCommand.kt:141`) and the scanner's
  int-triple loop still calling `World.getBlockAt` directly, as intended; no
  leftover `region.world.getBlockAt(position.x, ...)` remains outside
  `RegionExt.kt`.
- **Concur with tester round-2 finding 1.** The new guard has no automated test
  and no `docs/manual-test.md` row; that is a verification/tracking gap owned by
  tester, and I add nothing to it.
- **Concur with correctness and architecture round-2.** Their fixes and analyses
  (guard world comparison, `RegionExt` restore, doc updates) need no readability
  follow-up.
