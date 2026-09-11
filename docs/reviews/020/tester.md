# Tester review — 020 (Data model and JSON export)

## Round 1

### Verdict
Ship. All four acceptance criteria have direct tests at the right layer (plain
JUnit, Bukkit-free `model`/`export`), and the suite is small and behaviour-focused
rather than padding a coverage number. The remaining findings are low-severity
gaps worth one or two cheap additions, not blockers.

### Issues

#### 1. No escaping/unicode case in the exact-output contract (severity: low)
- Location: `src/test/kotlin/dev/cypdashuhn/uidesigner/export/JsonExporterTest.kt:16-37` and `:122-158`; `src/test/kotlin/dev/cypdashuhn/uidesigner/model/UiChestTest.kt:10-31`
- Problem: every `name`/`item` value in the suite is ASCII-safe (`"Shop"`,
  `"Stone"`, `"minecraft:diamond"`). The exporter's central promise is an
  *exact* JSON document, but nothing pins the escaping of `"`, `\`, control
  characters, unicode, or legacy `§` formatting codes, which are plausible in
  item display names and chest names. Today `kotlinx.serialization` handles
  this, so the risk is low — but a future hand-rolled writer or config change
  could mangle these and the suite would stay green.
- Suggested fix: extend the round-trip test (or add one snapshot slot) with a
  value such as `name = "Café \"Special\" \\"`. One small assertion, no new
  test class.

#### 2. Missing-parent-directory branch of `export` is untested (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/export/JsonExporter.kt:18`; `src/test/kotlin/dev/cypdashuhn/uidesigner/export/JsonExporterTest.kt:90-114`
- Problem: the export test always writes into an existing `@TempDir`, so
  `Files.createDirectories(absolute.parent)` is never exercised. That is real
  code the exporter runs (e.g. the configured output path may not exist yet),
  and deleting it would not fail any test. The atomicity/overwrite behaviour is
  covered; this one branch is not.
- Suggested fix: add one test that exports to `directory.resolve("nested/design.json")`
  and asserts the file exists with the expected content.

#### 3. Tie-break for equal canonical positions is unspecified and untested (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/export/JsonExporter.kt:29-31`
- Problem: `sortedBy { it.position }` is a stable sort, so two chests with the
  same `BlockPos` keep input order, but `docs/data-format.md:38-39` does not say
  what the tie-break is and no test pins it. In practice a canonical block is
  unique per chest, so this is unlikely to occur — but if the exporter is ever
  fed hand-built models (as these tests do), the order of ties is silent
  behaviour that a refactor (e.g. `sortedWith`) could change unnoticed.
- Suggested fix: pick one — either state in `docs/data-format.md` that
  equal-position chests are left in input order (stable) and leave it, or drop
  the ambiguity by noting positions are unique and add no test. Do not add a
  test that locks in stable-sort unless the behaviour is documented; testing
  unspecified incidental ordering would be the wrong kind of brittleness.

### Non-issues
- **All four acceptance criteria are covered, each by the right test.**
  Round-trip: `UiChestTest.kt:10-31`. Exact named+unnamed snapshot:
  `JsonExporterTest.kt:16-37` against `EXPECTED_SNAPSHOT` (`:122-158`).
  Deterministic ordering of chests, rows and slots:
  `JsonExporterTest.kt:39-66`. Empty input `[]`: `:85-88`.
- **The hard-coded snapshot is correct, not brittle.** The ticket explicitly
  asks for an exact expected JSON string matching `docs/data-format.md`, so
  asserting indentation, key order and omission of `name` is the contract under
  test, not incidental structure. It will only break when the format itself
  changes, which is exactly when it should.
- **The snapshot test is not tautological despite using `Json` indirectly.**
  It pins a literal string, so it independently catches a wrong `Json` config
  (e.g. `encodeDefaults = true` would emit `"name": null`), whereas the
  round-trip test would not. The two tests cover different failure modes.
- **`export` content assertion using `toJson` is fine.** `:107` compares the
  written file to `JsonExporter.toJson(chests)`, which looks circular, but the
  snapshot test independently fixes `toJson`, so together they verify the file
  path writes the same document and replaces a pre-existing file (`:95`).
- **The "no temp file" assertion is a reasonable proxy for atomic write.**
  `:108-113` asserts the directory contains only `design.json`, so a leaked
  `uidesigner-export-*.tmp` fails. True atomicity is not observable from a unit
  test without FS injection; this is the right depth.
- **No test for the `AtomicMoveNotSupportedException` fallback or failure-path
  temp cleanup** (`JsonExporter.kt:23-26`, `:52-54`). Triggering these needs FS
  mocking for little value; the success path and observable end state are
  covered. Not worth adding.
- **No direct `BlockPos.compareTo` test.** The ordering test at `:39-66` uses
  positions `(0,0,0)`, `(0,0,1)`, `(0,1,0)`, `(1,0,0)` and asserts the resulting
  name order, which exercises x-then-y-then-z precisely. A dedicated comparator
  test would duplicate that coverage; omitting it is the right call.
- **No test that `position` is never serialized.** It is covered indirectly:
  the two-chest snapshot would fail if `position` appeared, and
  `Json.decodeFromString<List<UiChest>>` in the ordering test would carry the
  wrong positions. Adequate.
- **No test for a slot with an empty/blank item or an empty-string chest
  `name`.** The model makes `item` non-null and represents "unnamed" as `null`
  (`UiChest.kt:29,45`), so `""` is a caller contract handled by the capture
  layer, not the exporter. Correctly out of scope here.
- **No test for `rows` deriving from single vs. double chest.** That is capture
  logic, explicitly out of scope per the ticket.
- **No test asserting `model`/`export` are Bukkit-free.** That is a structural
  property better caught by the architecture review / import rules than by a
  behavioural test; a reflection/classpath assertion would be heavier than the
  risk.
- **No excessive tests.** Six tests across two classes, each mapped to an
  acceptance criterion or a concrete branch; there is no snapshot noise or
  implementation-detail duplication to trim.
