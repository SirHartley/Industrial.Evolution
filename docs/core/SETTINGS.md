# Settings

How IE reads player settings and config files: LunaLib settings, `settings.json` keys, and the whitelist and blacklist CSVs. Read it before adding, renaming or reading a setting. The settings each feature uses are listed in that feature's doc; this guide owns the mechanism, not the values.

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`.

## Overview

Player settings live in `data/config/LunaSettings.csv` and are read through `indevo.utils.helper.Settings`. Static config (whitelists, production amounts, sprite keys, rule command packages) lives in `data/config/settings.json` and `data/config/indEvo/*.csv`. Caching is covered under [Reading a setting](#reading-a-setting) and [Whitelists](#whitelists).

## Files

| File | Owner / role |
|---|---|
| `indevo/utils/helper/Settings.java` | One `public static final String` per LunaLib field id (152 ids) and the typed readers `getBoolean`, `getFloat`, `getInt` |
| `data/config/LunaSettings.csv` | LunaLib fields: id, label, type, default, help text (`fieldDescription`), bounds, tab |
| `data/config/LunaSettingsConfig.json` | LunaLib mod entry: settings menu icon `graphics/icons/IndEvo.png` |
| `data/config/settings.json` | Fallback values for the older settings, non-player config, sprite keys, `ruleCommandPackages`, `designTypeColors` |
| `data/config/indEvo/*.csv` | Whitelists and blacklists, one `id` column each |
| `indevo/utils/helper/MiscIE.java:578` | `getCSVSetFromMemory(path)`: loads and caches a whitelist |
| `indevo/ids/Ids.java` | Whitelist path constants (`EMBASSY_LIST` and so on) read from `settings.json` at class init |

## LunaLib settings

| Tab | Fields | Content |
|---|---|---|
| General | 9 | Disclaimer, commerce balance changes, government (changeling) settings, ship picker rows |
| Exploration & Campaign | 12 | Derelicts, derelict stations, relic components, crucibles |
| Meteor Swarms | 17 | `IndEvo_meteor_*` |
| Minefields | 11 | `IndEvo_Minefield_*` |
| Artillery | 26 | `IndEvo_Artillery_*`, watchtower |
| Consumable Items | 5 | `IndEvo_consumables_*` |
| Industries | 98 | One enable toggle per industry (`IndEvo_AdManuf`, `IndEvo_embassy`, ...) plus per industry tuning |

Field counts include `Header` and `Text` rows. Every `Settings` constant has a LunaLib row; the rows without a constant are headers and text.

### Reading a setting

```java
if (!Settings.getBoolean(Settings.EMBASSY)) return;
```

- `Settings.getX(id)` calls `LunaSettings.getX("IndEvo", id)` when `lunalib` is enabled, else `Global.getSettings().getX(id)` (`Settings.java:166` to `182`).
- `Settings` itself does not cache: each call goes to LunaLib, so a value read at use time applies on the next read.
- 16 classes copy settings into `static final` fields at class load (65 fields; `grep -rn 'static final .*= *Settings\.get' jars/src`), for example `MuseumConstants`, `CampaignAttackScript`, `MeteorSwarmManager`, `ShippingCostCalculator`. Those settings apply only after a game restart. Prefer reading at use time in new code; do not read in a per frame loop where a daily check works.
- Industry toggles are read in each industry's `isAvailableToBuild()` and `showWhenUnavailable()` (example: `indevo/industries/embassy/industry/Embassy.java:95`, `:105`). A disabled industry stays on markets that already have it.

### Adding a setting

1. Add the row to `LunaSettings.csv` in the right tab, with `defaultValue`, `minValue`, `maxValue` and the help text in `fieldDescription`. Help text is player-facing.
2. Add the constant to `Settings.java`, value equal to `fieldID`, prefix `IndEvo_`.
3. Read it through `Settings.getX`. Do not call `LunaSettings` or `Global.getSettings()` directly for a LunaLib field.
4. Add the key to the feature doc's Data and ids table.

## settings.json

| Key group | Use | Reader |
|---|---|---|
| `IndEvo_*` booleans and numbers matching LunaLib ids | Fallback for `Settings` when LunaLib is missing | `Settings` |
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

- LunaLib is a hard dependency in `mod_info.json`, so the `settings.json` fallback in `Settings` does not run in a normal install. It is also incomplete: 50 `Settings` ids (all `IndEvo_meteor_*`, `IndEvo_museum_*`, `IndEvo_courier_*`, `IndEvo_consumables_*` and others) have no `settings.json` value and would throw if the fallback ran. Inferred from a key diff of `Settings.java` against `settings.json`.
- `settings.json` value `IndEvo_Artillery_defense_FP` differs from the LunaLib id `IndEvo_Artillery_defense_FP2` that `Settings.ARTILLERY_DEFENSE_FP` uses.
- `Ids` reads the whitelist paths from `settings.json` in static initializers, so `Ids` must not be loaded before settings are available (any time after application load is fine).

## Maintenance

Update this guide when the settings reader, the LunaLib tabs, a `settings.json` key group or a whitelist changes. Individual setting values and their meaning belong in the feature doc. Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
