---
name: JobVault
description: A restrained product UI for explainable job matching, resume parsing, and hiring operations.
colors:
  ink-charcoal: "#111827"
  canvas-white: "#ffffff"
typography:
  body:
    fontFamily: "Arial, Helvetica, sans-serif"
    fontSize: "1rem"
    fontWeight: 400
    lineHeight: 1.5
components:
  page-shell:
    backgroundColor: "{colors.canvas-white}"
    textColor: "{colors.ink-charcoal}"
    typography: "{typography.body}"
  native-link:
    textColor: "{colors.ink-charcoal}"
    typography: "{typography.body}"
  native-control:
    textColor: "{colors.ink-charcoal}"
    typography: "{typography.body}"
---

# Design System: JobVault

## 1. Overview

**Creative North Star: "The Quiet Workbench"**

JobVault is currently a reset product scaffold, not a finished visual system. The implemented frontend uses semantic HTML, browser-native controls, a white canvas, charcoal text, inherited link color, and no custom component styling. This is a deliberate low-decoration baseline for rebuilding task surfaces around resume upload, matching, skill-gap review, authentication, and admin metrics.

The next design layer must stay calm, focused, and human. The interface should feel like a dependable workbench for career and hiring decisions: clear status, direct actions, readable evidence, and no decorative dashboard theater. Matching explanations, parsing failures, authorization state, and operational metrics are product content, not visual garnish.

This system explicitly rejects generic job-board blue, startup-gradient SaaS visuals, playful gamified career-app patterns, and corporate HR portal heaviness. Until richer tokens are introduced in code, agents should add functional styling that improves orientation, accessibility, and trust.

**Key Characteristics:**
- Semantic first: pages use native landmarks, headings, forms, labels, lists, and status text before decorative wrappers.
- Restrained by default: no visual treatment exists unless it clarifies workflow, state, hierarchy, or recovery.
- Evidence-led: scores, skill gaps, IDs, failure codes, and metric values stay legible and close to their labels.
- Operationally honest: admin, auth, error, empty, and loading states receive the same visual discipline as happy paths.

## 2. Colors

The current palette is a bare two-token reset: charcoal text on a white canvas.

### Primary
- **Workbench Ink**: The default text color for all product copy, labels, links, and control text. It should carry the interface until a more complete neutral scale is implemented.

### Neutral
- **Reset Canvas**: The current app background and page surface. It is intentionally undecorated, with no gradients, texture, glass, or brand color fields.

### Named Rules

**The Evidence Before Color Rule.** Color must never be the only way to express match quality, error severity, parsing status, selected state, or required action. Pair color with text, labels, icons, or structure.

**The No Job-Board Blue Rule.** Do not introduce generic job-board blue as the default brand move. If a future accent is added, it must be selected for JobVault's calm product tone, not for category recognition.

**The Transitional White Rule.** The existing canvas is pure reset white because the frontend is stripped back. Future designed surfaces should move to a subtly tinted neutral rather than expanding pure white into a full visual identity.

## 3. Typography

**Display Font:** Arial, Helvetica, sans-serif
**Body Font:** Arial, Helvetica, sans-serif
**Label/Mono Font:** Browser default monospace for raw `pre` diagnostics until a deliberate data font is introduced.

**Character:** The current typography is native, plain, and utilitarian. That is acceptable for a product scaffold, but future hierarchy must be tuned deliberately so dense hiring data reads as evidence instead of raw output.

### Hierarchy
- **Display** (browser default `h1`, inherited family, browser-defined size and weight): Used for route titles only. Do not use expressive display fonts in app labels, buttons, or data.
- **Headline** (browser default `h2`, inherited family, browser-defined size and weight): Used for workflow sections such as resume upload, ranked matches, session, profile, parsing, and matching.
- **Title** (inherited family, browser-defined weight): Reserved for future component titles when cards or panels are reintroduced.
- **Body** (400, `1rem`, `1.5`): Default paragraph, form, status, and navigation text.
- **Label** (inherited family, browser-defined weight): Native labels stay adjacent to controls. Labels must remain explicit and visible.

### Named Rules

**The Plain Language Rule.** Interface copy must explain the next action or system state directly. Avoid vague AI-style explanations for match scores, missing skills, parsing failures, or auth recovery.

**The Data Is Text Rule.** IDs, totals, timestamps, scores, and failure codes must be readable text, not decorative badge noise. If a value matters for trust or support, keep it selectable and legible.

## 4. Elevation

The current system has no elevation vocabulary. There are no shadows, no layered cards, no glass surfaces, and no tonal panels in the active CSS. Depth is conveyed only by document structure: headings, sections, lists, form grouping, and source order.

### Named Rules

**The Flat Until Useful Rule.** Surfaces are flat by default. Add borders, tonal panels, or shadows only when they clarify grouping, state, or focus.

**The No Dashboard Decoration Rule.** Admin metrics may use panels and tables when needed, but never decorative dashboards, gratuitous animation, or ornamental chart framing.

## 5. Components

The active frontend has no custom component library. Components are semantic HTML primitives styled by browser defaults and the global reset.

### Buttons
- **Shape:** Browser-native shape; no project radius token exists.
- **Primary:** Native `<button>` with inherited font and browser-defined background, border, and padding.
- **Hover / Focus:** Browser-native behavior only. Future custom buttons must include visible `:focus-visible`, disabled, loading, and active states.
- **Secondary / Ghost / Tertiary:** Not implemented. Do not invent visual variants until the product has a clear action hierarchy.

### Cards / Containers
- **Corner Style:** Not implemented.
- **Background:** The page uses the reset canvas.
- **Shadow Strategy:** No shadows.
- **Border:** Not implemented.
- **Internal Padding:** Browser and document-flow defaults only.

### Inputs / Fields
- **Style:** Browser-native inputs and selects with inherited font.
- **Focus:** Browser-native focus behavior only. Future styling must preserve visible keyboard focus.
- **Error / Disabled:** Error messages are rendered as text near the relevant workflow. Disabled controls use native disabled behavior.

### Navigation
- **Style, typography, default/hover/active states, mobile treatment.** Navigation is a plain text row of `NavLink` items separated by pipes. Active links receive the `active` class, but no CSS currently styles that class.

### Diagnostic Output

Raw `pre` blocks are used for access tokens, expiry values, resume IDs, pagination metadata, parse metrics, match metrics, and failure-code maps. Treat this as a temporary diagnostic pattern. When the UI is rebuilt, replace raw dumps with labeled rows, compact tables, or structured definition lists without hiding support-relevant values.

## 6. Do's and Don'ts

### Do:
- **Do** make matching explainable through visible scores, factors, skill gaps, and recovery messages.
- **Do** preserve semantic HTML and explicit labels before adding custom visual treatments.
- **Do** design loading, empty, error, disabled, and unauthorized states as first-class surfaces.
- **Do** use restrained styling when rebuilding: tinted neutrals, clear borders, visible focus, and compact evidence-first layouts.

### Don't:
- **Don't** use generic job-board blue.
- **Don't** use startup-gradient SaaS visuals.
- **Don't** use playful gamified career-app patterns.
- **Don't** use corporate HR portal heaviness.
- **Don't** make the product feel noisy, celebratory, sales-led, or bureaucratic.
- **Don't** add decorative dashboards, gratuitous animation, over-branded empty states, or vague AI-style match explanations that reduce trust.
- **Don't** use side-stripe borders, gradient text, glassmorphism, hero-metric templates, nested cards, or identical icon-card grids.
- **Don't** rely on color alone for status, match quality, error severity, or required action.
