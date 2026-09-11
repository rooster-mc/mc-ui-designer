# UX review — 050 (/chest-edit naming)

## Round 1
### Verdict
Ship with fixes. The core loop (`/chest-edit <name>`, `/chest-edit clear`, GUI
shows the name) is short and correct for single chests, but the reserved `clear`
sentinel has two real foot-guns (case-sensitivity and no escape hatch for a
chest literally named "clear") and naming a double chest only touches one half.
Help/prefix/tab-completion polish is legitimately deferred to 070.

### Issues
#### 1. A chest cannot be named "clear", and there is no escape hatch (severity: medium)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/ChestEditCommand.kt:53`
- Problem: `if (rawName == "clear")` means the literal name "clear" is
  unreachable. "Clear" is a very plausible UI label for a chest in this exact
  tool (a clear/reset button), so a designer will hit this. There is no quoting
  or flag to force a literal name — `GreedyStringArgument` passes the text
  verbatim, so the player has no workaround. They type `/chest-edit clear`
  expecting a named chest and instead silently destroy the existing name
  (message: `Cleared this chest's name.`).
- Suggested fix: use an unambiguous sentinel that cannot collide with a normal
  label, e.g. `--clear` / `-clear`, or add an explicit escape
  (`/chest-edit "clear"` handling, or a leading backslash) and document it.
  Whichever is chosen, the `clear` concept must be reserved *deliberately* and
  documented, not implicitly by string equality.

#### 2. The `clear` keyword is matched case-sensitively (severity: medium)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/ChestEditCommand.kt:53`
- Problem: `/chest-edit Clear` and `/chest-edit CLEAR` do **not** clear; they
  set a chest name to `"Clear"` / `"CLEAR"` and reply
  `Named this chest "CLEAR".`. Minecraft commands are conventionally
  case-insensitive, so a player typing `CLEAR` to reset will instead silently
  rename the chest to that word — a destructive surprise with a success message.
- Suggested fix: compare case-insensitively (`rawName.equals("clear", ignoreCase = true)`)
  once the escape-hatch/sentinel decision above is made, so keyword semantics
  are predictable regardless of capitalisation.

#### 3. Naming a double chest only names one half (severity: medium)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/naming/ChestNamer.kt:19`
  (`setName`), `ChestNamer.kt:25` (`clear`), `ChestEditCommand.kt:52`
- Problem: a double chest is two `Chest` block entities with independent custom
  names. `block.state as? Chest` sets only the targeted half. The player gets
  `Named this chest "Shop".` and thinks the whole (visually single) chest is
  named, but opening/pointing at the other half still shows the default "Chest"
  title, and ticket 040 groups a double chest into one export entry that reads a
  canonical half — so a name set on the non-canonical half can be silently
  dropped from the JSON. The GUI therefore does not consistently "show the name
  as expected".
- Suggested fix: when the target is a double chest (detect via
  `Chest.getInventory().holder as? DoubleChest`, per 040's note), apply
  `setName`/`clear` to both halves so both the GUI title and the eventual
  exporter agree. At minimum, document the single-half behaviour and make 040
  read the named half.

#### 4. `clear` is undiscoverable and there is no usage/help (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/ChestEditCommand.kt:28-31`
- Problem: the command is registered with a required `GreedyStringArgument` and
  no `withShortDescription` / `withFullDescription` / no-arg executor.
  `/chest-edit` with no arguments yields CommandAPI's generic missing-argument
  error, and nothing anywhere tells the player that `clear` is special or what
  the syntax is. Tab completion cannot surface it either — `GreedyStringArgument`
  has no suggestions.
- Suggested fix: this overlaps ticket 070 ("`/chest-edit` help/usage and
  completion"), so defer the polish, but ensure 070's help text explicitly
  documents the `clear` keyword (and its escape/sentinel). Not a 050 blocker on
  its own; listed so the reserved keyword is not forgotten.

#### 5. "Look at a chest to name it." hides *why* targeting failed (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/ChestEditCommand.kt:42-43,52`
- Problem: `Outcome.NotAChest` covers three distinct player situations:
  `getTargetBlockExact` returned null (looking at air / out of range), the
  block is in range but not a chest (e.g. a barrel, stone), and the look target
  is beyond reach. All three print the same line. The player who is clearly
  looking at a block may be confused why they are told to "look at a chest".
- Suggested fix: split into "not looking at a block / too far" vs "that block
  is not a chest" (and consider naming the block type). Keeps the failure
  actionable and consistent with 060's planned no-selection/empty-selection
  wording.

#### 6. Clearing an already-unnamed chest claims success (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/ChestEditCommand.kt:40-41,53-55`
- Problem: `/chest-edit clear` on a chest with no name still prints
  `Cleared this chest's name.` Nothing was cleared, so the feedback
  misrepresents the action and hides the case where the player meant to name a
  chest "clear".
- Suggested fix: check `ChestNamer.nameOf(target)` first and reply e.g.
  `This chest has no name.` when it is already unnamed.

### Non-issues
- **Command name and shape.** `/chest-edit <name>` as a separate top-level
  command matches `docs/design.md` use-case step 6 and the ticket note; it is
  short, memorable and works with multi-word labels (`GreedyStringArgument`
  keeps spaces, so "Main Menu" is one name).
- **Chest GUI display for single chests.** `chest.customName(Component.text(name))`
  plus `chest.update(true)` is the right mechanism; `Component.text` treats the
  input literally, so colour codes / `%` / `$` cannot inject or crash. The name
  persists in block-entity NBT across restarts, matching the documented choice.
- **Chat vs action bar.** A single short confirmation per command is not spammy;
  chat is appropriate (and keeps a scrollback the player can check) — no need to
  move this to the action bar.
- **Permission denial.** `.withPermission("uidesigner.chest-edit")` (line 30)
  makes CommandAPI hide the command and send its standard denial; an op gets it
  by default. Graceful-denial wording and centralised prefix/colour are
  explicitly ticket 070's job, not a 050 defect.
- **Reach.** `REACH = 5` with `getTargetBlockExact` is a correct line-of-sight
  raycast and a sensible tooling distance (slightly above the 4.5 survival block
  reach; irrelevant for creative/op design work).
- **Presentation.** Plain white, no prefix, no colour — consistent with the fact
  that 050 is the only command so far and 070 centralises messages in
  `Messages.kt`; not worth churning inline strings now.
- **`/uidesigner` integration.** Out of scope: 050 is deliberately a standalone
  top-level command, and 060 owns the `/uidesigner` tree.

## Round 2
### Verdict
Ship. Both round-1 functional foot-guns are fixed: a double chest is now
named/cleared on both halves, and `clear` is case-insensitive with blank input
treated as clear. The remaining findings are feedback-wording nits that are
legitimately 070's ("Review all error paths for actionable wording",
"`/chest-edit` help/usage and completion"). The literal name "clear" is still
unreachable, but the ticket mandates `/chest-edit clear`, the reservation is now
documented in `docs/design.md:67-73`, and the player-facing escape hatch/help is
a 070 concern.

### Issues
#### 1. A chest still cannot be named "clear", and the reservation is only documented for developers (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/ChestEditCommand.kt:56`;
  `docs/design.md:71-73`
- Problem: `rawName.equals("clear", ignoreCase = true)` reserves the word in
  every casing, so a designer who wants a UI button labelled "Clear" (a very
  plausible label in this tool) cannot set it. Round 1 called for either an
  escape hatch or deliberate, documented reservation; the implementor chose the
  latter, and `docs/design.md` now states "a literal name `clear` is
  unreachable". That resolves the *specification* gap, but the reservation is
  invisible to the player: no help, no tab completion, no on-screen hint, and
  `/chest-edit clear` on an unnamed chest still replies
  `Cleared this chest's name.` (issue 2), so the surprise is intact.
- Suggested fix: acceptable to ship 050 as mandated. Make 070's `/chest-edit`
  help text explicitly document that `clear` (any casing) and blank input remove
  the name, so the reserved word is at least discoverable. If a literal "Clear"
  label must be settable, note that an escape hatch is arguably *new
  functionality*, which 070 explicitly excludes (`docs/tasks/070-ux-polish.md:27-28`);
  file it as a small follow-up ticket rather than smuggling it into 070.

#### 2. Clearing an already-unnamed chest still claims success (severity: low, deferred)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/ChestEditCommand.kt:41-42,56-58`
- Problem: unchanged from round 1. `apply` returns `Outcome.Cleared` whenever the
  target is a chest and the input is blank/`clear`, without checking whether a
  name existed, so the message `Cleared this chest's name.` is printed for a
  no-op. With issue 1 this also masks the "I meant to name it Clear" case: the
  player sees a confident success for an action that destroyed nothing.
- Suggested fix: in 070's error-path pass, check `ChestNamer.nameOf(target)`
  first and reply e.g. `This chest has no name.` when already unnamed. Not a 050
  blocker.

#### 3. "Look at a chest to name it." still conflates air/out-of-range with a non-chest block (severity: low, deferred)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/ChestEditCommand.kt:43-44,55`
- Problem: unchanged from round 1. `Outcome.NotAChest` covers a null raycast
  result (looking at air / nothing in range), a looked-at non-chest block, and
  being out of range; all three print the same line. A player aiming at a barrel
  or stone is told to "look at a chest", which is not actionable.
- Suggested fix: 070's "Review all error paths for actionable wording" owns
  this; split into "not looking at a block / too far" vs "that block is not a
  chest" (optionally naming the block type), consistent with 060's planned
  no-selection/empty-selection wording. Not a 050 blocker.

### Non-issues
- **Double-chest naming writes both halves.** `ChestNamer.chestsOf(block)`
  (`ChestNamer.kt:36-46`) resolves the `DoubleChest` holder and returns
  `leftSide`/`rightSide`; `setName`/`clear` (`ChestNamer.kt:27-34`) iterate that
  list, so both block entities get the same custom name (or both are nulled).
  `nameOf` (`ChestNamer.kt:25`) uses `firstNotNullOfOrNull`, so a name set on
  either half is read back. The GUI title now agrees on both halves and 040's
  canonical half cannot silently lose the name — the round-1 issue is resolved.
- **`clear` is case-insensitive; blank input clears.** `ChestEditCommand.kt:56`
  matches `"clear"` with `ignoreCase = true`, so `Clear`/`CLEAR` clear instead of
  renaming (round-1 issue 2 resolved), and `isBlank()` prevents an empty/whitespace
  custom name. Both paths produce the existing `Cleared this chest's name.`
  message, which is the right wording for the action.
- **Feedback for the new paths still makes sense.** `Named this chest "X".` on
  the rename path and `Cleared this chest's name.` on both the keyword and blank
  paths are accurate for what the code does. Naming a double chest now really
  does name "this chest" as the player perceives it, so the singular wording is
  fine.
- **Help/completion and message centralisation are correctly deferred.** 070
  explicitly lists "`/chest-edit` help/usage and completion" and centralised
  Adventure messages/prefix; nothing in 050 regressed here, and `/chest-edit`
  still ships with no `withShortDescription`/no-arg executor exactly as 070
  expects to fix.
- **Presentation and permission unchanged.** Plain white, no prefix, single chat
  line per command; `.withPermission("uidesigner.chest-edit")` still hides the
  command from non-ops. Both remain 070's job (graceful denial, colour/prefix),
  not a 050 defect.
- **Command shape unchanged.** `/chest-edit <greedy name>` still matches
  `docs/design.md` use-case step 6 and keeps spaces in multi-word labels.
