# Changelog guide

How agents write entries in `Changelog`. Read it for every implementation task. Version numbers and publishing are in [RELEASE.md](RELEASE.md). Entry wording follows the house-style skill's "Changelog entries" section (the user's older IE voice); where this guide differs, the skill wins.

## When to write an entry

- Every implementation task adds or amends its entries in the current unreleased section before merge, in the same commit as the change. Agents write them; never leave them for the user.
- Read-only investigations and documentation-only changes add nothing.
- Record only completed changes from the actual diff. Never list plans or unfinished work as shipped.
- When a change needs no entry, say why in the PR.

## File layout

`Changelog` at the repository root, plain text, no extension:

```text
--------------------------- Changelog -----------------------------
4.2.a - UNRELEASED
General
Adjusted - Brought all mentions of solar units in line with vanilla

Museum
Fixed - Parade fleet stability bonus no longer stacks

4.1.b - CURRENT PUBLIC VERSION
SAVE COMPATIBLE WITH 4.1 - NOT SAVE COMPATIBLE WITH 4.0
...
```

- Versions newest first. The newest heading is `<version> - UNRELEASED` while work collects for the next release; the released one is `<version> - CURRENT PUBLIC VERSION`; older ones carry the version alone.
- The released version carries a save compatibility line below its heading (`SAVE COMPATIBLE WITH 4.1 - NOT SAVE COMPATIBLE WITH 4.0`). The user sets it at release; never write or change it unasked.
- Open tasks and known issues live in `Changelog_todo`, not in `Changelog`.

## Section format

- Category headings are feature names as the player knows them, on their own line (`General` first, then `Museum`, `Relays`, `Minefields`, `Meteor Showers`, `Pets`, `Research Projects`). Reuse an existing category in the unreleased section before adding one.
- Entries: one change per line, starting `Added - `, `Adjusted - `, `Fixed - ` or `Removed - `, in that order inside a category.
- `Note - ` directly below an entry gives a limit or something the player must do (for example a new save).
- Blank line between categories and between versions. No bullets, no Markdown.

## Wording

The skill section has the forms and examples. The short version:

- Under 80 characters, capital after the dash, no full stop on a one-clause line. No semicolons, no curly quotes, no hyphen as a dash.
- In-game names (`Edict: Mandated Overtime`, `Courier Port`), never class names or the call chain. Cross-mod bugs name the mod (`(Nexerelin)`).
- Added: name, colon, what it does. Adjusted: numbers as old `->` new with the unit, or a direction word. Removed: the thing, then the reason in parentheses.
- Fixed, most used first: `X no longer Y`, `X now properly Y`, `Crash when ...`, or a short noun phrase.
- Credits for real people only, in the user's forms (`(thanks, J2Greene)`, `(PR'd by abel1502, thank you!)`). A forum link to the bug report is fine.
- The user's own lines stay verbatim, jokes and asides included. Agent lines carry no first person, jokes, caps or hedges.
- No agent or model names, links to sessions, commit hashes or validation logs.

## Amending versus adding

- Combine entries when several edits fix the same player-visible problem.
- Amend an existing unreleased entry when the task changes the same feature again.
- A feature new in the unreleased version gets one `Added -` entry describing it as it now stands. Later work on it in the same version edits that entry.
- Something added and removed again within the unreleased version (a feature, a save fix for the last release) loses its entry and gets no `Removed -` line.
- Changes to features that shipped in an earlier release get their own entries.

## Maintenance

Update this guide when the changelog layout or entry rules change. Version and release rules belong in [RELEASE.md](RELEASE.md). Follow [CLAUDE.md](../CLAUDE.md#documentation-upkeep).
