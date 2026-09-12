---
name: Backlog: rooster-commands API gaps noticed in adoption
status: backlog
parent: Polish
depends-on: []
reviewers: [correctness, architecture, readability]
---

## Goal
Two API gaps in `rooster-commands` were noticed while adopting it in ticket
100. Neither blocks mc-ui-designer; record them on
`/home/cyp/repos/rooster-commands` issue tracker (preferred) or fix them there,
then clean up here if anything becomes unnecessary.

## Deferred from
Ticket 100 (deferral section: "command-tree TODOs that require rooster-commands
API not yet shipped there → note them in ../rooster-commands issue tracker or a
backlog ticket here").

## Items
1. `Factory.command(label, block)` has no root-executor parameter; bare
   `/chest-edit` and `/uidesigner` need a raw `CommandTree.executes(...)`
   afterwards. A `command(label, rootExecutor) { ... }` overload would remove
   the last direct CommandAPI executor touch in this repo.
2. CommandAPI/Brigadier can emit duplicate suggestions when a literal sibling
   and a custom suggestion overlap (`SafeSuggestions`-style); mc-ui-designer
   dropped the greedy node's `clear` suggestion because the literal node
   already suggests it. A dedupe in the suggestion path would make overlapping
   sources safe.

## Acceptance criteria
- Both gaps either shipped in rooster-commands or consciously declined there.

## Out of scope
- Any change to this repo's commands.
