# Tester review — 120 (Consume rooster-region lazy block enumeration)

## Round 1
### Verdict
Ship with minor fixes. The scanner change is well covered by the reworked
`ChestScannerTest`: multi-chunk enumeration, unloaded-chunk skipping, negative
floor chunk lookup and detached item cloning are all exercised, and dropping the
position-order assertion is justified. Two things need addressing: the set-based
assertion loses duplicate detection, and no `docs/manual-test.md` entry names
ticket 120 for the live double-chest/chunk-loading path MockBukkit cannot model.

### Findings
#### 1. `several single chests are captured` set assertion can no longer detect duplicate positions
- Location: `src/test/kotlin/dev/cypdashuhn/uidesigner/capture/ChestScannerTest.kt:66-74`
- Problem: switching the assertion to `captured.contents.map { it.position }.toSet()`
  is the right call for order-independence, but a set collapses duplicates: if
  `loadedBlockPositions`/`mapNotNull` ever yielded a position twice, this test
  would still pass with a list of 5+ entries. The old list assertion caught that
  incidentally (along with the ordering it over-pinned). Nothing else in this
  file asserts the count for the multi-position single-chunk case.
- Suggested fix: keep the order-independent set comparison but also pin the
  cardinality, e.g. `assertEquals(expected, actual.toSet())` plus
  `assertEquals(expected.size, actual.size)` (or assert `actual.distinct()` equals
  `actual`). Do not restore a sorted-list assertion — that would re-pin the
  enumeration order the ticket explicitly allows to change.

#### 2. No manual-test entry owns ticket 120 for the un-MockBukkit-able scanner path
- Location: `src/test/kotlin/dev/cypdashuhn/uidesigner/capture/ChestScannerTest.kt:77-89,123-142`
  (harness limit); `docs/manual-test.md:10-20` (no `120` row)
- Problem: `ChestScannerTest` can only verify the post-swap behaviour against
  MockBukkit's static chunk model and independent 27-slot blocks. MockBukkit
  cannot form a real `DoubleChest` (already documented in `docs/architecture.md`),
  so on a real server the two halves of a double chest are read from Paper's
  holder-backed inventories, and real chunk-loading at region/chunk borders
  differs from `WorldMock`. The acceptance criterion "same chests as before
  (same positions, same cloned item contents)" therefore has a live-server half
  that this diff leaves untracked for ticket 120. MT-001/MT-003 cover double
  chests for tickets 060/040 but do not name 120 or the new library-backed
  enumeration.
- Suggested fix: record a 120-owned entry in `docs/manual-test.md`, e.g.
  `MT-010 | 120 | Live-server scan parity after the rooster-region enumeration swap: just run, place a real double chest across a chunk border plus a chest in an adjacent unloaded chunk, select the region with FAWE, run /uidesigner save, and confirm each chest block is enumerated exactly once with its own cloned 27-slot contents (the double chest's halves stay separate until the grouper merges them). | unverified`.
  If the ticket-orchestrator judges this already covered by MT-001/MT-003, defer
  explicitly to those rows with that reason rather than leaving it untracked.

### Non-findings
- Removing the `in position order` assertion is correct: scanner order is
  unobservable downstream and `JsonExporterTest.kt:40` (`chests, rows and slots
  are sorted regardless of input order`) pins the normalisation that makes the
  library's chunk-major order safe.
- The new `chests in separate loaded chunks are all captured` test
  (`ChestScannerTest.kt:77-89`) is worthwhile, not excessive: it is the only test
  that forces a second loaded chunk and exercises the library's chunk-major
  enumeration across a chunk boundary.
- Rewriting `chests in unloaded chunks are skipped without error`
  (`ChestScannerTest.kt:154-164`) to place one loaded and one unloaded chest is a
  genuine strengthening: it asserts the loaded entry survives while the unloaded
  one is dropped, instead of only asserting an empty result.
- Negative floor-division chunk lookup is covered
  (`ChestScannerTest.kt:144-152`), and clone independence of the captured items
  is covered (`ChestScannerTest.kt:107-121`); no additional cloning test is
  warranted.
- "No TODO remains" is a static check, not a meaningful unit test; no test
  belongs here for it.
