---
name: URL Shortener
description: A calm contemporary library workspace for creating short URLs.
colors:
  plum: "#4b365f"
  plum-hover: "#382547"
  ground: "#f0ebf5"
  surface: "#fff"
  ink: "#2e2536"
  muted: "#66576f"
  line: "#d1c6db"
  warning-bg: "#fff0cc"
  warning-ink: "#855200"
  error: "#a12636"
  success: "#315d49"
  input-border: "#b6a6c5"
  button-disabled: "#766583"
  input-disabled: "#e8e0ee"
typography:
  display:
    fontFamily: "Source Sans 3, Segoe UI, system-ui, sans-serif"
    fontSize: "clamp(2.25rem, 4vw, 3rem)"
    fontWeight: 700
    lineHeight: 1.12
    letterSpacing: "-0.025em"
  title:
    fontFamily: "Source Sans 3, Segoe UI, system-ui, sans-serif"
    fontSize: "1.5rem"
    fontWeight: 700
    lineHeight: 1.25
    letterSpacing: "-0.015em"
  body:
    fontFamily: "Source Sans 3, Segoe UI, system-ui, sans-serif"
    fontSize: "1rem"
    fontWeight: 400
    lineHeight: 1.5
  intro:
    fontFamily: "Source Sans 3, Segoe UI, system-ui, sans-serif"
    fontSize: "1.25rem"
    fontWeight: 400
    lineHeight: 1.5
  label:
    fontFamily: "Source Sans 3, Segoe UI, system-ui, sans-serif"
    fontSize: "1rem"
    fontWeight: 650
  brand:
    fontFamily: "Source Sans 3, Segoe UI, system-ui, sans-serif"
    fontSize: "1.65rem"
    fontWeight: 700
    letterSpacing: "-0.02em"
rounded:
  control: "0.5rem"
spacing:
  small: "0.5rem"
  medium: "1rem"
  control-gap: "1.5rem"
  section: "1.75rem"
  destination: "2rem"
  workspace: "2.25rem"
  workspace-top: "3rem"
  page-bottom: "4rem"
components:
  button-primary:
    backgroundColor: "{colors.plum}"
    textColor: "{colors.surface}"
    typography: "{typography.label}"
    rounded: "{rounded.control}"
    padding: "0.8rem 1.25rem"
  button-primary-hover:
    backgroundColor: "{colors.plum-hover}"
  button-primary-disabled:
    backgroundColor: "{colors.button-disabled}"
  input:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.control}"
    padding: "0.8rem 1rem"
    width: "100%"
  input-disabled:
    backgroundColor: "{colors.input-disabled}"
  availability-notice:
    backgroundColor: "{colors.warning-bg}"
    textColor: "{colors.warning-ink}"
    rounded: "{rounded.control}"
    padding: "1.1rem 1.35rem"
  brand-header:
    backgroundColor: "{colors.plum}"
    textColor: "{colors.surface}"
    typography: "{typography.brand}"
    padding: "1.3rem 0"
---

# Design System: URL Shortener

## Overview

**Creative North Star: "Biblioteca contemporanea"**

A quiet, practical workspace uses humanist type, a plum identity, and a pale lavender ground. The visual character comes from typography and the relationship between entry and result, with little ornament and no raised card layer.

The current surface is an English anonymous URL creation utility. Its amber notice communicates the actual availability limitation before entry; the result area represents saved data only. Future surfaces should preserve this clarity without treating this page's particular composition as a universal template.

**Key Characteristics:**

- Plum brand and action on a lavender ground.
- Humanist type with clear, weighted labels.
- Flat regions separated by thin lines.
- Explicit empty, loading, error, and saved states.

This document records the current source implementation; the approved raster supplies the chosen direction rather than a claim of pixel fidelity.

## Colors

Plum supplies the identity; warm amber and restrained error/success colors communicate states.

### Primary

- **Library Plum:** brand header, primary action, caret, selection, and focus outline.
- **Deep Plum:** enabled action hover.

### Secondary

- **Warm Amber Paper / Amber Ink:** availability notice background and text.
- **Error Red / Saved Green:** error messages and successful persistence feedback.

### Neutral

- **Lavender Ground:** page background.
- **White Surface:** editable and read-only fields, with white text on plum actions.
- **Plum Ink / Muted Plum:** headings and primary text / supporting copy and placeholders.
- **Lavender Line / Input Border:** workspace divider / field stroke.
- **Disabled Plum / Disabled Lavender:** pending action / pending input.

**The Meaningful Color Rule.** Reserve amber, red, and green for availability, errors, and successful saving respectively.

## Typography

**Display Font:** Source Sans 3, with Segoe UI, system-ui, and sans-serif fallbacks.

**Body Font:** The same family. The upright variable font is hosted locally at `frontend/src/assets/fonts/SourceSans3VF-Upright.ttf`, with its Adobe OFL license alongside it. It supports weights 200–900, uses `font-display: swap`, and disables synthetic font styles.

**Character:** Humanist forms keep the utility approachable; weight and size establish hierarchy without a second display face.

### Hierarchy

- **Display:** page heading uses the display token.
- **Title:** result heading uses the title token.
- **Body:** explanatory copy uses the body token; long destinations wrap anywhere.
- **Intro:** introductory sentence uses the intro token; mobile reduces it to (1.1rem).
- **Label:** field labels, destination title, and buttons use weight (650).
- **Brand:** header uses the brand token; mobile reduces its size to (1.35rem).
- **Short URL:** read-only output uses (clamp(1.125rem, 1.5vw, 1.3rem)) and weight (650).

**The One Family Rule.** Use the locally hosted Source Sans 3 family across headings, text, and controls, with explicit heading and brand weight (700).

## Layout

The page and header content share a maximum width (70rem). Desktop gutters total (4rem); mobile gutters total (2.5rem). The body has a minimum width (20rem). Main content starts after (clamp(2.5rem, 5vw, 4.5rem)) of top padding and ends with the page-bottom spacing token.

The current workspace pairs a form and result in a grid (minmax(0, 1.1fr) minmax(0, 1fr)), with workspace gap and top-spacing tokens. The implemented ratio is approximately 52/48, rather than the contract's proposed 55/45. Controls remain vertically arranged with the control-gap token. The result has a left divider, workspace-sized inset, and minimum height (19rem).

At viewport width (48rem) and below, the workspace stacks, uses a control-gap-sized gap, and starts after destination-sized spacing. The result divider moves to the top, its padding becomes (1.75rem 0 0), and its minimum height becomes automatic. Result-heading bottom margin reduces to (1.25rem). All grid children permit shrinking; destination text wraps without forcing page overflow.

## Elevation & Depth

There are no shadows. Tonal contrast and single-pixel divider/input strokes establish regions; the form and result remain on the shared page ground.

**The Shared Ground Rule.** Keep the workspace flat, using the existing line and field surfaces to express structure.

## Shapes

Controls and the availability notice share gently rounded corners through the control radius token. Inputs have a single-pixel border. The workspace divider is a straight single-pixel line. The notice uses an inline outlined SVG, not an icon font or text glyph.

## Components

### Buttons

The primary action is full-width within its form row, with the primary token's padding and radius and minimum height (3.5rem). Hover uses deep plum. Pressing moves it down (1px); reduced-motion preferences remove that movement. Keyboard focus uses a plum outline (3px), offset (4px). Pending disables the action, changes its fill, uses a wait cursor, and changes its text to “Creating your link…”. No hover transition or loading spinner is implemented.

### Inputs / Fields

White fields use the input token and minimum height (3.5rem). Placeholder text is muted plum, the caret is plum, and invalid fields change their border to error red. Pending disables the editable input and changes its background. Keyboard focus uses the shared visible outline. The input retains submitted text on failure; field validation errors restore focus to it.

Labels are explicitly associated with controls. The URL input provides URL keyboard/autocomplete hints and supporting instructions. Invalid field messages join the input's accessible description; errors use `role="alert"`. The form announces busy state and has a status region for saving feedback.

### Brand Header

The header is a full-width plum band with content aligned to the main container. It contains the working product name; it is not navigation and has no interactive states.

### Availability Notice

A rounded amber region combines an outlined informational SVG and text with gap (1rem). The SVG is hidden from assistive technology. The named aside is associated with both the form and result so the redirect limitation remains contextual.

### Result Region

An empty instruction becomes progress copy while saving, then genuine returned data after success. The full short URL is a labeled read-only input that selects its value on focus for manual copying. A destination paragraph and green persistence confirmation follow it. The result is announced through a polite, atomic live region.

New result content moves from (6px) below to its resting position over (260ms), using `cubic-bezier(0.16, 1, 0.3, 1)`. Reduced-motion preferences remove this animation. There is no automatic clipboard action or navigable generated URL while redirect support is unavailable.

## Do's and Don'ts

### Do:

- **Do** use Library Plum for identity, primary actions, and visible focus.
- **Do** use the locally hosted variable font with explicit heading and label weights.
- **Do** preserve associated labels, readable feedback, and reduced-motion behavior.
- **Do** show genuine returned results and clear availability information.

### Don't:

- **Don't** present planned redirects or one-click copying as implemented features.
- **Don't** replace the shared flat workspace with raised cards without a deliberate system change.
- **Don't** use status colors as decorative accents.

No craft defects are canonized as reusable rules. The documented grid ratio reflects the implemented layout, rather than the proposed ratio in the surface contract.
