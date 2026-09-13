# UX review — 160 (Import a design file as named chest scaffolds)

## Round 1

### Verdict

The loop is close to ship: `/uidesigner scaffold [file]` is listed in help,
tab-completes the subcommand and the data folder's `.json` files, and every
outcome has a red `[UiDesigner]` `Cannot …`-style message except the two
success paths. Five feedback/discoverability gaps remain, the sharpest being a
green `"Scaffolded 0 chest designs"` for an empty file (a no-op reported as
success) and IO/parse errors that repeat the file path while suppressing the
fix hint.

### Findings

#### 1. An empty design file reports success, and the success count drops save's double-count caveat

- Location:
  `src/main/kotlin/dev/cypdashuhn/uidesigner/place/ScaffoldPlacer.kt:44-75`,
  `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/UiDesignerCommand.kt:200-205`,
  `:372-375`
- Problem: a file that decodes to `[]` (or any hand-authored design with no
  entries) passes import validation, `layout` returns an empty plan list, the
  pre-check finds nothing, and the placer returns `PlacementResult.Placed(0)`.
  The command then prints the success message
  `"Scaffolded 0 chest designs from <file>."` in green. Nothing was placed, so
  the player reads success for a no-op — exactly the "claims success for a
  no-op" failure mode. The count is also the number of file entries, so a
  single 6-row entry prints `"Scaffolded 1 chest design"` while two chest
  blocks appear; `save` guards this reading with
  `"Exported N chest designs to … (a double chest counts once)."`, but the
  scaffold success line drops the parenthetical, so the asymmetry looks like a
  miscount.
- Suggested fix: treat a zero-entry design as a distinct error (e.g.
  `"Cannot scaffold: the design file <file> contains no chests."`) before
  placing, and add the same `"(a double chest counts once)"` caveat to
  `scaffoldSuccessMessage` that `saveSuccessMessage` (`:266-272`) already has.

#### 2. A missing file repeats the path and suppresses the actionable hint

- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/UiDesignerCommand.kt:397-402`,
  `src/main/kotlin/dev/cypdashuhn/uidesigner/export/JsonImporter.kt:13`
- Problem: `Files.readString(file)` throws `NoSuchFileException`, whose
  `message` is just the path string, and it is caught as `IOException` →
  `IoFailure(file, e.message)`. `scaffoldIoFailureMessage` then builds
  `target = "the design file $file"` and `detail = reasonOrDefault(reason, SCAFFOLD_IO_HINT)`;
  because the reason is non-blank the `"check the file exists and is readable"`
  hint is never appended and the path is printed twice:
  `"Could not read the design file /…/design.json: /…/design.json."` The
  commonest real failure therefore gives the player no fix direction, unlike
  `writeFailedMessage` / `reloadFailedMessage` which fall back to a real hint.
- Suggested fix: when the reason is (or contains) only the file path, use
  `SCAFFOLD_IO_HINT`; or always append the hint rather than substituting it,
  e.g. `"Could not read the design file <path> (check the file exists and is readable)."`.

#### 3. Parse failures print the path twice, and malformed JSON leaks library text

- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/export/JsonImporter.kt:14,62-63`,
  `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/UiDesignerCommand.kt:383-387`
- Problem: `JsonImporter.fail` already embeds the path in the exception
  (`"$file: $detail"`), and `scaffoldParseFailureMessage` prefixes it again
  with `"Invalid design in $file: "`, so a bad `rows` entry renders as
  `"Invalid design in /…/design.json: /…/design.json: entry #1 (\"Shop\") has rows=7; expected 1..6."`
  The entry context is good, but the duplicated path is noise and makes the
  message read like two files. Separately, a JSON *syntax* error is a raw
  `SerializationException` caught at `UiDesignerCommand.kt:197-199`; its
  `message` is a parser offset/token description, not a player-facing
  "offending entry", so the player sees an internal-sounding line under
  `"Invalid design in …"`.
- Suggested fix: have the importer throw the reason without the path (the
  command already adds it), or have the command not re-add a path the reason
  already starts with. For a `SerializationException`, translate it to a fixed
  phrase such as `"the file is not valid JSON (see docs/data-format.md)"`
  rather than echoing the raw parser text.

#### 4. Obstruction does not say whether the player is the blocker, and gives no way out

- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/place/ScaffoldPlacer.kt:59-69`,
  `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/UiDesignerCommand.kt:389-395`
- Problem: the pre-check folds `occupied(target)` (the player's own bounding
  box) and `!target.isReplaceable` into one `obstructed` list and reports a
  bare `PlacementResult.Obstructed(count, first)`. The message
  `"Cannot scaffold: 1 target block is obstructed (first at (x, y, z)); nothing placed."`
  is truthful but gives the player no clue when the "obstruction" is
  themselves: looking down at one's own feet produces an obstruction at the
  feet block, and the player sees no block there to clear. It is also silent
  on the fix (step aside / clear the block / look elsewhere), unlike the save
  path's clipped-chest message which spells out the recovery.
- Suggested fix: carry the reason in `Obstructed` (e.g. `playerOccupied: Boolean`
  or a small enum) and word the message accordingly, e.g.
  `"Cannot scaffold: you are standing in the target row at (x, y, z); step aside and try again."`
  vs `"…blocked by a block at (x, y, z); clear it and try again."`

#### 5. Help does not explain the default file or the anchor/layout rules, and the natural "look at the spot" flow silently fails

- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/UiDesignerCommand.kt:249-255`,
  `src/main/kotlin/dev/cypdashuhn/uidesigner/place/ScaffoldPlacer.kt:88-112`
- Problem: the help line is
  `"/uidesigner scaffold [file] - place named, empty chests from a design file."`
  It never says that an omitted/blank `file` falls back to the configured
  `output-file` (`UiDesignerCommand.kt:208-213`), so the optional argument's
  default is guesswork. It also says nothing about where the chests land.
  The anchor is the *targeted block itself*, with the block-in-front-of-feet
  fallback used only when the ray hits nothing (`ScaffoldPlacer.kt:106-112`),
  and the placer requires that block be air/replaceable. A player who does the
  obvious thing — aim at the ground where the row should go — targets a
  non-replaceable dirt/stone block and gets finding 4's obstruction message,
  with no indication that they should instead aim at open space so the fallback
  fires (or target grass/flowers). Facing and row direction (`facing = view.oppositeFace`,
  row along `view.rotateYClockwise()`) are likewise undiscoverable.
- Suggested fix: expand the help line to state the default and the anchor
  contract, e.g. `"/uidesigner scaffold [file] - place empty chests in a row in
  front of you (default file: the configured output-file)."` and add a short
  line to the obstruction/no-target message pointing at the anchor rule
  (e.g. `"aim at open space to use the block-in-front anchor"`).

### Non-findings

- **Subcommand discoverability is fine.** `scaffold` is in `HELP_TEXT`
  (`UiDesignerCommand.kt:252`), the bare `/uidesigner` and `/uidesigner help`
  show the same text, and the root tab-completes `save`/`scaffold`/`reload`/`help`
  (covered by `UiDesignerCommandTest.kt:829-835`).
- **File tab completion is right and filtered.** `.suggestStrings { configProvider().jsonFiles() }`
  (`UiDesignerCommand.kt:130`) lists only regular `.json` files, sorted, and
  `resolveScaffoldFile` resolves a bare suggestion back against the data folder,
  so picking a suggestion round-trips; absolute paths still work
  (`ScaffoldPlacer` tests / `UiDesignerCommandTest.kt`).
- **Styling matches the save/reload UX.** Every non-success outcome uses
  `Messages.styled(Messages.errorColor, …)` with the shared aqua `[UiDesigner]`
  prefix and a `Cannot …`/`Could not …` opener; the success path uses
  `successColor`. Verbosity is one line per run and no outcome is silent.
- **Player-only handling is consistent.** `scaffold` mirrors `save`: a
  non-player sender is silently ignored (same as the existing commands), and no
  permission checks are introduced.
- **The `NoTarget` text is acceptable.** `"Cannot scaffold: no world or usable
  target block."` names the failure without a fix, but in the live loop the
  fallback in `anchorOf` (`ScaffoldPlacer.kt:106-112`) means an obstruction
  message, not this one, is what a player actually reaches.
- **Implicit success location is fine.** The success line names the file but not
  the anchor coordinates; the player just triggered the placement and is
  standing at the anchor, so the "where" is self-evident.
