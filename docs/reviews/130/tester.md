# Tester review — 130 (Consume rooster-commands root executor and suggestion dedupe)

## Round 1
### Verdict
Ship. The single added test is a genuine integration test that pins the
"`clear` appears once, not twice" criterion, and the moved root executors are
already covered by existing bare-invocation tests. No test-quality work found.

### Findings
No findings.

### Non-findings
- **The new test is load-bearing for the dedupe, not decorative.**
  `CommandAPITestUtilities.assertCommandSuggests` (verified against
  `commandapi-paper-test-toolkit` 11.2.0 bytecode) extracts only
  `Suggestion.getText()` and then calls `Assertions.assertEquals(expected,
  actualTexts)`, i.e. exact list equality including cardinality. So
  `ChestEditCommandTest.kt:197` (`"chest-edit cl"` → exactly `["clear"]`) fails
  if a second `clear` is produced.
- **Brigadier's own `Suggestions.merge` does *not* mask a missing
  `excludingLiterals`.** I traced this because it would make the tests
  vacuous: `StringTooltip.ofString(value, null)` is not null-tooltip
  (`Tooltip.messageFromString(null)` returns `new LiteralMessage(null)`, and
  `ofMessage` only falls back to `none(suggestion)` when the `Message` is
  null), so the greedy node's suggestion carries a non-null tooltip while the
  literal sibling's carries null. Brigadier's `HashSet`-based merge in
  `Suggestions.merge` compares `Suggestion` including tooltip, so the two
  would both survive. The repo test therefore genuinely exercises
  `Compiler.compileNode`'s `.excludingLiterals(siblingLiterals)`
  (`rooster-commands/.../Compiler.kt:167`). Good choice of layer: a real
  dispatcher integration test rather than a library-internal unit test.
- **The `.suggestStrings` call itself is not separately observable, and that is
  fine.** With the sibling `clear` literal present, removing
  `.suggestStrings { listOf("clear") }` still yields `["clear"]`; the tests pin
  the combination (greedy suggestion + sibling-literal exclusion), which is the
  only observable behaviour and exactly what the acceptance criterion asks for.
- **The two tab-completion tests are near-duplicates, but I would keep both.**
  `ChestEditCommandTest.kt:185` (`"chest-edit "`) and `:193` (`"chest-edit cl"`)
  can each only detect "`clear` present exactly once"; because `suggestStrings`
  returns a constant list independent of input, neither can fail where the
  other passes. `"chest-edit cl"` maps directly to the "typing a partial name"
  clause of the acceptance criterion, so it earns its line even though it adds
  no new code path. Not reported as work.
- **`UiDesignerCommand` root-executor change is already covered.** No test was
  added there, correctly: `UiDesignerCommandTest.kt:459` (`bare command prints
  help`) covers root `onExecute` for a player; `:469` (`alias uid resolves the
  command`), `:438` and `:452` cover the literals and messages. A bare
  `/uidesigner` from console is not asserted, but the root handler uses
  `sender.sendMessage`, so the player test already exercises the same path; not
  worth a test.
- **No new `docs/manual-test.md` entry is required for this ticket.** The
  harness runs the real Brigadier dispatcher via the CommandAPI test toolkit,
  so both the partial-name suggestion and the once-not-twice dedupe are fully
  automatable — there is no harness-unreachable path introduced by this diff.
  MT-007 (ticket 100) still covers the live end-to-end command behaviour.
