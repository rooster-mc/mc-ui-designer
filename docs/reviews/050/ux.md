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
