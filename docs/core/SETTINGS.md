# Settings

How IE reads player settings and config files: LunaLib settings, `settings.json` keys, and the whitelist and blacklist CSVs. Read it before adding, renaming or reading a setting. The settings each feature uses are listed in that feature's doc; this guide owns the mechanism, not the values. A move to a typed, cached model is planned in [SETTINGS_REWORK.md](SETTINGS_REWORK.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`.

## Overview

Player settings live in `data/config/LunaSettings.csv`. `indevo.config.PlayerSettings` caches them; callers read them through the `indevo.config.Setting` constants. Static config (whitelists, production amounts, sprite keys, rule command packages) lives in `data/config/settings.json` and `data/config/indEvo/*.csv`. Caching is covered under [Reading a setting](#reading-a-setting) and [Whitelists](#whitelists).

## Files

| File | Owner / role |
|---|---|
| `indevo/config/Setting.java` | One constant per LunaLib field (151): `id`, `type` (`BOOLEAN`, `INT`, `DOUBLE`), `fallback`, `min`, `max`, `block`, `apply` (`LIVE`, `NEW_GAME`); `sanitize`, `byId`, reads `enabled()`, `value()`, `intValue()` |
| `indevo/config/PlayerSettings.java` | Cache: `install`, `reload`, LunaLib listener, write back, `applyCampaignSettings` |
| `data/config/LunaSettings.csv` | LunaLib fields: id, label, type, default, help text (`fieldDescription`), bounds, tab |
| `data/config/LunaSettingsConfig.json` | LunaLib mod entry: settings menu icon `graphics/icons/IndEvo.png` |
| `data/config/settings.json` | Non-player config, sprite keys, `ruleCommandPackages`, `designTypeColors` |
| `data/config/indEvo/*.csv` | Whitelists and blacklists, one `id` column each |
| `indevo/utils/helper/MiscIE.java:578` | `getCSVSetFromMemory(path)`: loads and caches a whitelist |
| `indevo/ids/Ids.java` | Whitelist path constants (`EMBASSY_LIST` and so on) read from `settings.json` at class init |

## LunaLib settings

| Tab | Fields | Content |
|---|---|---|
| Quick Settings | 17 | Disclaimer; Content: the content switches `IndEvo_block_industries`, `IndEvo_block_consumables`, `IndEvo_Enable_Derelicts`, `IndEvo_Enable_Artillery`, `IndEvo_Enable_minefields`, `IndEvo_meteor_enabled`, `IndEvo_pets`; General: commerce balance changes, ship picker rows, meteor and artillery spawn chance, derelict station and automated shipyard amounts |
| Exploration & Items | 14 | Salvage specials, derelict stations, relic components, `IndEvo_consumables_*` |
| Meteor Swarms | 16 | `IndEvo_meteor_*` tuning |
| Minefields | 10 | `IndEvo_Minefield_*` |
| Artillery | 24 | `IndEvo_Artillery_*` tuning, watchtower |
| Industries | 26 | One enable toggle per industry (`IndEvo_AdManuf`, `IndEvo_embassy`, ...), governance, world wonders |
| Production | 21 | Variable industries, supercomputer, salvage yards, engineering hub, restoration docks |
| Derelict Industries | 19 | Derelict industry tuning |
| Colony Services | 31 | Academy, museum, courier port, privateer base |

LunaLib orders tabs by their first row in the CSV and skips rows with an empty `fieldID` (blank spacer rows). Content switches stay in Quick Settings; a block constant's row must be there.

Field counts include `Header` and `Text` rows. Every `Setting` constant has a LunaLib row; the rows without a constant are headers and text.

### Reading a setting

```java
if (!Setting.EMBASSY.enabled()) return;
```

- `enabled()` for `BOOLEAN`, `value()` (float) for `DOUBLE`, `intValue()` for `INT`. `enabled()` returns false while the setting's `block` parent is off and leaves the stored value alone, so switching the block back on restores the player's choices. One level only. Blocks: `BLOCK_INDUSTRIES` over the 19 industry toggles, `BLOCK_CONSUMABLES` over the three consumable toggles, `ENABLE_DERELICTS` over `ENABLE_DERELICT_STATIONS`. A block constant is declared before its children (enum forward references do not compile). `intValue()` on a `DOUBLE` truncates. `Setting.byId(id)` throws `IllegalArgumentException` for an unknown id.
- `PlayerSettings.install()` runs first in `ModPlugin.onApplicationLoad` and `onGameLoad`: it adds the listener once (`hasSettingsListenerOfClass`) and reloads. `onNewGame` reloads too: LunaLib calls no listener for a Save in the new game settings panel (`LunaSettingsUIModsPanel`, `if (!this.newGame)`), but stores it in `LunaSettingsLoader.getSettings()`.
- `reload()` forces LunaLib's lazy load, reads `LunaSettingsLoader.getSettings().get("IndEvo")`, runs `Setting.sanitize` on every field (missing or wrong type gives the CSV default, numbers clamp to `min`/`max`, `INT` rounds), applies the pair rule, writes changed values back and calls `save()`. Pair rule (`PlayerSettings.PAIRS`): a max below its min is raised to the min, for artillery delay between shots, cooldown and range, meteor intensity, distance from sun and angle, and museum additional credits. With no LunaLib data it logs an error and uses the defaults. In dev mode it logs `Reloaded IndEvo settings`.
- The listener reloads on every Save in the LunaLib menu, in the campaign and on the title screen, so a value read at use time applies on the next read. In the campaign (`GameState.CAMPAIGN`) it then runs `PlayerSettings.applyCampaignSettings()`, which applies settings that change campaign state: the pet ability follows `IndEvo_pets` ([PETS](../industries/PETS.md#registration-and-lifecycle)). On the title screen it skips that, because `Global.getSector()` still holds the previous game; `ModPlugin.onGameLoad` calls it for the loaded game. Add a new side effect there, not in `onGameLoad`. LunaLib logs listener exceptions at debug level only, so `settingsChanged` logs them with `log.error`. A dev mode F8 reload runs no listener (`onDevModeF8Reload`); the cache catches up on the next hook or Save.
- Code reads a setting at use time and never copies it into a static field (`grep -rnE 'static .*= *Setting\.' jars/src` finds none). A read is an `EnumMap` lookup, so per frame reads are fine. Objects stored in the save that copy a value at creation keep it; the feature doc says which.
- Industry toggles are read in each industry's `isAvailableToBuild()` and `showWhenUnavailable()` (example: `indevo/industries/embassy/industry/Embassy.java:95`, `:105`). A disabled industry stays on markets that already have it.

### Adding a setting

1. Add the row to `LunaSettings.csv` in the right tab, with `defaultValue`, `minValue`, `maxValue` and the help text in `fieldDescription`. Help text is player-facing. Every `Int` and `Double` row needs both bounds as plain numbers: LunaLib uses 0 and 100 for an empty bound and clamps typed values to it.
2. Add the `Setting` constant with the same `fieldID` (prefix `IndEvo_`), type, default and bounds as the row. A setting read only during world generation gets `Apply.NEW_GAME` and its help text ends with "Needs a new game.". A switch that also stops world generation placement stays `LIVE` and its help text says which part needs a new game ("Placement needs a new game.", "NPC placement needs a new game.").
3. Read it through the constant. Do not call `LunaSettings` or `Global.getSettings()` directly for a LunaLib field.
4. Add the key to the feature doc's Data and ids table.

## settings.json

| Key group | Use | Reader |
|---|---|---|
| `IndEvo_AdAssem_amounts`, `IndEvo_AdManuf_amounts`, `IndEvo_ComForge_amounts` | Output per commodity for the variable manufactories | [Assembler](../industries/ASSEMBLER.md) |
| `adminSalaryTier1` to `3` | Vanilla override, marked "do not touch" | Vanilla |
| `IndEvo_*List` | Paths of the whitelist CSVs | `Ids` |
| `oldstory_csv_path` | Survey story CSV path | [Survey stories](../exploration/SURVEY_STORIES.md) |
| `IndEvo_ToggleMagicRandomSelector` | Debug switch for hull forge random picks | `indevo/industries/derelicts/industry/HullForge.java:287` |
| `ruleCommandPackages` | Packages the rules engine searches for IE rule commands | [Rule commands](../dialogue/RULE_COMMANDS.md) |
| `designTypeColors` | Colors for IE design types (Experimental, Consumable, Cryochamber) | Vanilla |
| `graphics` | Sprite keys under `IndEvo` and feature categories | Callers via `Global.getSettings().getSprite(category, key)` |

## Whitelists

| `Ids` constant | `settings.json` key | File | Feature |
|---|---|---|---|
| `EMBASSY_LIST` | `IndEvo_embassyList` | `embassy_whitelist.csv` | [Embassy](../industries/EMBASSY.md) |
| `REVERSE_LIST` | `IndEvo_reverseList` | `reverse_engineering_whitelist.csv` | [Engineering hub](../industries/ENGINEERING_HUB.md) |
| `PRINT_LIST` | `IndEvo_printList` | `printing_whitelist.csv` | [Derelicts](../industries/DERELICTS.md) |
| `SHIPPING_LIST` | `IndEvo_shipingList` | `shipping_submarkets_whitelist.csv` | [Courier port](../industries/COURIER_PORT.md) |
| `ORDER_LIST` | `IndEvo_custOrderList` | `custom_order_blacklist.csv` | Custom orders |
| `BUREAU_LIST` | `IndEvo_buerauList` | `bureau_industry_whitelist.csv` | [Small industries](../industries/SMALL_INDUSTRIES.md) |
| `RUIND_LIST` | `IndEvo_ruindList` | `ruins_industry_whitelist.csv` | [Derelicts](../industries/DERELICTS.md) |
| `CLOUD_LIST` | `IndEvo_cloudList` | `graphics/clouds/clouds.csv` | Cloud painter |
| `LAB_LIST` | `IndEvo_labList` | `lab_commodity_whitelist.csv` | [Derelicts](../industries/DERELICTS.md) |
| `TREE_LIST` | `IndEvo_treeList` | `world_tree_whitelist.csv` | [World wonder](../industries/WORLD_WONDER.md) |
| `RURAL_LIST` | `IndEvo_ruralList` | `rural_polity_blacklist.csv` | [Changeling](../industries/CHANGELING.md) |
| `ARCOLOGIES_LIST` | `IndEvo_arcologiesList` | `hidden_arcologies_blacklist.csv` | [Changeling](../industries/CHANGELING.md) |
| `ARCOLOGIES_COND_LIST` | `IndEvo_arcologiesCondList` | `hidden_arcologies_supressed_whitelist.csv` | [Changeling](../industries/CHANGELING.md) |

The feature column is the main consumer; the feature doc is the owner of how the list is applied.

- `MiscIE.getCSVSetFromMemory(path)` reads the file with `getMergedSpreadsheetDataForMod("id", path, "IndEvo")`, so other mods can add rows by shipping a CSV at the same path. The set is cached in `SessionTransientMemory` under `"$" + path`.
- `ModPlugin.loadTransientMemory()` loads every list on game load, together with `getVPCItemSet()`, `getVayraBossShips()` and `getPrismBossShips()` ([Compatibility](COMPATIBILITY.md)).
- Read a list only through `getCSVSetFromMemory`; never parse the CSV again in a feature.

## Contracts

- LunaLib field ids have no `settings.json` value. Do not add one: `PlayerSettings` never reads it.
- `Ids` reads the whitelist paths from `settings.json` in static initializers, so `Ids` must not be loaded before settings are available (any time after application load is fine).

## Maintenance

Update this guide when the settings reader, the LunaLib tabs, a `settings.json` key group or a whitelist changes. Individual setting values and their meaning belong in the feature doc. Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
