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

## Round 2

### Verdict
Ship. All four round-1 fixes landed cleanly and read better than before, the new
message bodies and `MessagesTest` coverage are easy to follow, and the tree still
has no comment/dead-code/line-length drift. Two small message/clarity nits below;
neither is a reason to hold the ticket.

### Findings

#### 1. The `NoSuchFileException` branch silently drops the reason for a non-obvious end
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/UiDesignerCommand.kt:199-200`
- Problem: `catch (e: NoSuchFileException) { return ScaffoldOutcome.IoFailure(file, null) }`
  passes `null` instead of `e.message` (which the sibling `IOException` branch
  uses) so that `scaffoldIoFailureMessage` falls back to the actionable hint
  rather than echoing the path twice. That reasoning spans two files and is
  stated nowhere: on the page the branch reads as if the exception message was
  forgotten, and the `NoSuchFileException`-before-`IOException` ordering adds a
  second detail a reader must notice to understand why the more specific catch
  exists at all.
- Suggested fix: add a one-line *why* comment (the repo allows comments for a
  non-obvious why), e.g. `// message is only the path; fall back to the action
  hint`, or extract the branch into a named helper such as
  `missingFileFailure(file)` so the intent lives in the name.

#### 2. `scaffoldNoTargetMessage`'s second sentence has no object
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/UiDesignerCommand.kt:392-397`
- Problem: `"Aim at open space to place in front of you."` never says what is
  placed; the verb dangles, so the one line meant to tell the player how to
  recover from a failed scaffold is the least clear of the new bodies.
- Suggested fix: name the object, e.g. `"Aim at open space to place the row in
  front of you."`

### Non-findings
- **Round-1 finding 1 (two names for one resolve)** is fixed cleanly:
  `UiDesignerConfig` now has a single public `resolvePath` and the `outputFile`
  getter calls it; the naming is now unambiguous.
- **Round-1 finding 2 (`1..6`/`1..9` literals)** is fixed: the rows and slots
  messages interpolate `ROW_RANGE`/`SLOT_RANGE` bounds, so the constants are the
  single source of truth.
- **Round-1 finding 3 (`BlockPos.plus`)** is fixed: `offsetBy(step, offset)` reads
  correctly at both call sites and in `layout`.
- **Round-1 finding 4 (near-duplicate hint)** is fixed: `SCAFFOLD_PATH_HINT` is
  gone and the unresolved-path branch reuses `INVALID_OUTPUT_HINT`.
- **No formatting drift.** No line in any changed `.kt` file exceeds 100 columns;
  the scaffold message bodies and `allMessages()` entries follow the existing
  `save`/`reload` shape (one assertion block per body, case-driven helper
  variants), and the new `MessagesTest` scaffold tests are named by the same
  `"<feature> <what it asserts>"` convention as their neighbours.
- **No new comments or dead code.** The only comment in the changed test range is
  the pre-existing MockBukkit note; every new constant (`SCAFFOLD_IO_HINT`,
  `SCAFFOLD_PARSE_HINT`, `MALFORMED_DESIGN`) has a live use, and there is no
  leftover `SCAFFOLD_PATH_HINT` or unused branch.
- **`byPlayer` vs `firstIsPlayer` naming is acceptable.** The private
  `Obstruction.byPlayer` maps once to the public
  `PlacementResult.Obstructed.firstIsPlayer`, and the longer public name is the
  clearer one at the call sites; not worth churn.
