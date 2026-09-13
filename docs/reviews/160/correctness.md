# Correctness review — 160 (Import a design file as named chest scaffolds)

## Round 1

### Verdict
Ship. The reader validates the schema and fails closed before returning, the
placer lays the row out consistently with the grouper's and vanilla's
`getConnectedDirection` rule and pre-checks every target before any block write,
and the command separates IO from parse failures and resolves default/relative/
absolute paths correctly. I found no logic, state, data or integration defect.

### Findings
No findings.

### Non-findings

- **Reader validation is complete and fails closed.**
  `JsonImporter.read` decodes the whole list, then validates `rows` 1..6, each
  content `row` in `1..rows`, each `slot` in 1..9, the injected item matcher,
  and non-blank/unique names; the first violation throws
  `InvalidDesignException` naming the file and entry, and nothing is returned, so
  a malformed file cannot be partially accepted (`JsonImporter.kt:12-55`).
- **Name rules match the 150 export gate.** Keys are `trim().lowercase()`,
  original casing/spelling is preserved in the returned chests, and a blank name
  is rejected before insertion; this is the same normalization used by
  `validateForExport` (`JsonImporter.kt:23-33`, `ExportValidation.kt:21-35`).
- **Double geometry is correct for all four cardinals.** `layout` places `RIGHT`
  at the anchor and `LEFT` one step along `view.rotateYClockwise()`, both facing
  `view.oppositeFace` (`ScaffoldPlacer.kt:88-104`). I hand-checked N/E/S/W
  against `ChestBlock.getConnectedDirection` (LEFT = `facing.clockWise`, RIGHT =
  `facing.counterClockWise`) and the grouper's `partnerOffset`
  (`DoubleChestGrouper.kt:162-170`); the pair is complementary in every
  direction and the chest front matches vanilla's
  `getStateForPlacement` (`facing = player direction opposite`).
- **Real linking does not depend on `onPlace`.** Verified against the bundled
  Paper 26.2 server jar: `ChestBlock` has no `onPlace` link step — double
  detection is block-state driven via `getMenuProvider`/`DoubleBlockCombiner`.
  Placing the first half alone is reset to `SINGLE` by `updateShape`, but placing
  the complementary half relinks it (`updateShape` sets
  `TYPE = neighborType.getOpposite()`), and `CraftChest.getInventory()` then
  returns a `CraftInventoryDoubleChest` whose holder is a `DoubleChest`, so
  `ChestNamer.setName` on the start half names both. Real-server confirmation
  stays with MT-013/MT-014.
- **Atomicity and the overlap check are sound.** All planned blocks are
  flattened and checked before the first write, so a later obstruction cannot
  leave a partial row; the player test uses `BoundingBox.of(block)` (the full
  1×1×1 cube) against `player.boundingBox` and count/first-obstruction are taken
  in placement order (`ScaffoldPlacer.kt:58-70`). Block/inventory access happens
  on the command thread; file IO is synchronous by the MVP decision.
- **Failure classification is right.** Missing/dir/permissionless files raise
  `IOException` and map to `IoFailure(file)`; `InvalidDesignException` and
  kotlinx `SerializationException` map to `ParseFailure(file)`; an
  `InvalidPathException` while resolving maps to `IoFailure(null)`; the missing
  case names the path and adds the "file exists and is readable" hint
  (`UiDesignerCommand.kt:185-213, 397-402`).
- **Path resolution and completion contracts hold.** Omitted/blank uses
  `config.outputFile`; relative uses `config.resolvePath` (data folder);
  absolute is only normalized; `jsonFiles()` lists data-folder `.json` names for
  completion and is empty when the folder is missing/unreadable
  (`UiDesignerCommand.kt:208-213`, `UiDesignerConfig.kt:14-27`).
- **Item-id round trip works.** The exporter writes `stack.type.key.toString()`
  (`minecraft:*`); Paper's `Material.matchMaterial` strips `minecraft:`,
  uppercases and looks up `BY_NAME`, so exported ids resolve; legacy names also
  resolve (`Material.java:3112-3124`). Note that `Material.AIR` (and block-only
  materials) also resolve, so a hand-written `"item": "minecraft:air"` passes
  the reader. It is harmless for scaffold because `content` is ignored, but 170
  (`sync`) should decide whether explicit AIR/non-item ids are acceptable.
- **`ScaffoldOutcome.NoTarget` is unreachable, but not a behavior gap.** The
  anchor fallback always yields a block (targeted block, else block in front of
  the feet) and non-replaceable targets surface as `Obstructed`, which is the
  ticket's fail-closed path. The outcome is defined and mapped; no user-facing
  case is missing.
- **`rows` 1/2/4/5 downgrade to a 3-row single is the documented compromise.**
  The ticket mandates accepting 1..6 and `design.md:152` records the structural
  approximation, so the "same rows" round trip holds for real exporter output
  (only 3/6).

## Round 2

### Verdict
Ship. I concur with all five round-1 fixes — the empty-design rejection,
`NoSuchFileException` -> `IoFailure(file, null)`, `InvalidDesignException`
carrying `file`+`detail`, `Obstructed.firstIsPlayer`, and the
`resolveScaffoldFile` delegation to `resolvePath` — and found no regression in
the double geometry, atomic pre-check, name semantics, or outcome mapping.

### Findings
No findings.

### Non-findings

- **Empty design rejects as `ParseFailure` (concur).** `JsonImporter.validate`
  fails up front with `"the design contains no chests."`, so `read` can no
  longer return an empty list and `scaffold` can no longer print a green
  `Placed(0)`/`"Scaffolded 0 chest designs"` for a no-op. The export path can
  never write an empty array (`save` returns `NoChests` before the exporter), so
  the `save`->`scaffold` round trip is unaffected, and no 160 acceptance
  criterion requires accepting an empty file. It is a deliberate policy for the
  shared reader; if 170 needs an empty desired state to be legitimate it can
  catch `InvalidDesignException` or special-case it there.
- **`NoSuchFileException` -> `IoFailure(file, null)` (concur).** The subtype
  catch precedes `IOException`, so a missing file keeps the resolved `file` and
  the null reason lets `scaffoldIoFailureMessage` fall back to
  `SCAFFOLD_IO_HINT`; the duplicated path is gone. Other IO failures
  (`AccessDeniedException`, directory reads) still carry `e.message`, a bad
  explicit path still maps to `IoFailure(null, ...)` via the
  `resolveScaffoldFile` catch, and the IO-vs-parse split is unchanged.
- **`InvalidDesignException` carries `file` + `detail` (concur).** The command
  renders `ParseFailure(file, e.detail)` against the resolved file once, so a
  bad entry reads `Invalid design in <file>: entry #1 ("Shop") has rows=7;
  expected 1..6.` with no doubled path; `message` still contains both file and
  entry. The row/slot messages now derive their bounds from
  `ROW_RANGE`/`SLOT_RANGE`, so the wording matches the range actually enforced
  and 1/2/4/5 still validate as before.
- **`Obstructed.firstIsPlayer` (concur).** `ScaffoldPlacer` builds an ordered
  `List<Obstruction>` with a `byPlayer` flag and still evaluates every target
  before any write, so atomicity, the count, and first-in-plan-order semantics
  are intact. `firstIsPlayer` tracks the first blocked target only (a later
  player block behind an earlier solid one still reports the solid one), which
  is exactly what the message claims; `ScaffoldOutcome.Obstructed` and
  `scaffoldObstructedMessage` plumb it through.
- **`resolveScaffoldFile` delegates to `resolvePath` (concur).** The blank guard
  stays and everything else goes through the single normalising rule
  (`UiDesignerConfig.resolvePath`, now public and also used by `outputFile`), so
  the absolute/relative contract cannot drift; `InvalidPathException` still
  falls into the surrounding try and maps to `IoFailure(null, ...)`. Absolute
  paths still pass through normalised and relative paths still join the data
  folder.
- **Geometry, atomic pre-check, and naming are unchanged.** The only placer
  changes are the `offsetBy` rename (identical body: `x + step.modX * offset`,
  `z + step.modZ * offset`), the `Obstruction` wrapper, and the extra result
  field; the RIGHT/LEFT assignment, `view.oppositeFace`,
  `view.rotateYClockwise()`, pre-check-before-write, the
  `BoundingBox.of(block)` player-overlap volume, and `ChestNamer.setName` on the
  start block are the round-1 code that I verified against vanilla's
  `getConnectedDirection` and `CraftChest.getInventory()`.
- **Outcome mapping still covers all five branches.** `Placed`, `NoTarget`,
  `ParseFailure` (invalid design, empty design, malformed JSON), `Obstructed`
  (now with `firstIsPlayer`), and `IoFailure` (missing, unreadable, unresolvable
  path) each map to their `ScaffoldOutcome` and message.
- **Round-1 non-findings still stand.** Explicit `minecraft:air`/non-item ids
  still pass `Material.matchMaterial` (deferred to 170), `ScaffoldOutcome.NoTarget`
  is still unreachable because the anchor fallback always resolves a block (the
  new hint text does not change that), and `rows` 1/2/4/5 still place a 3-row
  single per the documented approximation.
