# Tasks

Tickets for this repository. Each ticket is one feature / part of code that
goes through the review pipeline in `docs/workflow.md`.

## Index

| Id | Title | Parent | Depends on | Status |
|---|---|---|---|---|
| [000](000-project-setup.md) | Project setup and dev server | MVP | — | done |
| [010](010-config.md) | Config loading and default output path | MVP | 000 | done |
| [020](020-model-and-json.md) | Data model and JSON export | MVP | 000 | done |
| [030](030-selection-capture.md) | FAWE selection capture and chest scan | MVP | 020 | done |
| [040](040-double-chest-grouping.md) | Double-chest grouping | MVP | 030 | done |
| [050](050-chest-naming.md) | `/chest-edit` naming | MVP | 000 | done |
| [060](060-export-command.md) | `/uidesigner save` export command | MVP | 010, 020, 030, 040, 050 | done |
| [070](070-ux-polish.md) | Command UX polish | Polish | 060 | done |
| [080](080-adopt-rooster-region.md) | Adopt rooster-region for capture | MVP | 060 | done |

## Template

```markdown
---
name: Human readable title
status: todo
parent: MVP
depends-on: []
reviewers: [tester, correctness, architecture, readability, ux]
---

## Goal
What this ticket delivers, in one or two sentences.

## Scope
- Bullet list of what is included.

## Acceptance criteria
- Verifiable statements.

## Out of scope
- Explicit non-goals.

## Notes
- Decisions, references, gotchas.
```
