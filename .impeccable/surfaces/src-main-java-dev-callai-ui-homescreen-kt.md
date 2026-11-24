---
version: 1
slug: "src-main-java-dev-callai-ui-homescreen-kt"
primary_target: "src/main/java/dev/callai/ui/HomeScreen.kt"
related_targets: []
---

## Scope

The Kivi Android app, in Operate mode, covering every screen: Home (the hero tailgate, modes, inbox), mode sheets, call detail, test call, setup wizard, settings and diagnostics. Owner-facing only.

## Audience and task

The phone's owner, in short glances: after waking at 6:30 AM in a dim room, after a meeting, after parking. There are three tasks:
1. Know exactly who reaches them right now.
2. Switch a mode in one tap.
3. Act on a note: call back or reply.

## Direction contract

THESIS: The home screen is the back of a painted Indian truck. It tells every caller how to get through ("KIVI ANSWERS EVERYONE · CALL 3 TIMES IF URGENT") the way a tailgate says HORN OK PLEASE. It refuses the category default: a tinted status card with a cute mascot over a grey list.

OWN-WORLD:
- **Ground:** night-enamel blue-black.
- **The tailgate panel:** peacock-teal paint framed by a marigold band and a vermilion inner pinstripe.
- **Sign lettering:** shaded hand-painted lettering (Yatra One) for the state word and the painted rules only, at exactly two sizes, cream with a vermilion drop shade.
- **Mode colours:** each mode carries its own truck-paint colour (sleep indigo, busy marigold, drive leaf green, away vermilion, strangers teal).
- **Everything else:** standard Material 3 components in Roboto.

STORY: In one glance the owner sees Kivi's state, the reason for it, and the rules callers face. Below that they see which mode is on, then the calls that need them. They tap a mode to change it, or a note to act on it.

FIRST VIEWPORT:
- **Top app bar:** the title "Kivi", with settings on the right.
- **Hero:** a full-width tailgate panel. The elephant sits left, about 120dp. The state word is in large sign lettering ("Sleeping."), with one plain subtitle line below it ("Answering everyone until 6:30 AM"). A dashed marigold rule separates the painted rules at the bottom of the panel.
- **Modes:** a 3-column grid of tonal tiles. The active tile is filled with its mode colour and captioned ("until 6:30 AM", "Always on").
- **Calls:** filter chips for Needs you, Handled and Blocked. Each call row shows the name, then the duration right-aligned tight to the margin, then the reason. The primary action is tapping a mode tile.

FORM: Tailgate (hand-painted truck art), assigned candidate 7 of 7; seed: 67e226a0f87de349.

Raises:
- **From J-card:** durations right-aligned tight to the margin.
- **From the Bitmap specimen:** sign lettering at exactly two sizes.
- **From the Title wall:** the transcript and actions appear only on the detail screen.
- **From the Zoo map:** one motion grammar, 350ms with a small overshoot, including the panel repaint on mode change.
- **From Labanotation:** the transcript as mirrored columns, caller left and Kivi right.

Signature move: a mode change repaints the tailgate. The panel's paint wipes across in the new mode's colour while the elephant changes pose.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance.

## Unresolved

- UI language: English only for now.
- Tablet layout: not a target (phone only).
