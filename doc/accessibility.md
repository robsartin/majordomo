# Accessibility (WCAG 2.1 AA)

Majordomo's server-rendered pages target **WCAG 2.1 AA**. This page is the
checklist to run against every new or changed Thymeleaf template, plus a note on
how it is verified.

## Per-page checklist

Structure & landmarks
- [ ] `<html lang="en">` is set.
- [ ] Exactly one `<main id="main-content" tabindex="-1">` wraps the primary content.
- [ ] Page has a single, meaningful `<h1>`; headings nest without skipping levels.
- [ ] Navigation is in a `<nav>` with an accessible name (`aria-label`); the app
      shell provides the "Skip to main content" link (header fragment) and the
      primary `<nav aria-label="Primary">` (sidebar fragment) — reuse them.

Keyboard & focus
- [ ] Every interactive control is reachable and operable with the keyboard alone.
- [ ] Focus is always visible (`focus:ring-2 focus:ring-…` or `focus-visible`);
      never `outline:none` without a replacement.
- [ ] Actions that change state are real `<button>`s in a form (not clickable
      `<div>`/`<span>`), so they are keyboard-operable and announced as buttons.
- [ ] Tab order follows reading order (no positive `tabindex`).

Forms
- [ ] Every `<input>`, `<select>`, `<textarea>` has an accessible name — an
      associated `<label for>` (preferred) or an `aria-label` for grid/repeating
      fields where a visible per-field label is impractical.
- [ ] `placeholder` is **not** used as the only label.
- [ ] Validation errors are associated with their field (`th:errors`) and are
      text, not colour alone.
- [ ] Required fields use the `required` attribute.

Colour & content
- [ ] Text contrast ≥ 4.5:1 (≥ 3:1 for large text); interactive/state colours
      (links, badges, focus rings) also meet contrast.
- [ ] Information is never conveyed by colour alone (pair with text/icon/label).
- [ ] Images have `alt` (empty `alt=""` for decorative); icon-only controls have
      an `aria-label`.
- [ ] Tables use `<th>` header cells.

## How it's verified

- **Automated render checks:** `AccessibilityLandmarksTest`
  (`src/test/java/com/majordomo/adapter/in/web/AccessibilityLandmarksTest.java`)
  renders representative pages through the real controllers and asserts the
  structural invariants (lang, single `<main id="main-content">`, `<h1>`, skip
  link, labelled primary nav). Add a case when you add a new top-level page.
- **Manual pass:** before merging a UI change, tab through the page start to
  finish, confirm visible focus and that the skip link works, and spot-check
  contrast (browser devtools / a contrast checker).

New shared UI (fragments) should bake these in so individual pages inherit them.
