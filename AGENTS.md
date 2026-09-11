# AGENTS.md

Operating manual for agents working in `mc-ui-designer`.

## What this is

A Paper plugin that turns chests placed in a world into a JSON description of a
UI. The user designs in-game (places/fills/names chests), selects the region
with Fast Async WorldEdit, and runs a command to export the design to JSON. The
file is later handed to an agent to implement the real UI.

Read these before working:

- `docs/design.md` — goal, scope, decisions, naming.
- `docs/data-format.md` — the authoritative JSON schema.
- `docs/architecture.md` — package layout and seams.
- `docs/workflow.md` — the review pipeline and report/ticket formats.
- `docs/fcp.md` — how to correlate source files with docs.
- `docs/tasks/` — the tickets.

## Stack

Paper `26.2`, Java `25`, Kotlin `2.4.10`, Gradle Kotlin DSL. CommandAPI
(`commandapi-paper-shade`), `kotlinx-serialization-json`, FAWE (`compileOnly`),
JUnit 5 + MockBukkit. Full list and rationale in `docs/design.md`.

## Commands

```sh
just build      # shaded plugin jar
just run        # dev server on localhost:25000 with FAWE
just test       # JUnit suite
just format     # ktlint
```

(If `justfile` targets do not exist yet, they are created by ticket 000.)

## Orchestration model

The primary agent is an **orchestrator**: it does not implement. It plans,
writes tickets, delegates to subagents, and administers git.

- Subagents live in `.opencode/agent/`: `implementor`, `tester`,
  `correctness`, `architecture`, `readability`, `ux`.
- `implementor` is the only agent that edits source.
- Reviewers are read-only except for their own report file,
  `docs/reviews/<ticket-id>/<role>.md`; they never touch source. Report format
  is in `docs/workflow.md`.
- Per ticket: implement, then run the ticket's `reviewers` in order, hand the
  reports back to `implementor`, commit, and repeat for a second round.
- Omit reviewers that are irrelevant; keep the order of those that remain.
- Only `implementor` runs slow verification (build, `just test`, `just
  format`, the dev server) and must leave the tree green. Reviewers read and
  reason; they do not re-run it.

## Git

- You own the repository: branches, worktrees, staging.
- Conventional commits: `type(domain): description`, imperative, lowercase,
  ≤72 chars, no trailing period, no tool/authorship trailers.
- Stage precisely; never `git add -A`.
- Commit after each completed work unit. Never push unless asked.

## Conventions

- No code comments unless they explain a non-obvious *why*.
- Keep `model`/`export` free of Bukkit imports; isolate FAWE behind
  `SelectionSource`.
- Update docs in the same commit as the change that invalidates them.
- Format with ktlint per `.editorconfig`.

## Current status

Planning/setup only. No code has been written yet. Start with ticket
[`docs/tasks/000-project-setup.md`](docs/tasks/000-project-setup.md).
