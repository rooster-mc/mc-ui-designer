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
