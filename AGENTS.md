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
- `docs/manual-test.md` — the gate for non-automatable acceptance criteria.
- `docs/fcp.md` — how to correlate source files with docs.
- `docs/tasks/` — the tickets.

## Stack

Paper `26.2`, Java `25`, Kotlin `2.4.10`, Gradle Kotlin DSL. CommandAPI
(`commandapi-paper-shade`), `kotlinx-serialization-json`, FAWE (`compileOnly`),
JUnit 6 + MockBukkit. Full list and rationale in `docs/design.md`.

## Commands

```sh
just build      # shaded plugin jar
just run        # dev server on localhost:25000 with FAWE
just test       # JUnit suite
just format     # ktlint
```

## Orchestration model

Two orchestrator tiers, then workers:

- **Meta-orchestrator** (`orchestrator`, primary): owns the ticket queue,
  picks unblocked tickets, manages branches/worktrees, and launches one
  **ticket-orchestrator** per ticket. It never implements or reviews.
- **Ticket-orchestrator** (subagent): runs one ticket end to end by delegating.
  It is the only agent allowed to spawn the workers.
- **Workers** (subagents): `implementor`, `tester`, `correctness`,
  `architecture`, `readability`, `ux`.

Nesting is two levels deep: `orchestrator` → `ticket-orchestrator` →
worker. opencode caps subagent nesting at `subagent_depth` (default `1`,
which forbids subagents from spawning subagents); this repo sets it to `2` in
`.opencode/opencode.json`, and `ticket-orchestrator` declares a `task`
permission (subagents otherwise get the `task` tool denied).

Rules:

- `implementor` is the only agent that edits source.
- Reviewers are read-only except for their own report file,
  `docs/reviews/<ticket-id>/<role>.md`; they never touch source. Report format
  is in `docs/workflow.md`.
- Per ticket: implement, then run the ticket's `reviewers` **concurrently**, hand
  the reports back to `implementor`, commit, and repeat for a second round.
- Reviewers are stateful across rounds: within a round they run in parallel and
  do not see each other's reports (their scopes are exclusive, so overlap is
  not expected). Round 2 resumes the same sessions and gives each reviewer the
  round-1 reports, so nothing fixed there is re-reported.
- Findings carry no severity labels; a finding is work — fixed, or deferred to a
  named ticket/gate with a reason.
- Omit reviewers that are irrelevant; list order is not significant.
- Only `implementor` runs slow verification (build, `just test`, `just
  format`, the dev server) and must leave the tree green. Reviewers read and
  reason; they do not re-run it.
- Non-automatable acceptance criteria are recorded in `docs/manual-test.md`;
  the meta-orchestrator surfaces open entries when reporting a phase done.
- Parallel tickets must be file-disjoint; otherwise the meta-orchestrator
  serializes them or isolates them in separate worktrees.

## Git

- You own the repository: branches, worktrees, staging.
- Conventional commits: `type(domain): description`, imperative, lowercase,
  ≤72 chars, no trailing period, no tool/authorship trailers.
- Stage precisely; never `git add -A`.
- Commit after each completed work unit. Never push unless asked.

## Conventions

- No code comments unless they explain a non-obvious *why*.
- Keep `export` free of Bukkit imports; isolate FAWE behind `SelectionSource`.
- Update docs in the same commit as the change that invalidates them.
- Format with ktlint per `.editorconfig`.

## Current status

Project setup is in place: the Gradle build, wrapper, ktlint, shaded jar,
MockBukkit smoke test and `just` recipes exist, and `just run` boots Paper 26.2
on `localhost:25000` with FAWE and UiDesigner. Continue with the next unblocked
ticket in `docs/tasks/`.
