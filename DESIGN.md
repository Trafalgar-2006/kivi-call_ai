---
name: Kivi
description: The back of a painted Indian truck tells every caller how to get through; everything around it is quiet Material 3.
colors:
  night-enamel: "#0B1C26"
  marigold: "#F2B21B"
  vermilion: "#C7372B"
  leaf: "#23774A"
  cream: "#F4ECDC"
  resting-slate: "#33434B"
  mode-sleep: "#4B3CA7"
  mode-busy: "#9A6400"
  mode-away: "#B0352B"
  mode-strangers: "#14627A"
  on-paint: "#FFFFFF"
  kivi-skin: "#B3C7D3"
  kivi-ear: "#9FB6C4"
  kivi-blush: "#F2A29A"
  enamel-tonal: "#3B300E"
  coral-dark: "#FF8A7A"
  peacock-mist: "#E3F5FA"
  container-high-dark: "#183442"
  container-highest-dark: "#1E3D4C"
  on-surface-variant-dark: "#A9C1CC"
  outline-dark: "#55707D"
  error-container-dark: "#5C1A14"
  on-error-container-dark: "#FFDAD4"
  ground-light: "#EEF3F5"
  primary-light: "#7A5200"
  primary-container-light: "#FFE3A3"
  container-high-light: "#E1EAEE"
  container-highest-light: "#D9E4E9"
  on-surface-variant-light: "#3F5560"
  outline-light: "#6F8791"
  tertiary-container-light: "#CDEBF3"
  error-light: "#B3261E"
  error-container-light: "#F9DEDC"
typography:
  sign-large: { fontFamily: "Yatra One", fontSize: "42px", fontWeight: 400, lineHeight: "44px" }
  sign-small: { fontFamily: "Yatra One", fontSize: "19px", fontWeight: 400, lineHeight: "24px", letterSpacing: "0.6px" }
  headline-medium: { fontFamily: "Roboto, sans-serif", fontSize: "28px", fontWeight: 400, lineHeight: "36px" }
  headline-small: { fontFamily: "Roboto, sans-serif", fontSize: "24px", fontWeight: 400, lineHeight: "32px" }
  title-large: { fontFamily: "Roboto, sans-serif", fontSize: "22px", fontWeight: 400, lineHeight: "28px" }
  title-medium: { fontFamily: "Roboto, sans-serif", fontSize: "16px", fontWeight: 500, lineHeight: "24px", letterSpacing: "0.15px" }
  title-small: { fontFamily: "Roboto, sans-serif", fontSize: "14px", fontWeight: 500, lineHeight: "20px", letterSpacing: "0.1px" }
  body-large: { fontFamily: "Roboto, sans-serif", fontSize: "16px", fontWeight: 400, lineHeight: "24px", letterSpacing: "0.5px" }
  body-medium: { fontFamily: "Roboto, sans-serif", fontSize: "14px", fontWeight: 400, lineHeight: "20px", letterSpacing: "0.25px" }
  body-small: { fontFamily: "Roboto, sans-serif", fontSize: "12px", fontWeight: 400, lineHeight: "16px", letterSpacing: "0.4px" }
  label-large: { fontFamily: "Roboto, sans-serif", fontSize: "14px", fontWeight: 500, lineHeight: "20px", letterSpacing: "0.1px" }
  label-medium: { fontFamily: "Roboto, sans-serif", fontSize: "12px", fontWeight: 500, lineHeight: "16px", letterSpacing: "0.5px" }
  label-small: { fontFamily: "Roboto, sans-serif", fontSize: "11px", fontWeight: 500, lineHeight: "16px", letterSpacing: "0.5px" }
rounded:
  strip: "4px"
  tag: "6px"
  chip: "8px"
  signboard: "12px"
  card: "16px"
  bubble: "18px"
  pinstripe: "19px"
  tile: "20px"
  tailgate: "26px"
  pill: "999px"
spacing:
  gap: "8px"
  inset: "12px"
  tile: "14px"
  gutter: "16px"
  text-gutter: "20px"
  sheet-gutter: "24px"
  section: "28px"
  end: "32px"
components:
  tailgate: { backgroundColor: "{colors.resting-slate}", textColor: "{colors.cream}", typography: "{typography.sign-large}", rounded: "{rounded.tailgate}", padding: "20px 22px" }
  tailgate-signboard: { backgroundColor: "{colors.night-enamel}", textColor: "{colors.marigold}", typography: "{typography.sign-small}", rounded: "{rounded.signboard}", padding: "8px 0" }
  mode-tile: { backgroundColor: "{colors.container-high-dark}", textColor: "{colors.cream}", typography: "{typography.title-small}", rounded: "{rounded.tile}", padding: "14px", height: "92px" }
  mode-tile-on: { backgroundColor: "{colors.mode-sleep}", textColor: "{colors.on-paint}", typography: "{typography.title-small}", rounded: "{rounded.tile}", padding: "14px", height: "92px" }
  mode-option: { backgroundColor: "{colors.container-high-dark}", textColor: "{colors.cream}", typography: "{typography.title-small}", rounded: "{rounded.card}", padding: "0 16px", height: "52px" }
  filter-chip: { textColor: "{colors.on-surface-variant-dark}", typography: "{typography.label-large}", rounded: "{rounded.chip}", height: "32px" }
  filter-chip-selected: { backgroundColor: "{colors.enamel-tonal}", textColor: "{colors.marigold}", typography: "{typography.label-large}", rounded: "{rounded.chip}", height: "32px" }
  category-tag-needs-you: { backgroundColor: "{colors.vermilion}", textColor: "{colors.on-paint}", typography: "{typography.label-medium}", rounded: "{rounded.tag}", padding: "3px 8px" }
  category-tag-handled: { backgroundColor: "{colors.leaf}", textColor: "{colors.on-paint}", typography: "{typography.label-medium}", rounded: "{rounded.tag}", padding: "3px 8px" }
  category-tag-blocked: { backgroundColor: "{colors.container-highest-dark}", textColor: "{colors.on-surface-variant-dark}", typography: "{typography.label-medium}", rounded: "{rounded.tag}", padding: "3px 8px" }
  call-row: { textColor: "{colors.cream}", typography: "{typography.title-medium}", padding: "14px 20px" }
  button-primary: { backgroundColor: "{colors.marigold}", textColor: "{colors.night-enamel}", typography: "{typography.label-large}", rounded: "{rounded.pill}", height: "48px" }
  button-tonal: { backgroundColor: "{colors.enamel-tonal}", textColor: "{colors.marigold}", typography: "{typography.label-large}", rounded: "{rounded.pill}", height: "48px" }
  button-end-call: { backgroundColor: "{colors.vermilion}", textColor: "{colors.on-paint}", typography: "{typography.label-large}", rounded: "{rounded.pill}", height: "56px" }
  transcript-caller: { backgroundColor: "{colors.container-high-dark}", textColor: "{colors.cream}", typography: "{typography.body-large}", rounded: "{rounded.bubble}", padding: "10px 14px", width: "300px" }
  transcript-kivi: { backgroundColor: "{colors.mode-strangers}", textColor: "{colors.peacock-mist}", typography: "{typography.body-large}", rounded: "{rounded.bubble}", padding: "10px 14px", width: "300px" }
  problem-banner: { backgroundColor: "{colors.error-container-dark}", textColor: "{colors.on-error-container-dark}", typography: "{typography.body-medium}", rounded: "{rounded.bubble}", padding: "8px 8px 8px 16px" }
  kivi-badge: { backgroundColor: "{colors.mode-strangers}", size: "112px" }
---

# Design System: Kivi

## Overview

**Creative North Star: "The Painted Tailgate"**

Home is the back of a painted Indian truck. Where a tailgate says HORN OK PLEASE, Kivi's says KIVI ANSWERS EVERYONE · CALL 3 TIMES IF URGENT. One hand-painted panel carries the whole personality: the paint of the active mode, a marigold band, a vermilion pinstripe, shaded sign lettering and Kivi the elephant. Everything around it is standard Material 3 in Roboto on night enamel, so the panel stays the loudest thing on screen and the rest reads in short glances.

The system is source-of-truth in `src/main/java/dev/callai/ui/Theme.kt` (`Paint`, `modeColor()`, `Sign`, `signLarge()`, `SignSmall`, `MOTION_MS`, `KiviEase`). Dark is the canonical world; light is its daylight print. Frontmatter dimensions are Android dp (type in sp), written as px for DESIGN.md tooling. Phone only; no tablet layout.

**Key Characteristics:**
- One signature surface (the tailgate); quiet M3 chrome everywhere else.
- Every mode owns one truck paint, used wherever that mode appears.
- Sign lettering lives only on the tailgate, in two roles.
- One motion grammar: 350ms with a small overshoot.
- Flat: depth comes from paint and tonal containers, never shadows.
- All vector. No rasters ship.

### Motion grammar
- **Kivi motion** (`kiviMotion()`): `tween(350ms, KiviEase = cubic-bezier(0.34, 1.3, 0.64, 1))`. Used for the tailgate wipe, the state word's slide-in (from 1/3 height), mode-tile colour changes and setup step slides (from 1/4 width).
- **Fades** use the same 350ms (`tween(MOTION_MS)`): Kivi's mood crossfade, the old word fading out.
- **Kivi idles** on infinite loops: ear flap 1300ms (2600ms asleep), breathing 1800ms (2600ms asleep), a blink every 4.2s, trunk talk 170ms, sleep "z" drift 2400ms linear.
- **Reduced motion:** when the system's animator duration scale is 0, Kivi holds still and the z's stay solid.

## Colors

Truck paint on night enamel: saturated, matte, warm-on-cool. Paint never changes with the theme; only the chrome does.

### Primary
- **Marigold** (marigold): the band around the tailgate, the dashed rule, the painted rules, dark-theme primary (buttons, focus, switches) and Kivi's busy spectacles and mic.
- **Deep Ochre** (primary-light): light-theme primary; Marigold is too pale to carry text on the light ground.

### Secondary
- **Vermilion** (vermilion): the tailgate pinstripe, the sign drop shade, the *Needs you* tag, the end-call button and Kivi's earcups. Light-theme secondary.
- **Coral** (coral-dark): dark-theme secondary and error (error icons, destructive text).

### Tertiary
- **Peacock Teal** (mode-strangers): Strangers' paint, the KiviBadge, the launcher background and Kivi's transcript bubbles in dark theme (Peacock Wash, tertiary-container-light, in light).

### Mode paints
Each mode has its own paint (`modeColor()`), with white text (on-paint) at 5:1 or better: **Sleep Indigo** (mode-sleep), **Busy Ochre** (mode-busy), **Drive** in **Leaf** (leaf, also the *Handled* tag and success ticks), **Away Red** (mode-away), **Strangers** in Peacock Teal. With no mode on, the tailgate rests in **Resting Slate** (resting-slate).

### Neutral
- **Night Enamel** (night-enamel): the dark ground and surface, the signboard behind the painted rules, Kivi's ink outline, and the text colour in light theme.
- **Sign Cream** (cream): dark-theme text, and the state word on every paint.
- **Enamel Brass** (enamel-tonal): the dark secondary container, used for selected chips and tonal buttons with Marigold text.
- **Enamel containers** (container-high-dark, container-highest-dark), **Fog** (on-surface-variant-dark) and **Weathered Outline** (outline-dark) make up the dark chrome. Their light counterparts are **Daylight Ground** (ground-light), container-high-light, container-highest-light, on-surface-variant-light and outline-light.
- **Mascot hide** (kivi-skin, kivi-ear, kivi-blush): Kivi only, shared with the launcher icon.

**The Paint Not Chrome Rule.** The tailgate, mode paints and KiviBadge use the same hex in both themes. Only the ground, containers and text roles follow the system theme.

**The Signboard Rule.** Marigold lettering never sits directly on a mode paint. The painted rules sit on a night-enamel signboard (9.2:1) so they hold AA on all six paints *(approved adaptation)*.

**The Ochre Busy Rule.** Busy is painted ochre, not marigold, so white text on an active Busy tile passes AA (5.0:1) *(approved adaptation)*.

## Typography

**Sign Font:** Yatra One (`res/font/yatra_one.ttf`). Google Fonts, SIL Open Font License, downloaded from github.com/google/fonts (`ofl/yatraone`).
**UI Font:** Roboto, the system default, on the stock Material 3 type scale (Theme.kt overrides no typography).

**Character:** shaded, brush-cut sign-painter lettering for what the truck says aloud, and plain Roboto for everything the owner reads.

### Hierarchy
- **Sign large** (42/44sp, cream with a hard 2.5dp vermilion drop shade at 0 blur): the state word only ("Sleeping.", "On a call."). It is always one line, stepping down to 26sp at the smallest rather than wrapping.
- **Sign small** (19/24sp, +0.6sp tracking, marigold, uppercase copy, centred): the painted rules on the signboard.
- **Headline medium / small** (28sp / 24sp): the caller's name on call detail / sheet titles, setup headings and the welcome line.
- **Title large** (22sp): the app bar title, and the call reason on detail.
- **Title medium** (16sp, 500): section titles in on-surface-variant, caller names in call rows.
- **Title small** (14sp, 500): mode tile names and sheet option labels.
- **Body large / medium / small** (16 / 14 / 12sp): transcripts and setup copy / reasons, subtitles and banners / helper text.
- **Label large / medium / small** (14 / 12 / 11sp, 500): chips and durations / tags (bold) and timestamps / tile captions.

**The Two Sizes Rule.** Yatra One appears at exactly two roles, sign-large and sign-small, and only on the tailgate. The sleep "z"s drawn inside Kivi's artwork are illustration, not type.

**The Tabular Rule.** Numbers that change (durations, chip counts, clock times in logs) merge `Tabular` (`tnum`) so they don't jitter.

## Layout

Home is a single `LazyColumn`, edge to edge: app bar, tailgate, problem banner (only when Kivi is blocked and a mode wants it), **Modes**, then **Calls** (chips, then rows). Panels and grids sit on a 16dp gutter. Text rows and detail screens use 20dp, and sheets and setup use 24dp. Section titles get 28dp above and 10dp below, and lists end with 32dp of breathing room. The mode grid is three equal columns with 8dp gaps; a short last row is padded with empty cells, never stretched. Filter chips scroll horizontally in one row. In call rows the duration is right-aligned tight to the margin, opposite the name.

## Elevation & Depth

Flat by construction. No surface casts a shadow. Depth comes from paint laid over enamel and from M3 tonal container steps; sheets and dialogs keep M3's tonal defaults. The single shadow in the system is the sign painter's hard vermilion drop shade under sign-large lettering.

**The Paint-Is-Depth Rule.** To lift something, give it a container step or a paint, never an elevation shadow.

## Shapes

A rounded-rectangle family that nests concentrically. The tailgate is 26dp, with its pinstripe at 19dp inset 9dp. Tiles are 20dp. Bubbles and banners are 18dp, with a 4dp tail corner on the speaker's side. Cards and sheet options are 16dp, the signboard 12dp, chips 8dp, tags 6dp and the sheet's paint strip 4dp. Buttons are M3 pills. A KiviBadge's radius is 28% of its size. The tailgate has three painted lines: a 5dp marigold band, a 1.5dp vermilion pinstripe, and a 3dp dashed marigold rule (10dp dash, 7dp gap).

## Components

### Tailgate (signature)
Kivi (112dp, left) beside the state word and a one-line plain subtitle (body-medium, cream at 90%). Below them sit the dashed rule and the signboard with two painted rules. The paint is `modeColor(lead)`. **Repaint:** on a mode change the new paint wipes left to right over the old one (`kiviMotion()`) while Kivi crossfades to the new pose and the word slides up. *Build note:* the direction contract named peacock teal as the panel paint. As built, the panel wears the lead mode's paint and rests in slate; teal belongs to Strangers.

### Kivi and KiviBadge
Original vector artwork authored for this project: SVG paths on a 200×200 grid in `Kivi.kt` (preview at `.impeccable/mocks/kivi.html`). It ships no rasters, and the launcher and notification icons are vector drawables built from the same paths. There are six moods (off, duty, sleep, drive, busy, talking), each a pose plus an accessory (headset, shades, spectacles, z's). Off the tailgate, Kivi always sits on a **KiviBadge**: a teal square with a 28% radius and 8% inset, so his night-ink outline never melts into the night ground *(approved adaptation)*. Badges appear at 112dp (empty inbox), 160 to 200dp (setup) and 180dp (test call).

### Mode tiles and mode sheet
Tiles are 92dp minimum, padded 14dp, showing an icon, the title (title-small) and a caption (label-small, two lines at most). When off, a tile is container-high with a muted caption. When on, it fills with its mode paint and all text turns white, animated with `kiviMotion()`. Tapping a tile opens a bottom sheet led by an 8dp strip of the mode's paint. Its options are 52dp container-high rows with a 10dp paint dot. Strangers toggles in place and confirms with a snackbar.

### Filter chips, call rows, category tags
M3 FilterChips: *Needs you*, *Handled*, *Blocked* and **All** (the last is an approved adaptation, because it is the only time-ordered view of every call and the owner asked to see "who all called"). Each label carries its count in tabular figures, and a selected chip is Enamel Brass with Marigold text. A call row stacks the name and duration, then a two-line reason, then a tag and relative time. Tags are 6dp blocks with bold label-medium: Vermilion for *Needs you*, Leaf for *Handled*, and a neutral container for *Blocked*.

### Transcript
Mirrored columns: the caller on the left in a container-high bubble, Kivi on the right in a teal tertiary-container bubble. Bubbles are 300dp maximum, padded 14×10dp, in body-large. A speaker label appears only when the speaker changes.

### Buttons and banners
These are M3 buttons at 48dp minimum: filled (primary) for the main action (Call back, Next) and tonal for secondary actions (Send reply, test call). End-call is a 56dp Vermilion button with white text. The problem banner is an 18dp error-container strip with a warning icon, the sentence "Calls will ring normally: …", and a **Fix** text button.

## Do's and Don'ts

### Do:
- **Do** take every mode colour from `modeColor()` and use it everywhere that mode appears: tailgate, active tile, sheet strip, option dots.
- **Do** put white (on-paint) text on mode paints. Busy Ochre is the tightest at 5.0:1; check any new paint against it.
- **Do** run every UI state change through `kiviMotion()` or `tween(MOTION_MS)`.
- **Do** keep touch targets at 48dp or more (sheet options are 52dp, tiles 92dp, end-call 56dp).
- **Do** keep paint-coloured text only on surfaces where it holds 4.5:1. Leaf text on night enamel is 3.15:1 and fails.
- **Do** honour the system's "Remove animations" setting in any new looping art.

### Don't:
- **Don't** use Yatra One off the tailgate, at a third size, or wrapped onto two lines.
- **Don't** put marigold lettering straight onto a mode paint; it belongs on the night-enamel signboard.
- **Don't** place Kivi bare on night enamel; use a KiviBadge.
- **Don't** add elevation shadows or raster art.
- **Don't** introduce a second duration or easing for UI state; only Kivi's idle loops run on their own clocks.
- **Don't** change tailgate, mode or badge paints between light and dark themes.
