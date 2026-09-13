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
