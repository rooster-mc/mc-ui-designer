# Using FCP

[`fcp`](/home/cyp/repos/fcp) (File Correlation Protocol) correlates source
files with the docs that describe them, based on `fcp:` frontmatter in the
markdown docs. We use it to answer "which doc explains this file?" and "which
files does this doc cover?" without guessing.

## Use it

From this repository root:

```sh
fcp query <source-file> --docs-dir docs --project-root .
fcp covers <doc-file> --docs-dir docs --project-root .
```

Both print a JSON array. `query` returns docs that explain a source file;
`covers` returns source files a doc claims to cover.

## Frontmatter

A doc opts in by declaring `fcp:` frontmatter:

```markdown
---
fcp:
  scope:
    - "src/main/kotlin/dev/cypdashuhn/uidesigner/capture/"
  match:
    - "class ChestScanner"
  score: 0.8
---
```

- `scope` — path prefixes (relative to project root) the doc may cover. Empty
  or omitted = whole project.
- `match` — ripgrep-style queries a source file must hit; plain strings are
  shorthand for `{ query, tool: ripgrep }`. Empty or omitted = everything in
  scope.
- `score` — `0.0..1.0`, passed through verbatim. Default `1.0`.

## When to write/update docs

When a change makes an existing doc wrong, update it in the same commit. When
adding a new subsystem, add or extend the doc that covers it. Use
`fcp query <changed-file>` to find the doc that should be updated.
