---
name: Adopt rooster-region for capture
status: done
parent: MVP
depends-on: ["060"]
reviewers: [tester, correctness, architecture, readability]
---

## Goal
Replace `mc-ui-designer`'s duplicated region and FAWE-selection code with the
`rooster-region` library, so the region/WorldEdit logic lives in one place.

## Scope
- Wire the sibling `../rooster-region` build into `settings.gradle.kts` as a
  Gradle **composite build** (`includeBuild`) with explicit
  `dependencySubstitution`:
  - `dev.rooster.region:rooster-region` → `:core`
  - `dev.rooster.region:rooster-region-worldedit` → `:worldedit`
  (the artifact ids differ from the project names, so auto-substitution will
  not bind without this).
- Add both modules as dependencies in `build.gradle.kts`.
- Delete `capture/Region.kt`; use `dev.rooster.region.Region` (Location-based:
  `edge1`/`edge2`, `minX..maxX`, `world`, ...).
- Reduce `capture/FaweSelectionSource.kt` to the library adapter:
  `player.worldEditSelection()?.toRegion(player)`. Keep the `SelectionSource`
  seam (now returning `dev.rooster.region.Region?`) so capture stays testable
  with a fake.
- Update `ChestScanner`, `ChestCapture`, `DoubleChestGrouper` and their tests to
  the new `Region` type. Keep `model/BlockPos` — it is the pure model used by
  `UiChest.position` and `ChestContent`.
- Delete `capture/RegionTest.kt` (the library owns `Region` tests).
- Ensure the shaded jar does **not** bundle `org.joml` (Paper provides joml at
  runtime; the library exposes `Vector3d`, which Paper supplies).
- Update `docs/architecture.md` to name the library instead of the local copy.

## Acceptance criteria
- `just build`, `just test`, `just format` pass.
- No WorldEdit session code remains in this repo; selection goes through the
  library adapter, and capture uses `dev.rooster.region.Region`.
- The shaded jar contains no `org/joml/**` entries.
- Existing behaviour is unchanged: the capture tests and JSON output are the
  same as before the swap.
- The dev server still boots and `/uidesigner save` works end to end (record in
  `docs/manual-test.md` if it cannot be automated here).

## Out of scope
- Any change to `rooster-region` itself (if it needs one, file a follow-up
  there rather than editing the sibling repo from this ticket).
- JSON format or command behaviour changes.

## Notes
- `rooster-region` is at `/home/cyp/repos/rooster-region`; its README documents
  the explicit `dependencySubstitution` for composite consumption.
- If the library's `joml` dependency turns out to be `implementation` rather
  than `compileOnly`, do not bundle it — exclude it from the shadow jar instead
  and note the follow-up.
- The FAWE runtime dependency in `plugin.yml` stays as-is.
