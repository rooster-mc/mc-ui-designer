---
name: Consume rooster-commands root executor and suggestion dedupe
status: done
parent: Polish
depends-on: ["100"]
reviewers: [tester, correctness, architecture, readability, ux]
---

## Goal
Both `rooster-commands` API gaps recorded during ticket 100 are now fixed in
the sibling repo at commit `f0c2606`:
1. `Factory.command` takes a `CommandScope`, so a root `onExecute { }` replaces
   the raw `CommandTree.executes(CommandExecutor { ... })` touch in this repo.
2. The compiler dedupes suggestions against sibling literals
   (`List<Suggestion>.excludingLiterals`), so a greedy node may suggest a value
   that is also a literal sibling without producing duplicates.

Consume both, removing the last direct CommandAPI executor usage and restoring
the `/chest-edit` `clear` suggestion.

## Scope
- `commands/ChestEditCommand.kt`: move the bare-command usage executor into the
  `command("chest-edit") { ... }` block via root `onExecute`; delete the
  `CommandExecutor` import and the `usageExecutor` local. Add
  `.suggestStrings { listOf("clear") }` to the optional greedy `name` so typing
  a partial value suggests `clear`; the compiler now filters it against the
  `clear` literal sibling.
- `commands/UiDesignerCommand.kt`: same root-executor change for the help
  executor; delete the `CommandExecutor` import. Keep the `uid` alias and all
  message content identical.
- Update any doc that still describes `.executes(...)` or the dropped
  suggestion (check `docs/design.md`, `docs/architecture.md`).

## Out of scope
- Further `rooster-commands` changes (shipped in `f0c2606`).
- The `ChestScanner` → `rooster-region` backlog (ticket 120).

## Acceptance criteria
- `just build`, `just test`, `just format` pass.
- No `CommandExecutor` usage remains in this repo.
- `/chest-edit` and `/uidesigner` behave as before; typing a partial name
  suggests `clear`, and `clear` appears once, not twice.
- Resolved TODO markers/history notes in the touched files are gone.

## Notes
- The library change lives in `/home/cyp/repos/rooster-commands` and is picked
  up through the existing composite build; no publish step.
