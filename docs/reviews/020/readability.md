# Readability review — 020 (Data model and JSON export)

## Round 1

### Verdict
Ship with fixes. Both source files are small, direct and comment-free, the tests
are named as backticked sentences with tidy helpers, and `docs/data-format.md`
was updated in lockstep with the code. Four small things slow a reader down: the
shared `Json` value shadows the library type it is built from, `ordered` hides
that it also drops rows, and two doc lines lag the new `BlockPos`/row-omission
behaviour.

### Issues

#### 1. `val Json` shadows `kotlinx.serialization.json.Json` (severity: medium)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/model/UiChest.kt:7-11`
- Problem: The file imports `kotlinx.serialization.json.Json` and then declares a
  top-level value with the same name, initialized by calling that class
  (`val Json = Json { ... }`). Every reader who sees `Json.decodeFromString(...)`
  in `UiChestTest.kt:28` or `JsonExporterTest.kt:61` will read it as the library's
  default instance, not the project's `prettyPrint`/`encodeDefaults = false`
  config. The name also gives no hint that this is *our* output policy, and it
  sits in `model` although it exists to shape exported JSON. `fcp query` finds no
  doc for the file, so the only signpost is `docs/data-format.md:63-66`.
- Suggested fix: rename the value to something intention-revealing
  (`DesignJson`, `UiDesignerJson`) and let its type stay `Json`, or move it next
  to `JsonExporter` where the formatting policy is used. Update the
  `docs/data-format.md:63-66` sentence and the three call sites accordingly.

#### 2. `ordered` under-describes what it does (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/export/JsonExporter.kt:29-42`
- Problem: `ordered(chests)` sorts chests, sorts rows and slots, **and** drops
  rows with no slots (`:38`). A reader scanning the public surface (`toJson`,
  `export`, `ordered`) will not expect the filtering from the name, and the
  separate `UiRow.ordered()` extension (`:42`) only sorts, so the same word means
  two different things one line apart. The acceptance criterion is about stable
  ordering plus omission, so both behaviours belong in the name.
- Suggested fix: rename the list function to `normalized` / `exportable` (and
  leave `UiRow.ordered()` as the pure sorter), or split the
  `.filter { it.slots.isNotEmpty() }` out of `ordered` so each function has one
  job.

#### 3. `docs/architecture.md` model listing omits `BlockPos` (severity: low)
- Location: `docs/architecture.md:11` vs `src/main/kotlin/dev/cypdashuhn/uidesigner/model/UiChest.kt:13-25`
- Problem: The architecture map is the first place a newcomer looks to learn what
  `model/UiChest.kt` holds, and it still reads
  `UiChest / UiRow / UiSlot + Json config`. `BlockPos` is now a public type in
  that file (and is part of the documented model in `docs/data-format.md:60`), so
  the map and the schema disagree about the file's contents.
- Suggested fix: extend the line to `UiChest / UiRow / UiSlot / BlockPos + Json
  config`.

#### 4. "Empty slots are omitted" has no counterpart in this change (severity: low)
- Location: `docs/data-format.md:33` vs `JsonExporter.kt:38`
- Problem: The rule is stated next to "A row with no items is omitted entirely",
  which the exporter *does* implement, but the model has no empty-slot concept —
  `UiSlot.item` is a non-null `String` (`UiChest.kt:44`), so a slot without an
  item cannot be represented and the exporter never drops one. A reader trying to
  match the rule to the code finds only the row filter and is left unsure which
  half the sentence describes. (It reads as a capture-side rule for a later
  ticket.)
- Suggested fix: scope the sentence to capture ("slots with no item are not
  emitted by the scanner") or drop it until the capture ticket exists.

### Non-issues
- **Comments.** None in the four changed files; the atomic-write fallback and the
  `encodeDefaults = false` rationale are self-evident or already explained in
  `docs/data-format.md:63-66`, so no `why` comment is missing.
- **Unused imports / dead code.** Every import in all four files is used; every
  private helper (`ordered`, `UiRow.ordered`, `move`, the test `chest`/`slot`
  helpers) has a caller.
- **Test readability.** Names are backticked sentences describing behaviour
  (`a chest survives a JSON round trip`, `rows without items are omitted`,
  `empty input produces an empty JSON array`). The `EXPECTED_SNAPSHOT`
  `trimMargin()` block mirrors the schema order, and the `Files.list(...).use`
  check is resource-safe and obvious.
- **Control flow.** `export` is a straight
  absolute-path → create dirs → temp → write → move with a single cleanup
  `catch`/rethrow; `BlockPos.compareTo` is an explicit x/y/z ladder rather than a
  clever one-liner. No double negations or needless locals (`absolute` and
  `directory` are each used twice).
- **Formatting.** No line exceeds the `.editorconfig` 100-column limit; the
  `val Json =` line break, annotation placement and indentation match ktlint.
  The trailing comma after `UiChest.position` (`UiChest.kt:32`) but not on
  `UiRow`/`UiSlot` is allowed because
  `ktlint_standard_trailing-comma-on-declaration-site` is disabled, and it
  matches the snippet in `docs/data-format.md:47-52`.
- **Docs sync.** `docs/data-format.md` was correctly updated for name omission
  (`:12`, `:34-35`), mandatory row omission (`:33`), the `BlockPos`/`position`
  model (`:51`, `:60`, `:63-66`) and the delta table (`:76`); no other doc claims
  the old `"name": ""` behaviour.
- **Hygiene.** `git status` shows only the expected new source/test trees plus
  the `docs/data-format.md` edit — no temp files, scratch notes or stray
  artifacts.

## Round 2

### Verdict
Ship with one small fix. Round-1 issues 1, 3 and 4 are resolved cleanly, and
issue 2 is resolved the way round 1 suggested (`normalized` + a pure
`UiRow.ordered()`). The fixes introduced one new subtlety worth addressing:
`normalized` validates positions twice, and the discarded first call reads like
dead code while actually being load-bearing. One minor doc/identifier drift
remains after the rename.

### Issues

#### 1. `normalized` validates positions twice; the discarded call reads as dead code (severity: medium)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/export/JsonExporter.kt:36-39`
  (helper at `:52-53`)
- Problem: `chests.forEach { it.requiredPosition() }` throws away its result,
  then `sortedBy { it.requiredPosition() }` calls the same function again. A
  reader reasonably concludes the first line is redundant and may delete it —
  but it is load-bearing: Kotlin's `sortWith` returns early for lists of size
  ≤ 1, so `sortedBy` never invokes its selector for the single-chest case. The
  documented "fails fast" behaviour and its test (`JsonExporterTest.kt:75-79`)
  depend on the explicit pass. Because the reason is non-obvious, the line reads
  as noise instead of intent, and removing it would silently change behaviour.
- Suggested fix: fold the validation into the mapping so the selector is always
  invoked and only called once, e.g.
  `chests.map { it to it.requiredPosition() }.sortedBy { (_, position) -> position }`
  then map the chests; or keep the current shape and add a one-line `why`
  comment (allowed by the no-comments convention for exactly this case) noting
  that `sortedBy` skips its selector for single-element lists.

#### 2. `docs/architecture.md:11` still says "Json config" after the rename (severity: low)
- Location: `docs/architecture.md:11`
- Problem: Round 1 renamed the value to `DesignJson` and `docs/data-format.md:71`
  names it correctly, but the architecture map still describes `UiChest.kt` as
  `... + Json config`. The map is the first place a newcomer looks, so it should
  use the identifier the file actually declares. The same stale identifier sits
  in `docs/tasks/020-model-and-json.md:33` (`Json { prettyPrint = ... }`).
- Suggested fix: change the line to
  `UiChest / UiRow / UiSlot / BlockPos + DesignJson config`, and update the
  ticket note if ticket docs are kept in sync.

### Non-issues
- **Round-1 issue 1 resolved.** The shadowing value is now `DesignJson`
  (`UiChest.kt:7-11`) and every call site/import was updated
  (`JsonExporter.kt:4,20`, `UiChestTest.kt:28`, `JsonExporterTest.kt:4,67,93,109`);
  `docs/data-format.md:71` names it. No remaining `Json` value reference outside
  the library import and the `Json { }` builder call.
- **Round-1 issue 2 resolved.** `normalized` (`JsonExporter.kt:36`) replaced the
  ambiguous `ordered`, and `UiRow.ordered()` (`:55`) is again a pure sorter, so
  the word no longer means two things one line apart. `normalized` still both
  sorts and drops empty rows, but that is the single place for both and the name
  is the umbrella round 1 proposed; acceptable.
- **Round-1 issue 3 resolved.** `docs/architecture.md:11` now lists `BlockPos`.
- **Round-1 issue 4 resolved.** `docs/data-format.md:33` scopes the empty-slot
  rule to the scanner and leaves row omission to the exporter.
- **New helpers read well.** `requiredPosition` (`JsonExporter.kt:52`) and
  `createTemp` (`:57`) have intention-revealing names; `WORLD_READABLE` (`:17`)
  and the posix branch (`:58-61`) are self-explanatory without a comment.
- **Test helpers.** `chest`/`slot` (`JsonExporterTest.kt:193-197`) mirror the
  model with sensible defaults and no hidden behaviour; test names remain
  backticked sentences and the snapshot blocks are unchanged and schema-ordered.
- **Comments / dead code.** No comments were added; the only discarded statement
  is the one in issue 1. Every import in all four files is used.
- **Formatting / hygiene.** No line exceeds the 100-column limit; the
  `DesignJson` initialiser, chain wrapping and trailing commas match the
  disabled-trailing-comma settings; `git status` shows no scratch or temp files
  and only the expected docs edits.
