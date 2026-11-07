# Survey stories

Lore snippets an "archivist" tells when the player surveys a planet with ruins: the story CSV, the picker and the rules.csv loop. Read it before adding stories or changing when they show. It does not cover ruins themselves or tech mining; see [Derelicts](../industries/DERELICTS.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

When the player picks the vanilla survey option on a planet that is not fully surveyed, a high-score rule intercepts it, picks one unread story that matches the planet, and offers "Check in with the archivist". The story plays as intro, up to three parts and outro, then the normal survey continues. A planet offers a story once; a story is marked read only when the outro is reached. Code lives in its own small package with an "oldstory" prefix in keys and rule ids (inferred: ported from a standalone mod).

## Files

| File | Owner / role |
|---|---|
| `indevo/exploration/surveystories/dialogue/rules/SurveyStoryReader.java` | Rule command `SurveyStoryReader <ACTION>`: `PREP_STORY`, `HAS_NEXT`, `READ_INTRO`, `READ_NEXT`, `READ_OUTRO`; owns the planet memory keys |
| `indevo/exploration/surveystories/plugins/Historian.java` | Persistent singleton in sector memory: told-story list, filtering and weighted pick (`getStoryForPlanet`) |
| `indevo/exploration/surveystories/memory/FileLoader.java` | Loads the story CSV with `getMergedSpreadsheetDataForMod("id", path, "IndEvo")` and caches the set in `SessionTransientMemory` |
| `indevo/exploration/surveystories/memory/StoryEntry.java` | One CSV row: id, filters, weight, intro, parts, outro |
| `indevo/exploration/surveystories/OldStoryLogger.java` | Dev-mode-only logger with the mod name prefix |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Story CSV | `data/campaign/stories.csv`: `id, optional_planet_types, optional_required_conditions, optional_ruins_sizes, picker_weight, comms_intro_text, story_part_1..3, comms_outro_text`. 6 stories (`IE-desert-warlords-1`, `IE-desert-madmax`, `IE-broken-domes-1`, `IE-empty-1`, `IE-plague-1`, `IE-genocide-1`) and 12 blank rows | path from `settings.json:65` key `oldstory_csv_path` |
| Draft CSV | `data/campaign/stories_TODO.csv`: older column set without `optional_required_conditions`; not read by code | |
| Rule command package | `indevo.exploration.surveystories.dialogue.rules` | `settings.json` `ruleCommandPackages` (`:71`) |
| Rules | `oldstory_onSurveyIntercept` (trigger `DialogOptionSelected`, `$option == surveyPerform score:1000` + `SurveyStoryReader PREP_STORY`), `oldstory_intro`, `oldstory_readnext`, `oldstory_outro`, `oldstory_addNextOption`, `oldstory_addOutroOption` (trigger `OldStoryReader`) | `data/campaign/rules.csv:841-852` |
| Memory (planet) | `$oldstory_localStory` (story id), `$oldstory_lastChapter` (int), `$oldstory_done` (set on prep, permanent) | `SurveyStoryReader:19-21` |
| Memory (planet) | `$indEvo_fixedStoryId`: forces a specific story; nothing sets it in IE | `Historian:19` |
| Memory (sector) | `$indEvo_Historian` = `Historian` instance | `Historian:18` |
| Transient cache | `"$" + oldstory_csv_path` in `SessionTransientMemory` | `FileLoader:22` ([Utils](../core/UTILS.md)) |

## Registration and lifecycle

No ModPlugin hook. The rule command is found through `ruleCommandPackages`; `Historian` is created lazily in sector memory by `getHistorian()` and saved with it. The CSV is loaded on first use per session.

Order: `surveyPerform option -> oldstory_onSurveyIntercept -> SurveyStoryReader PREP_STORY -> Historian.getStoryForPlanet -> FileLoader.fetchStories`; then `oldstory_intro -> READ_INTRO -> FireAll OldStoryReader`, `oldstory_readnext -> READ_NEXT -> FireAll OldStoryReader -> HAS_NEXT ? readnext : outro`, `oldstory_outro -> READ_OUTRO -> Historian.setStoryRead -> surveyPerform` (now passes through because `$oldstory_done` is set).

## Flow

`PREP_STORY` (`SurveyStoryReader:59-80`) returns false (rule does not fire, vanilla survey runs) unless the target is a `PlanetAPI` whose market survey level is not `FULL` and `$oldstory_done` is unset. It then asks `Historian.getStoryForPlanet`; a null story also returns false. On success it stores the story id, resets the chapter to 0 and sets `$oldstory_done`.

`Historian.getStoryForPlanet` (`:36-72`):

1. `$indEvo_fixedStoryId` on the planet wins.
2. Return null for systems tagged `SYSTEM_CUT_OFF_FROM_HYPER`, `THEME_HIDDEN`, `THEME_SPECIAL`, `SYSTEM_ABYSSAL`, or planets tagged `NOT_RANDOM_MISSION_TARGET`.
3. For each story not in `toldStories`: ruins filter (any listed condition id present, or empty), required-condition filter (any listed condition present, or empty), planet type filter (`planet.getTypeId()` in list, or empty). In dev mode every story passes.
4. Weighted pick by `picker_weight`.

List columns are split on `;` or `,` after removing whitespace (`FileLoader:41-43`).

## Contracts

- The intercept rule runs at score 1000 on vanilla's `surveyPerform` option; any other mod rule on the same trigger with a higher score wins.
- `Historian` is saved in sector memory with its `Random` and `toldStories`. Renaming the class or package breaks saves (inferred, XStream).
- `$oldstory_done` is set before the player chooses to listen; ignoring the story uses up the planet but not the story.
- `getEntry(i)` returns null for empty parts; `READ_NEXT` then adds a null paragraph (inferred harmless).

## Known issues and dormant code

| Item | State |
|---|---|
| Blank CSV rows | 12 empty rows load as stories with id `""` and weight 0 (inferred: never picked) |
| `stories_TODO.csv` | Draft, unused; its columns would throw `JSONException` if pointed to by `oldstory_csv_path` |
| `READ_OUTRO` | `//todo add a record item to inventory` (`SurveyStoryReader:39`) |
| `$indEvo_fixedStoryId` | Read only, never set |
| Changelog_todo | No open entries |

## Maintenance

Update this doc when the story CSV format, the rules rows, the rule command or the picker filters change. Story text itself is player-facing content; edit it with the player-text rules. Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
