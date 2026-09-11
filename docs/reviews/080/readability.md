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
