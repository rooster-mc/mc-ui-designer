---
name: "/chest-edit naming"
status: done
parent: MVP
depends-on: [000]
reviewers: [tester, correctness, ux]
---

## Goal
`/chest-edit <name>` names the chest block the player is looking at;
`/chest-edit clear` removes the name. The name is read during export.

## Scope
- `ChestEditCommand` using CommandAPI, top-level `/chest-edit`.
- Target the chest within the player's reach / line of sight. Reject when the
  looked-at block is not a chest.
- Store the name so it survives restarts and is visible to the player. Decide
  between the chest's custom display name (visible in the GUI) and a
  PersistentDataContainer entry; document the choice.
- `ChestNamer` exposes `nameOf(block)` / `setName(block, name)` / `clear(block)`
  for the exporter and the command.
- Player feedback on success and on failure (not a chest, no permission).

## Acceptance criteria
- Renaming then exporting a selection produces the name in the JSON `name`.
- Clearing removes it from subsequent exports.
- Names persist across a server restart.
- Tests cover name round-trip and the not-a-chest rejection.

## Out of scope
- Naming item stacks (their names come from the item itself).

## Notes
- If using the chest's custom name, use Adventure `Component`; the exporter
  converts to plain text.
- `/chest-edit` is a separate top-level command, not under `/uidesigner`.
