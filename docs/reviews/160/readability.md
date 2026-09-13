# Readability review — 160 (Import a design file as named chest scaffolds)

## Round 1

### Verdict
Ship with fixes. The new reader and placer are small, directly followable, and
free of comments, dead code, and debug output; formatting and line length are
clean. Four small structure/naming/message nits below, all cheap to fix.

### Findings

#### 1. `UiDesignerConfig` has two names for one operation
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/config/UiDesignerConfig.kt:14` and `:50`
- Problem: `fun resolvePath(raw: String): Path = resolve(raw)` is a one-line
  public alias for the private `resolve`. A reader sees `outputFile` calling
  `resolve(...)` and the command calling `resolvePath(...)` and has to check
  whether the two normalise differently; they are the same function, so the
  indirection only adds a name to learn.
- Suggested fix: make the implementation public and keep one name — rename
  `private fun resolve` to `fun resolvePath` and update the `outputFile` getter
  — or, if the private name must stay, inline the body of `resolvePath`.

#### 2. `JsonImporter` messages hardcode the bounds its constants define
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/export/JsonImporter.kt:35`, `:39`, `:43`, constants at `:65-66`
- Problem: `ROW_RANGE = 1..6` and `SLOT_RANGE = 1..9` are the validation
  authority, but the failure messages repeat `1..6` and `1..9` as literals
  (`"expected 1..6."`, `"expected 1..9."`). The row-count message already
  interpolates `1..${chest.rows}`, so the three messages are not even internally
  consistent. Anyone changing a range must hunt down the messages too, and a
  reader cannot tell which copy is authoritative.
- Suggested fix: derive the message bounds from the constants, e.g.
  `"expected ${ROW_RANGE.first}..${ROW_RANGE.last}."` and likewise for slots.

#### 3. `BlockPos.plus(step, offset)` reads like ordinary addition
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/place/ScaffoldPlacer.kt:114-115`
- Problem: the extension adds `step * offset` along an axis, i.e. it offsets a
  position by a number of blocks in a direction. Called as
  `anchor.plus(step, offset)` it looks like it adds a face and an int rather
  than moving the position; the name hides the multiplication that makes the
  layout work.
- Suggested fix: rename to `offsetBy(step, offset)` (call site reads
  `anchor.offsetBy(step, offset)`) or `step(step, offset)`, which matches what
  `layout` is doing.

#### 4. `SCAFFOLD_PATH_HINT` duplicates `INVALID_OUTPUT_HINT`
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/UiDesignerCommand.kt:259` and `:262`, used at `:397-401`
- Problem: `INVALID_OUTPUT_HINT` is `"check the output-file setting"` and the new
  `SCAFFOLD_PATH_HINT` is `"check the configured output-file setting"` — the same
  advice with one word added. A reader who sees both must diff the strings to
  work out whether the scaffold case is meant to be different, and the near-copy
  will drift.
- Suggested fix: reuse `INVALID_OUTPUT_HINT` for the unresolved-path branch; if
  the scaffold branch genuinely needs its own wording, make it distinguish the
  case rather than restating the existing hint.

### Non-findings
- Control flow in `JsonImporter.validate`: the three nested `forEach`s plus
  `fail(...)` reads linearly, and each check is flat. The `fail` helper keeps
  the branches from nesting.
- `ScaffoldPlacer.place`'s obstruction check (`occupied(target) || (!target.type.isAir && !target.isReplaceable)`) is a faithful direct transcription of "occupied, or neither air nor replaceable"; no boolean tangle worth a rewrite.
- The `var offset` captured by the `map` in `layout` is a mildly imperative
  shape, but it is short and reads top-to-bottom; a `fold`/`scan` rewrite would
  not be clearer.
- Sealed case names are clear and internally consistent (`Placed`, `NoTarget`,
  `ParseFailure`, `Obstructed`, `IoFailure`); the parallel
  `PlacementResult`/`ScaffoldOutcome` hierarchies are the deliberate seam, not a
  naming problem.
- Message style matches the file's existing pattern: `"Cannot scaffold: ..."`,
  `"Could not read ..."`, and `reasonOrDefault`/`withTrailingPeriod` usage are
  consistent with the `save`/`reload` bodies.
- File hygiene: no comments restating code (the removed test comment about
  `dataFolder` is appropriately gone), no dead code, no debug output, no
  imports left unused. No line exceeds 100 columns; continuation/indentation
  looks ktlint-consistent.
- `MaterialResolver` as a one-line object is justified as the injected matcher
  seam; not unnecessary indirection.
