# 10. Require human review and branch-based development workflow

Date: 2026-03-24

## Status

Accepted

## Context

Majordomo development may involve AI-assisted code generation. Regardless of how code is produced, only robsartin is accountable for what lands in the codebase. We need a workflow that ensures every change is intentionally reviewed and approved before it reaches main.

## Decision

All code committed to this repository must be either written or reviewed by robsartin. No code reaches main without explicit human approval.

The development workflow is:

1. **Branch** — All work happens on a fresh branch created from main. No commits directly to main.
2. **Develop** — Commits are made to the feature branch following strict TDD (see ADR-0003).
3. **Pull Request** — When work is complete, a merge PR is submitted against main.
4. **Review** — robsartin reviews and approves the PR before merging.
5. **Merge** — The PR is merged to main. The feature branch is deleted.

Rules:

- No direct pushes to main.
- No merging without a PR.
- AI-generated code is treated the same as any other contribution — it must be reviewed and approved by robsartin.

## Consequences

- Main is always in a known-good, reviewed state.
- Every change has a PR as its audit trail, linking the diff to its review and approval.
- AI tooling can accelerate development without reducing accountability — robsartin remains the gatekeeper.
- Solo development with mandatory PRs adds a small amount of process overhead, but the review step catches issues before they reach main.
