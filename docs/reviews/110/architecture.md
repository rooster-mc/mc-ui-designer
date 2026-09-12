# Architecture review — 110 (TODO-sweep hygiene and exporter robustness)

## Round 1

### Verdict

Pass with two documentation-staleness findings, both in `docs/architecture.md`.
The code-side split is at the right seam and the new helpers are placed and
named sensibly; nothing structural needs to change.

### Findings

1. **`docs/architecture.md:36` — package map describes the old centralized
   message layout.**
   - Location: `docs/architecture.md:36` (`Messages.kt    Adventure components / prefixes`).
   - Problem: after this ticket `Messages` no longer builds player-facing
     components; it holds only shared styling primitives (`PREFIX`, the colour
     palette, `styled`, `reasonOrDefault`, `withTrailingPeriod`). The actual
     message bodies now live as internal top-level functions in
     `ChestEditCommand.kt:82-101` and `UiDesignerCommand.kt:131-209`. The
     one-line package-map entry is the first place a reader looks to learn what
     owns player-visible text, so it now points the wrong way.
   - Suggested fix: update the entry to say what the file now is, e.g.
     `Messages.kt    shared chat styling primitives (prefix, palette, styled)`,
     and note in the `commands/` entries (lines 33-34) that each command file
     owns its message bodies.

2. **`docs/architecture.md:135-136` — "`Messages` owns every player-facing
   component (prefix, colour, wording)" is now false.**
   - Location: `docs/architecture.md:135-136`, inside the `UiDesignerCommand`
     seam bullet.
   - Problem: wording moved to the command files in this ticket; `Messages`
     owns prefix and colour only. This sentence is also the normative statement
     that earlier reviews (070 readability round 1) measured against, so
     leaving it stale re-creates the exact contradiction that review flagged
     once already — just in the opposite direction.
   - Suggested fix: reword to something like "`Messages` owns the shared
     styling primitives (prefix, colour palette, `styled`); each command file
     owns its own message bodies as internal top-level functions, kept
     testable from the same module." One edit covering both spots keeps the
     seam description and the package map consistent.

### Non-findings

- **The split is at the right seam.** `util/` keeps exactly the things a second
  consumer would reuse (prefix, palette, `styled`, the reason/punctuation
  helpers); `commands/` owns the per-feature wording, which is what the ticket
  asked for and what the original TODO wanted. Walking the "next command"
  hypothetically: a new command file imports `Messages.styled` + palette and
  writes its bodies locally — one file touched, no `Messages` growth, no
  cross-package coupling back into `util`. The seam earns its keep without
  being over-generalised (no message registry, no locale machinery for a need
  that does not exist).
- **Internal top-level functions (not private class members) are the right
  visibility.** They exist at file scope so `MessagesTest` can pin every
  player-visible string from the same module; making them private members
  would have forced reflection or dropped the coverage. `internal` is the
  narrowest visibility that keeps that test seam, and nothing outside the
  module can reach them.
- **Public palette is justified.** `prefixColor`/`successColor`/etc. went from
  private to public because the command files now compose
  `Messages.styled(color, ...)` themselves; that is the minimal widening the
  split requires. They are `val`s (not `const`) only because `TextColor`
  cannot be a compile-time constant — no concern.
- **`Messages` keeping its name despite holding no messages is acceptable.**
  Strictly the object is now a styling toolkit, and a name like
  `MessageStyle` would describe it better, but the ticket explicitly scoped
  this as "keep the shared constants" in `Messages.kt`, the test file and
  call sites already reference it, and a rename now would be churn without a
  forcing function. Revisit only if a second styling consumer appears.
- **POSIX-fallback placement is right.** `applyWorldReadablePermissions` is
  private in `JsonExporter.kt:60-69`, next to its only caller `createTemp`; no
  other code applies file attributes, so extracting it to `util/` would be
  premature. The catch of `UnsupportedOperationException` carries a why-comment
  ("filesystems report POSIX support but reject attribute writes"), which is
  exactly the non-obvious-why case the conventions allow. Extendability is
  fine: a future Windows-specific attribute path slots in beside the POSIX
  branch inside the same private helper without touching `toJson`/`export`.
- **`orderedChests` rename needs no doc change.** `docs/architecture.md:145-147`
  says the exporter "normalises order", which stays true; the rename only
  names the mechanism, and no doc names the old identifier.
- **Grouper why-note keeps the docs accurate.** The replacement comment
  (`DoubleChestGrouper.kt:16-20`) states the same geometry rule
  (`docs/architecture.md:95-97` already documents: partner = facing rotated
  clockwise for `LEFT`, counter-clockwise for `RIGHT`), so the arithmetic
  `partnerOffset` and the doc agree; no staleness introduced.
- **Concur with all correctness findings** (there are none — its report is a
  clean pass with four verified non-findings). Nothing in its scope overlaps
  or contradicts mine.
- **Boundaries unchanged and clean.** No Bukkit imports entered `export/`
  (`JsonExporter.kt` diff touches only `java.nio`), FAWE remains behind the
  injected selection lambda, and the command classes stay thin — the added
  message functions are pure string→`Component` mappers with no command
  logic.
