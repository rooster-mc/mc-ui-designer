# Readability review — 110 (TODO-sweep hygiene and exporter robustness)

## Round 1

### Verdict

Pass with three small findings. The moved message blocks read well, the new
names (`orderedChests`, `applyWorldReadablePermissions`, `partnerOffset`) are
clear, both new comments are genuine why-comments, and no TODO hygiene was
missed (`ChestScanner.kt:11` is the one remaining TODO and is explicitly out of
scope). The findings are a naming inconsistency between the two command files,
a confusing two-path parameter, and a one-use wrapper.

### Findings

1. **`ChestEditCommand.kt:81-101` — `chestEdit` prefix on every message
   function is redundant, and inconsistent with `UiDesignerCommand.kt`.**
   - Location: `ChestEditCommand.kt:81,88,91,94,97,100`
     (`chestEditUsageMessage`, `chestEditNamedMessage`, …).
   - Problem: these functions live at top level in `ChestEditCommand.kt`; the
     file name already scopes them, so the prefix carries no information —
     `chestEditClearedMessage` says "chest-edit cleared message" three times.
     `UiDesignerCommand.kt:156-208` does not do this (`helpMessage`,
     `saveSuccessMessage`, `noSelectionMessage`, …), so the two files the
     ticket touched use two different conventions for the same kind of
     top-level function, and a reader moving between them has to notice the
     mismatch before trusting it as meaningless.
   - Suggested fix: drop the prefix in `ChestEditCommand.kt` —
     `usageMessage`, `namedMessage`, `clearedMessage`, `nothingToClearMessage`,
     `noTargetMessage`, `notAChestMessage` — matching the
     `UiDesignerCommand.kt` style. Keep the `Message` suffix in both files: it
     is what separates the builders from the `Outcome` types they describe and
     it is applied consistently.

2. **`JsonExporter.kt:64-65` — `applyWorldReadablePermissions(temp,
   directory)` takes two paths where one explains itself.**
   - Location: `JsonExporter.kt:60,64-65`.
   - Problem: `directory` is used only for
     `directory.fileSystem.supportedFileAttributeViews()`. A reader stops to
     ask why the *directory's* filesystem is checked when the attribute is set
     on `temp` — and the answer (the temp file is created inside that
     directory, so the filesystems are the same) is not visible at the call
     site. The second parameter looks like it might matter when it cannot.
   - Suggested fix: drop the parameter and read the view list from the file
     being modified: `if (!temp.fileSystem.supportedFileAttributeViews()
     .contains("posix")) return`. Same behaviour, one parameter, and the
     function then reads as "make this temp file world-readable if the
     filesystem supports it".

3. **`UiDesignerCommand.kt:121-122` — `failure(e, outputFile)` is a one-use
   wrapper around a constructor call.**
   - Location: `UiDesignerCommand.kt:104,121-122`.
   - Problem: the helper is called once and its body is
     `SaveOutcome.WriteFailed(outputFile, e.message)` — it adds a name and an
     indirection hop without hiding anything (the catch block at line 103-105
     already says what failed). Every other outcome in the file is constructed
     directly at its decision point.
   - Suggested fix: inline it — `return SaveOutcome.WriteFailed(outputFile,
     e.message)` in the catch — and delete the helper.

### Non-findings

- **Concur: the `Message`-suffix style itself is fine.** Architecture's
  non-finding on internal top-level visibility covers the seam; from the
  readability side the suffix is consistent within each file and earns its
  place against the `Outcome`/`SaveOutcome`/`ReloadOutcome` type names. Only
  the `chestEdit` prefix (finding 1) is noise.
- **`orderedChests` (`JsonExporter.kt:32`) is a good rename.** It names the
  observable effect (chests come out ordered) rather than the mechanism, and
  the body shows the sort key immediately. No kdoc needed — the ticket's "only
  if naming cannot capture it" condition is not met.
- **The `DoubleChestGrouper.kt:16-20` why-note is the right comment.** It
  answers exactly the questions the deleted TODO asked: why the block-state
  fallback exists (holder unavailable in some states, e.g. MockBukkit) and why
  type+facing is sufficient (no partner reference on the block state; the pair
  determines the neighbour). It explains why, not what, and sits at the object
  it governs. The `partnerOffset` arithmetic (`DoubleChestGrouper.kt:114-122`)
  is denser than the old table but the comment's "facing vector rotated ±90°"
  is the key to reading it, and the intermediate `facingOffset` val keeps the
  negations legible. No change needed.
- **The belt-and-braces catch comment (`JsonExporter.kt:69`) is a proper
  why-comment.** "Some filesystems report POSIX support but reject attribute
  writes; defaults are fine" explains why an `UnsupportedOperationException`
  is swallowed despite the `contains("posix")` guard one line above — without
  it the catch looks redundant. Keep.
- **File size and organization after the inlining are fine.**
  `UiDesignerCommand.kt` at 209 lines is one class (outcomes, register, two
  command actions, two outcome→message dispatchers) followed by a clearly
  delimited block of top-level message builders with their private constants
  (`HELP_TEXT`, the three hint constants) — the layout mirrors
  `ChestEditCommand.kt`'s class-then-messages shape, so the two files scan the
  same way. `Messages.kt` at 29 lines is now a single-responsibility styling
  toolkit with no dead code.
- **TODO hygiene is complete.** The only remaining `TODO` in `src/` is
  `ChestScanner.kt:11`, which the ticket explicitly scopes out
  (`rooster-region` API additions). All four swept TODOs are gone and each was
  replaced by either code (POSIX guard, `partnerOffset`) or a why-note
  (grouper), never by silence.
- **Formatting signals are clean.** Consistent trailing commas in multiline
  argument/parameter lists, expression-body functions throughout the new
  code, no line-length pressure, imports sorted and minimal (no unused
  imports introduced by the move — `Path` correctly left `Messages.kt` and
  entered `UiDesignerCommand.kt`). Nothing ktlint would flag is visible from
  source.
