# Changelog guide

How agents write entries in `Changelog`. Read it for every implementation task. Version numbers and publishing are in [RELEASE.md](RELEASE.md); player-facing wording rules are in the house-style `player-text.md`.

## When to write an entry

- Every implementation task adds or amends its entries in the current unreleased section before merge, in the same commit as the change. Agents write them; never leave them for the user.
- Read-only investigations and documentation-only changes add nothing.
- Record only completed changes from the actual diff. Never list plans or unfinished work as shipped.
- When a change needs no entry, say why in the PR.

## File layout

`Changelog` at the repository root, plain text, no extension:

```text
--------------------------- Changelog -----------------------------
4.1.c - UNRELEASED
General
Adjusted - Brought all mentions of solar units in line with vanilla

Museum
Fixed - Parade fleet stability bonus no longer stacks

4.1.b - CURRENT PUBLIC VERSION
SAVE COMPATIBLE WITH 4.1 - NOT SAVE COMPATIBLE WITH 4.0
...
```

- Versions newest first. The newest heading is `<version> - UNRELEASED` while work collects for the next release; the released one is `<version> - CURRENT PUBLIC VERSION`; older ones carry the version alone.
- The current heading reads `4.1.c- UNRELEASED` (missing space). Fix it to `4.1.c - UNRELEASED` the next time an entry is added.
- The released version carries a save compatibility line below its heading (`SAVE COMPATIBLE WITH 4.1 - NOT SAVE COMPATIBLE WITH 4.0`). The user sets it at release.
- Open tasks and known issues live in `Changelog_todo`, not in `Changelog`.

## Section format

- Category headings are feature names as the player knows them, on their own line (`General`, `Museum`, `Relays`, `Minefields`, `Meteor Showers`, `Pets`, `Research Projects`). Reuse an existing category in the unreleased section before adding one.
- Entries: one change per line, starting `Added - `, `Adjusted - `, `Fixed - ` or `Removed - `.
- `Note - ` directly below an entry explains a limit or something the player must do (for example a new save).
- Blank line between categories and between versions. No bullets, no Markdown.
- A credit to an outside contributor goes in the entry, as the user does (`(PR'd by abel1502, thank you!)`).

## Wording

Entries are player-facing: plain sentences in sentence case, not programmer speak.

- Say what changed, naming the feature so a player finds it.
- Give old and new values for balance changes.
- For a fix, describe the broken behavior that is now fixed, not the call chain.
- No agent or model names, links to sessions, commit hashes or validation logs. A forum link to the bug report is fine, as the user does.

## Amending versus adding

- Combine entries when several edits fix the same player-visible problem.
- Amend an existing unreleased entry when the task changes the same feature again.
- A feature new in the unreleased version gets one `Added -` entry describing it as it now stands. Later work on it in the same version edits that entry.
- Changes to features that shipped in an earlier release get their own entries.

## Maintenance

Update this guide when the changelog layout or entry rules change. Version and release rules belong in [RELEASE.md](RELEASE.md). Follow [CLAUDE.md](../CLAUDE.md#documentation-upkeep).
