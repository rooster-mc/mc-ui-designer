# Correctness review — 110 (TODO-sweep hygiene and exporter robustness)

## Round 1

### Verdict

Pass. I traced every moved string against `git show HEAD:src/main/kotlin/dev/cypdashuhn/uidesigner/util/Messages.kt`, re-derived the `partnerOffset` arithmetic for all eight facing×type cases, and walked the POSIX guard. No findings.

### Findings

None.

### Non-findings

- **Player-visible strings are byte-identical.** Every body string, hint constant (`WRITE_FAILURE_HINT`, `RELOAD_FAILURE_HINT`, `INVALID_OUTPUT_HINT`), color mapping, and the `styled` component structure (colored root, aqua `PREFIX` child, body child) moved verbatim from `Messages.kt` into `ChestEditCommand.kt:85-106` and `UiDesignerCommand.kt:146-209`. The singular/plural noun logic in `saveSuccessMessage` and the `withTrailingPeriod(reasonOrDefault(...))` composition are unchanged. No remaining callers of the removed `Messages.*` functions exist (`Messages.PREFIX` is still used by `MessagesTest.kt:36`).
- **`partnerOffset` rewrite is equivalent.** For `LEFT`, `(-modZ, modX)` reproduces the old table: NORTH (mod 0,-1) → (1,0); SOUTH (0,1) → (-1,0); EAST (1,0) → (0,1); WEST (-1,0) → (0,-1). For `RIGHT`, `(modZ, -modX)`: NORTH → (-1,0); SOUTH → (1,0); EAST → (0,-1); WEST → (0,1). All eight match. The old `else -> null` branch is unreachable in practice: chest `facing` block state is restricted to the four cardinals by the game, and even a hypothetical degenerate face (e.g. modX=modZ=0) would produce offset (0,0), point at the chest itself, and be rejected by `isComplementaryHalf` (`DoubleChestGrouper.kt:80-85`) since the type would not be the opposite half — so grouping behaviour is unchanged either way.
- **POSIX guard is correct.** `Files.setPosixFilePermissions` declares `UnsupportedOperationException` for non-POSIX providers; catching exactly that and keeping the pre-existing `supportedFileAttributeViews().contains("posix")` fast path means non-POSIX filesystems now proceed with default temp-file permissions instead of crashing `createTemp` (which previously aborted the whole export). No JSON content or file-move behaviour changed; `IOException` from the attribute write was and remains uncaught, which is out of this ticket's scope.
- **Export JSON unchanged.** `normalized` → `orderedChests` is a pure rename (`JsonExporter.kt:33`); the sort key, `DesignJson.encodeToString` call, and everything downstream are untouched.
- **Tests are renames, not weakenings.** `MessagesTest.kt` swaps `Messages.x(...)` for the corresponding internal top-level function one-for-one, including all fallback/blank-reason and no-target-vs-not-a-chest cases; the prefix assertion (`startsWith(Messages.PREFIX)`) and `allMessages()` coverage are preserved. Internal visibility is accessible from the test source set (same module).

## Round 2

### Verdict

Pass. The round-2 edits (message-function renames, `applyWorldReadablePermissions` deriving attribute views from `temp`, `failure()` inlining, `docs/architecture.md` refresh) introduce no behavioural change, and all round-1 guarantees still hold.

### Findings

None.

### Non-findings

- **Renames are pure renames.** `chestEditUsageMessage` → `usageMessage`, `chestEditNamedMessage` → `namedMessage`, etc. (`ChestEditCommand.kt:81-106`); every body string, color, and the `styled` composition are character-for-character identical to the round-1 versions I verified against the old `Messages.kt`. All call sites (`ChestEditCommand.kt:42,60-64`) and `MessagesTest.kt` imports/assertions were updated one-for-one; a grep confirms no name collisions with other top-level functions in the `commands` package and no leftover old names.
- **POSIX guard still correct after the `temp`-derived change.** `applyWorldReadablePermissions(temp)` (`JsonExporter.kt:64-71`) now checks `temp.fileSystem.supportedFileAttributeViews()`. Since `temp` is created by `Files.createTempFile(directory, ...)` (`JsonExporter.kt:59`), it always lives on the same filesystem as `directory`, so the guard is equivalent to the directory-based check — and arguably more precise, since it is the temp file's own provider that must support the attribute write. The `UnsupportedOperationException` catch and the "defaults are fine" fallback are unchanged; no JSON or move behaviour changed.
- **`failure()` inlining is behaviour-neutral.** `UiDesignerCommand.save` (`:100-105`) still maps any exporter exception to `SaveOutcome.WriteFailed(outputFile, e.message)` and `reload` (`:117-119`) still maps any exception to `ReloadOutcome.Failed(e.message)`; the inlined try/catch blocks are semantically identical to the previous wrapper.
- **`partnerOffset` and grouping untouched.** `DoubleChestGrouper.kt:114-122` is byte-identical to the version I verified in round 1 against the old when-table.
- **Tests not weakened.** `MessagesTest.kt` keeps every round-1 assertion (prefix, palette, aqua prefix child, per-message color/wording, fallback hints, no-target vs not-a-chest distinction) and the full 20-entry `allMessages()` coverage; only the function names in the calls changed.
- **`docs/architecture.md` refresh is documentation-only** and accurately describes the new split (out of my scope, noted only because it was part of this round's diff).
