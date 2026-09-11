---
name: Command UX polish
status: done
parent: Polish
depends-on: [060]
reviewers: [ux, readability, correctness]
---

## Goal
Make the command loop pleasant: clear help, consistent messages, tab
completion, and permission handling.

## Scope
- `/uidesigner help` and no-arg behaviour listing subcommands.
- Consistent Adventure-styled messages and a plugin prefix in `Messages`.
- Tab completion for subcommands and, where useful, argument suggestions.
- Permission checks for every command; graceful denial message.
- `/chest-edit` help/usage and completion.
- Review all error paths for actionable wording.

## Acceptance criteria
- Running `/uidesigner` with no args prints help.
- Tab completion lists subcommands.
- A player without permission gets a clear denial, not a stack trace.
- Messages are consistent in colour/prefix across commands.

## Out of scope
- New functionality.

## Notes
- Prefer centralising messages in `util/Messages.kt`; no inline literals
  scattered across commands.
