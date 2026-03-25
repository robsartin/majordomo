# 19. Use Tailwind CSS for server-rendered UI

Date: 2026-03-25

## Status

Accepted

## Context

Majordomo serves Thymeleaf-rendered web pages (login, home, dashboard, property detail). These pages need consistent, professional styling that is responsive and accessible. We need a CSS approach that integrates well with server-rendered HTML templates.

Alternatives considered:

- **Bootstrap**: Full component library with opinionated design. Easy to start but hard to customize without looking like every other Bootstrap site. Heavy bundle for utility usage.
- **Plain CSS**: Maximum control but high effort for responsive layouts, component patterns, and cross-browser consistency.
- **Material/MUI**: Designed for React/SPA, poor fit for server-rendered Thymeleaf templates.

## Decision

We will use Tailwind CSS for all server-rendered UI pages.

- Delivery: CDN (`<script src="https://cdn.tailwindcss.com">`) for development simplicity. Build-time compilation with PostCSS when the UI grows beyond a handful of pages.
- Design approach: utility-first classes applied directly in Thymeleaf templates.
- Accessibility: WCAG AA color contrast required. Semantic HTML elements used alongside Tailwind utilities.
- Responsive: mobile-first design using Tailwind breakpoint prefixes (`sm:`, `md:`, `lg:`).

## Consequences

- Rapid prototyping without custom CSS files.
- Consistent design language across all pages via shared utility patterns.
- Templates are slightly more verbose (class lists) but self-documenting.
- CDN approach has no build step; switching to PostCSS compilation later is straightforward.
- Team must learn Tailwind's utility class conventions.
