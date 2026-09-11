# Tester review — 060 (/uidesigner save export command)

## Round 1
### Verdict
Ship with fixes. The pipeline tests hit the right layer (MockBukkit for the
Bukkit-coupled command, fakes for `SelectionSource`/exporter) and cover the
interesting branches: no selection, empty selection, naming, double-chest
grouping, real file write, write failure, reload ordering, and the real
CommandAPI permission surface. The one acceptance criterion with no test at all
is the safety property "a failed write leaves the previous file intact"; the
current write-failure test only proves the outcome mapping.

### Issues
#### 1. Failed-write test does not prove the previous file survives (severity: medium)
- Location: `src/test/kotlin/dev/cypdashuhn/uidesigner/commands/UiDesignerCommandTest.kt:165-176`
  and `src/test/kotlin/dev/cypdashuhn/uidesigner/export/JsonExporterTest.kt:216-231`;
  criterion at `docs/tasks/060-export-command.md:27`.
- Problem: `save reports a write failure without throwing` injects a fake
  exporter that throws, so it proves only that `save` maps the exception to
  `SaveOutcome.WriteFailed`. It cannot prove anything about file state, and the
  fake never touches disk. `JsonExporterTest.a failed export deletes the
  temporary file` is the closest real test, but it never creates a target, so it
  proves "no temp left behind", not "the existing `design.json` is unchanged".
  The acceptance criterion is therefore unverified: a regression that truncates
  the target before writing would keep every test green.
- Suggested fix: add the missing case to `JsonExporterTest` (the layer that owns
  atomicity, `JsonExporter.kt:21-33`). Pre-write the target
  (`Files.writeString(target, "previous")`), call `JsonExporter.export` with a
  chest that has no `position` so `toJson` throws after `createTemp` but before
  the move, and assert `Files.readString(target) == "previous"`. Keep the
  command-level test as the outcome-mapping test it is; do not try to assert
  atomicity through the injected exporter.

#### 2. File-content assertion is coupled to pretty-print formatting (severity: low)
- Location: `src/test/kotlin/dev/cypdashuhn/uidesigner/commands/UiDesignerCommandTest.kt:162`
- Problem: `assertTrue(Files.readString(output).contains("\"name\": \"Shop\""))`
  depends on the exact whitespace of `DesignJson`'s pretty printer. If the
  exporter ever moves to compact JSON the command behaves correctly but this
  test fails. Format stability is already pinned by the exact snapshots in
  `JsonExporterTest`, so this test should assert behaviour instead.
- Suggested fix: decode the written file
  (`DesignJson.decodeFromString<List<UiChest>>(Files.readString(output))`) and
  assert the decoded name/position. That keeps the command test about "the
  command wired the real exporter to the configured path", independent of
  serialization whitespace.

#### 3. Plugin→command registration is not covered (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/UiDesignerPlugin.kt:29-34`;
  `src/test/kotlin/dev/cypdashuhn/uidesigner/UiDesignerPluginTest.kt:24-30`.
- Problem: every dispatch test registers `UiDesignerCommand` itself with
  `MockCommandAPIPlugin`, and `UiDesignerPluginTest` only asserts the plugin
  enables. If the `UiDesignerCommand(...).register()` call were removed from
  `onEnable`, `/uidesigner` would be unreachable yet the suite stays green. This
  ticket is explicitly the integration point, so the wiring is worth one cheap
  assertion. (The same gap was noted as low in 050 round 2.)
- Suggested fix: in `UiDesignerPluginTest`, `MockBukkit.load(UiDesignerPlugin::class.java)`
  and dispatch `uidesigner help` (no permission needed, so no FAWE and no op
  required), asserting the help text is returned. That exercises the real
  `onEnable` registration without needing a selection.

### Non-issues
- **Layer choice is right.** The command is Bukkit/CommandAPI-coupled, so
  MockBukkit plus a fake `SelectionSource` and injected exporter is the correct
  harness; `model`/`export` stay pure and are covered by their own suites. No
  Bukkit leaks into the pure-layer tests.
- **The 050-deferred naming→export seam is now closed.** `save exports named and
  unnamed chests to the configured path` names a chest through `ChestNamer`
  (`:90-92`), and both it and `save writes the exported design to the configured
  file` assert the name reaches the exported list/file. That was the explicit
  hand-off from `docs/reviews/050/tester.md`.
- **The double-chest-as-one-design test earns its place.** `save counts a double
  chest as a single design` (`:119-142`) is more than a duplicate of
  `DoubleChestGrouperTest`: it proves the command counts grouped `UiChest`s
  (`named.size`) rather than `selection.contents.size`, and pins `rows == 6` and
  the canonical position. Removing it would let a count regression through.
- **Permission surface is genuinely exercised.** Op default
  (`dispatch of save exports for an op`), explicit node on a non-op via
  `addAttachment` (`:232-249`), and denial via `CommandSyntaxException` are all
  present for both `save` and `reload`. Asserting the exception type rather than
  the message is the right brittleness level.
- **`help` / bare command / `uid` alias tests are not excessive.** Each covers a
  distinct registration decision (subcommand, root fallback, alias). They are
  cheap and share one assertion helper.
- **Not asserting message text is correct.** The outcome sealed types are the
  tested contract; exact player-facing strings belong to the `ux` review (050
  reached the same conclusion).
- **Reload ordering is adequately proven.** `reload runs the injected reload
  then reads the config` (`:178-200`) mutates the value the provider reads, so
  the returned path can only be the post-reload one. A stricter event-sequence
  assertion would add little.
- **Manual FAWE end-to-end is not testable here and that is fine.** FAWE is
  `compileOnly` and absent from the test classpath, and MockBukkit cannot form a
  real `DoubleChest`; the lazy delegating `SelectionSource` in
  `UiDesignerPlugin.kt:40-44` is validated indirectly by `UiDesignerPluginTest`
  enabling without FAWE. The ticket's dev-server walkthrough remains a manual
  check and should be recorded before `done`, not faked with a test that only
  re-tests MockBukkit.

## Round 2
### Verdict
Ship. All three round-1 tester issues are fixed and covered at the correct
layer, and the tests added for the other reviewers' fixes are proportionate
regression guards. I found no missing, brittle, or excessive tests that warrant
further work; the only unverified item remains the manual FAWE dev-server
walkthrough, which is explicitly out of reach in this harness.

### Issues
None.

### Non-issues (round-1 fixes verified)
- **Issue 1 fixed at the right layer.** `JsonExporterTest.a failed export leaves
  the previous file intact` (`JsonExporterTest.kt:233-244`) pre-writes the target
  with `"previous"`, drives the failure through `toJson`'s
  `requireNotNull(position)` after `createTemp` but before `move`, and asserts the
  target still reads `"previous"`. That closes the truncate-before-write
  regression the round-1 gap allowed, while `a failed export deletes the
  temporary file` (`:216-231`) still covers temp cleanup. Kept in `export`, not
  the command, exactly as recommended; the injected-thrower command test remains
  the outcome-mapping test it should be.
- **Issue 2 fixed.** `save writes the exported design to the configured file`
  (`UiDesignerCommandTest.kt:157-188`) now decodes via
  `DesignJson.decodeFromString<List<UiChest>>` and asserts name/rows/item
  (`:175-187`) instead of matching `"name": "Shop"` whitespace. Pretty-print
  stability is still pinned by the exact snapshots in `JsonExporterTest`, so this
  test is now about the wiring, not the formatter.
- **Issue 3 fixed.** `UiDesignerPluginTest.plugin registers the uidesigner
  command` (`UiDesignerPluginTest.kt:37-50`) loads the real plugin and dispatches
  `uidesigner help` through the `onEnable` registration, then asserts the help
  text. Removing `UiDesignerCommand(...).register()` from `onEnable` now fails a
  test — the integration assertion the ticket's "integration point" framing
  warranted.

### New tests for the other reviewers' fixes
- **Config-path failure (`correctness` #1) is well placed.**
  `save reports an unusable config path without throwing`
  (`UiDesignerCommandTest.kt:206-223`) throws from `configProvider` and asserts
  `WriteFailed(null, ...)`, exercising the catch that now wraps
  `configProvider().outputFile` (`UiDesignerCommand.kt:78-83`). Command layer is
  right: the classification is command behaviour, and the underlying
  `UiDesignerConfig` throw is already covered in `UiDesignerConfigTest`.
- **Console reload/help (`correctness` #4) are legitimate regression guards.**
  `console can reload` (`UiDesignerCommandTest.kt:312-321`) asserts the injected
  reload actually ran from `server.consoleSender`; `console can print help`
  (`:348-354`) proves help no longer uses a player-only executor. Both would fail
  against the pre-fix `executesPlayer` registration, so they earn their place.
  `console can print help` does not assert the text (the player help test already
  does), which is fine at this price.
- **`Region.blockAt` (`architecture` #2) needs no dedicated test.** It is a
  one-line delegate to `World.getBlockAt` (`Region.kt:12`); a direct test would
  only re-assert MockBukkit. It is exercised indirectly through both grouper call
  sites (`DoubleChestGrouper.kt:45,76`, hit by the geometry-fallback and
  orientation tests) and through `nameAt` in `save`
  (`UiDesignerCommand.kt:101`). The refactor is behaviour-preserving and covered.
- **No new brittleness.** The added assertions target outcomes/payloads (decoded
  model, `SaveOutcome`, an executed side effect) rather than message text or
  internal structure, and no snapshots were added. The mildest is
  `UiDesignerPluginTest`'s `message.contains("save")`, the same brittleness level
  as the existing help test.
- **No excessive tests.** The suite grew by four targeted cases (exporter
  atomicity, config-path, console-reload, console-help) plus the plugin
  registration case; each pins a distinct regression the round-1 gaps or fixes
  created, and none duplicates `DoubleChestGrouperTest`/`JsonExporterTest`.

### Not re-litigated
- Correctness #3 (name on the non-canonical half of an unlinked geometry-merged
  double) is deferred and now documented in `docs/design.md`; ux #2 (reload
  false-success wording) is deferred to 070. Neither affects test quality.
- The manual FAWE selection end-to-end (`docs/tasks/060-export-command.md:24-26`)
  remains unverifiable here (FAWE is `compileOnly`, MockBukkit cannot form a real
  selection or linked `DoubleChest`) and must be recorded as an unverified step
  before `done`, not faked with a test that only re-tests MockBukkit.
