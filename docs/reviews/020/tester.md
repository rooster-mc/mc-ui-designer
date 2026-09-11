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

## Round 2

### Verdict
Ship. All three round-1 tester issues are resolved and the five tests added in
response to reviewer feedback are behaviour-focused and at the right layer
(plain JUnit, no Bukkit). Two low-severity test problems remain: the POSIX
permission test is umask-dependent and quietly only proves the guarantee on the
common `022` umask, and the new `export` failure-path temp cleanup is still
untested even though the fail-fast change makes it cheap to reach.

### Issues

#### 1. POSIX permission test is environment-dependent and hides that the fix does not enforce world-readability (severity: low)
- Location: `src/test/kotlin/dev/cypdashuhn/uidesigner/export/JsonExporterTest.kt:179-191`;
  `src/main/kotlin/dev/cypdashuhn/uidesigner/export/JsonExporter.kt:17-18,57-62`
- Problem: the `assumeTrue` guard only skips non-POSIX filesystems; it does not
  cover the process umask. `Files.createTempFile(..., WORLD_READABLE)` applies
  the requested mode through `open(2)`, so the kernel still masks it with the
  umask. I verified on this machine (JDK 26): under `umask 077` the temp file is
  `rw-------`, under the default `022` it is `rw-r--r--`. Two consequences: on a
  developer/CI machine with a restrictive umask the test goes red with no source
  change (brittle to the environment), and on the usual `022` it passes while the
  exporter's "world-readable" intent is still only a best-effort request, not an
  enforced guarantee. `WORLD_READABLE` fixes the old owner-only default but not
  the umask, so the round-1 correctness fix is incomplete.
- Suggested fix: enforce the mode after creation (e.g. `Files.setPosixFilePermissions(temp,
  PosixFilePermissions.fromString("rw-r--r--"))` inside the existing posix
  branch), so the guarantee holds regardless of umask and the test becomes
  deterministic; then assert the exact permission string rather than two bits.
  If enforcement is deliberately out of scope, at least document the umask
  dependency so the test's red is not mistaken for a regression.

#### 2. `export`'s failure-path temp cleanup is still untested, and fail-fast now makes it easy (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/export/JsonExporter.kt:27-33`;
  `src/test/kotlin/dev/cypdashuhn/uidesigner/export/JsonExporterTest.kt:74-79`
- Problem: the `catch (e: Exception) { Files.deleteIfExists(temp); throw e }`
  branch is never exercised. Round 1 dismissed this as needing FS mocking, but
  the new `requiredPosition` fail-fast means `export` now reaches that catch with
  a plain domain input: `export(listOf(UiChest(...position = null)), target)`
  creates the temp, `toJson` throws `IllegalArgumentException`, and the cleanup
  runs. Deleting the `deleteIfExists` line would leave a leaked
  `uidesigner-export-*.tmp` that no test notices. The `toJson` fail-fast test at
  `:74-79` does not cover this because `export` creates the temp first.
- Suggested fix: add one test that exports a positionless chest into a
  `@TempDir` path, asserts `assertThrows<IllegalArgumentException>`, and asserts
  the directory contains no files (mirroring the existing no-temp assertion at
  `:152-157`). One test, no mocking.

### Non-issues
- **Round-1 #1 (escaping/unicode) is resolved.** `quotes, backslashes and unicode
  are escaped in the output` (`JsonExporterTest.kt:114-127`) pins
  `EXPECTED_ESCAPED_SNAPSHOT` (`:236-256`), which covers `"`, `\` and a
  non-ASCII character in both a chest name and a slot name. The expectation is
  correct: Kotlin raw strings do not process backslashes, so the literal
  `"Café \"Special\" \\"` is exactly the JSON encoding of `Café "Special" \`.
  Control characters and legacy `§` codes are not pinned, but they ride the same
  serializer path and adding more slots would be diminishing returns.
- **Round-1 #2 (missing parent directory) is resolved.**
  `export creates missing parent directories` (`:160-177`) exports to
  `nested/design.json` and reads it back, exercising
  `Files.createDirectories(absolute.parent)` (`JsonExporter.kt:25`). Deleting
  that line now fails the test.
- **Round-1 #3 (equal-position tie-break) is resolved by documentation, as
  recommended.** `docs/data-format.md:41-43` now states that equal positions keep
  input order (stable sort) and that canonical positions are expected unique. No
  test locks in the stable-sort edge, which is the right call: it is an
  invariant-backed edge case, and a test would pin incidental ordering. The
  fail-fast `requireNotNull` (`JsonExporter.kt:52-53`) already guards the more
  likely failure mode (missing position).
- **The blank-name test picks the stronger case.**
  `a blank chest name is treated as unnamed` (`:98-112`) uses `"   "`, which
  fails if the normaliser used `isNotEmpty()`; `isNotBlank()` also covers the
  documented `""`, so a second `""` case would duplicate coverage.
- **The fail-fast position test is at the right layer and pins the contract.**
  `:74-79` asserts `IllegalArgumentException` from `toJson` for a positionless
  chest, matching `requireNotNull` (`JsonExporter.kt:52-53`) and the doc's
  "fails fast ... instead of silently falling back to input order"
  (`docs/data-format.md:67-72`). It does not assert the exception message, which
  is fine.
- **The second full snapshot is mild duplication but earns its place.** It
  repeats the document shape to change two name values; a substring assertion
  would be less readable and would not prove the rest of the document is
  unchanged. Given the ticket explicitly requires an exact-string contract, this
  is acceptable, not snapshot noise.
- **No excessive tests.** Ten tests across two classes, each mapped to an
  acceptance criterion (round-trip, exact snapshot, ordering, `[]`) or a concrete
  branch (row omission, blank name, escaping, missing dir, no-temp, permissions);
  nothing is duplicated or implementation-shaped.
- **`export creates missing parent directories` not re-asserting "no temp file"
  is fine.** The sibling test `:134-158` already covers the success-path cleanup,
  so re-asserting it per new test would be redundant.
- **The POSIX test's guard matches the production branch.** Both use
  `supportedFileAttributeViews().contains("posix")` (`JsonExporterTest.kt:183`
  vs `JsonExporter.kt:58`), so the test exercises exactly the branch the code
  takes on that filesystem; the gap is umask (issue 1), not branch selection.
