# 3. Use strict test-driven development

Date: 2026-03-24

## Status

Accepted

## Context

Majordomo is a service-based system where services interact across well-defined boundaries. Confidence in correctness at both the unit and integration level is critical. We need a development discipline that keeps design emergent, prevents speculative complexity, and produces a reliable test suite as a byproduct of normal work.

## Decision

We will follow strict TDD using the red-green-refactor cycle:

1. **Red** — Write one failing unit test that describes the next small increment of behavior.
2. **Green** — Write the minimum code to make that test pass.
3. **Commit** — Commit the passing test and implementation together.
4. **Refactor** — Improve the design (remove duplication, clarify names, extract abstractions) while keeping all tests green. Commit again if changes were made.

Rules:

- No production code is written without a failing test driving it.
- Only one test is added at a time. Do not write a batch of tests up front.
- The green step does the simplest thing that works — no anticipatory design.
- Refactoring is where design emerges. It is not optional, but it must not change behavior.
- Each red-green and refactor cycle produces its own commit, keeping the history bisectable and reviewable.

## Consequences

- The codebase will have high test coverage as a natural side effect, not as a separate effort.
- Commits will be small and focused, making code review and git bisect effective.
- Design emerges from real requirements rather than speculation, reducing unnecessary abstraction.
- The discipline requires practice and can feel slow at first, but prevents costly rework later.
- Developers must resist the urge to write code ahead of tests or to batch multiple behaviors into one cycle.
