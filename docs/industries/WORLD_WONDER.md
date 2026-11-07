# World Wonders

The World Symbol structures (`IndEvo_BaseFlavourInd` and its nine sub items): flavour industries that give immigration, stability and one story point, plus the Cloud Painter and Tree of Life planet effects. Read it before adding a wonder, changing the stability condition or the Cloud Painter shield visuals.

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

All wonders share `WorldWonder`: +10 immigration (independent faction), monthly reduction of recent unrest by 1, a one time story point per market, no AI cores. While functional a wonder tags its star system and adds the hidden `IndEvo_WorldWonderCondition` to every same faction market in that system, which gives +1 stability. Four subclasses add build requirements or planet effects: Ice Palace (cold), Lava Fortress (very hot), Tree of Life (vegetation planet, suppresses inimical biosphere) and Cloud Painter (atmosphere, cycles cloud textures, picks the planetary shield visuals). Two wonders have an alternate industry image.

## Files

| File | Owner / role |
|---|---|
| `indevo/industries/worldwonder/industry/WorldWonder.java` | Base plugin for all rows. Immigration (`modifyIncoming`), system tag and condition spread (`apply`), unrest (`reportEconomyMonthEnd`), story point (`finishBuildingOrUpgrading`), alternate image (`getCurrentImage`, `getAlternateImagePath`) |
| `indevo/industries/worldwonder/industry/IcePalace.java` | Build check: planet with `cold` or `very_cold` |
| `indevo/industries/worldwonder/industry/LavaFortress.java` | Build check: planet with `very_hot` |
| `indevo/industries/worldwonder/industry/WorldTree.java` | Build check: `habitable` planet whose type is in the tree list; suppresses `inimical_biosphere`; name "Eggdrasil" when the market name contains "egg" |
| `indevo/industries/worldwonder/industry/CloudPainter.java` | Build check: planet without `no_atmosphere`. Cloud texture cycle (`nextTexture`, `applyVisuals`) |
| `indevo/industries/worldwonder/patches/CloudPainterShieldPatch.java` | PatchLib redirect of `applyVisuals` in vanilla `PlanetaryShield.apply`: shield visuals from the market tags while a Cloud Painter works; `@After` on `PlanetaryShield.addPostDemandSection`: shield visuals tooltip line; tag constants, `hasFunctionalCloudPainter`, `getDefaultTag` |
| `indevo/industries/worldwonder/patches/PlanetBackgroundRefreshPatch.java` | PatchLib `@After` on the colony screen planet background's `advanceImpl`: rebuilds the background when the planet spec changed |
| `indevo/industries/worldwonder/conditions/WorldWonderCondition.java` | Hidden condition: +1 stability while any functional wonder of the market faction is in the location |
| `indevo/industries/worldwonder/plugins/WorldWonderAltImageOptionProvider.java` | Industry option "Change Visuals": toggles `WorldWonder.isAlternateVisual`; only on player wonders with an alternate image |
| `indevo/industries/worldwonder/plugins/WorldWonderTexChangeOptionProvider.java` | Industry option "Change cloud texture": calls `CloudPainter.nextTexture()` |
| `indevo/industries/worldwonder/plugins/ShieldVisualsOptionProvider.java` | Industry option "Change Shield Visuals" on a functional `planetaryshield` of a market with a functional Cloud Painter: cycles the market tags, then reapplies the shield |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Industry | `IndEvo_BaseFlavourInd` (World Symbols), tags `flavor, parent_item`, data `flavor` | `data/campaign/industries.csv:105` |
| Industry | `IndEvo_LightPalace`, `IndEvo_DarkPalace`, `IndEvo_Memorial`, `IndEvo_LeisureOrbitals`, `IndEvo_Megachurch` (`WorldWonder`) | `data/campaign/industries.csv:106-118`, tags `flavor, sub_item, structure, unraidable`, cost mult 100, build 90, upkeep 10 |
| Industry | `IndEvo_IcePalace`, `IndEvo_LavaFortress`, `IndEvo_LifeTree` (`WorldTree`), `IndEvo_CloudPainter` | `data/campaign/industries.csv:121-132`, same values |
| Condition | `IndEvo_WorldWonderCondition` (`Ids.COND_WORLD_WONDER`) | `data/campaign/market_conditions.csv:31`, `hidden, technical` |
| System tag | `IndEvo_hasWonder` (`WorldWonder.TAG_SYSTEM_HAS_WONDER`) | star system tags |
| Memory key | `$IndEvo_hasAwardedSP` (`HAS_AWARDED_SP`) | market memory |
| Memory key | `$IndEvo_alternateWonderVisual` (`ALTERNATE_VISUAL`) | market memory; read, never set by IE |
| Sprites | category `IndEvo`, keys `palace_light_2`, `kadur_megachurch` (CSV `data` column) | `data/config/settings.json:162-163` |
| List | `IndEvo_cloudList` -> `graphics/clouds/clouds.csv` (`Ids.CLOUD_LIST`) | `data/config/settings.json:59` |
| List | `IndEvo_treeList` -> `data/config/indEvo/world_tree_whitelist.csv` (`Ids.TREE_LIST`): `jungle`, `terran`, `terran-eccentric` | `data/config/settings.json:61` |
| Settings | `IndEvo_CPShieldRemove`, `IndEvo_CPShieldOverride` | `data/config/LunaSettings.csv:116-117`; constants in `indevo/config/Setting.java:143-144`, read by `CloudPainterShieldPatch.getDefaultTag` |
| Market tags | `IndEvo_PS_FULL`, `IndEvo_PS_LOW`, `IndEvo_PS_NONE` | `CloudPainterShieldPatch` |
| Sprite | `IndEvo` / `shield_texture` (alternate shield texture) | `data/config/settings.json:195` |

The `parent_item` / `sub_item` tags group the wonders under one build list entry (inferred from the tags). Five wonders are in `ruins_industry_whitelist.csv:22-26`, so [Ruined Infrastructure](RUINED_INFRASTRUCTURE.md) can restore into them.

## Registration and lifecycle

- CSV plugin column per row.
- `ModPlugin.setListenersIfNeeded()`: `WorldWonderAltImageOptionProvider.register()`, `WorldWonderTexChangeOptionProvider.register()` (`ModPlugin.java:448-449`), `ShieldVisualsOptionProvider.register()` (`:477`), all transient and deduped by `hasListenerOfClass`.
- `CloudPainterShieldPatch` and `PlanetBackgroundRefreshPatch` are `@Patch` classes; PatchLib installs them when `PlanetaryShield` and the colony screen planet background load ([Patches](../ARCHITECTURE.md#patches)).
- `BaseIndustry.apply` adds the industry as transient immigration modifier because it implements `MarketImmigrationModifier` (skill `api/impl/campaign/econ/impl/BaseIndustry.java:209`).
- `apply()` adds the industry as transient `EconomyTickListener` (`WorldWonder.java:61`); `ListenerManager.addListener` dedupes the same instance (skill `obf/campaign/ListenerManager.java:29`). `unapply()` removes it.
- Lists are loaded by `MiscIE.getCSVSetFromMemory`; see [Utils](../core/UTILS.md).

Chain: `WorldWonder.apply -> system tag IndEvo_hasWonder -> addCondition(IndEvo_WorldWonderCondition) on same faction markets -> WorldWonderCondition.apply -> stability +1`.

## Flow

### Base wonder

- `apply()` (`WorldWonder.java:57`): `super.apply(true)`, then, if functional and not in the build tooltip, tag the system and spread the condition.
- `reportEconomyMonthEnd`: `RecentUnrest.add(-1)` when the penalty is above 0.
- `finishBuildingOrUpgrading`: player market without `$IndEvo_hasAwardedSP` gets 1 story point and the flag. One point per market, not per wonder.
- `getCurrentImage`: alternate image when `isAlternateVisual` or the memory flag is set and `getAlternateImagePath()` resolves `getSpriteName("IndEvo", spec.getData())`; a missing key throws, which is caught and returns null.

### Cloud Painter

- `init` stores the planet's original cloud texture. `cloudNum` 0 is the original; 1..n index the cloud list in set order. `getTexture` falls back to the original if `loadTexture` fails.
- `applyVisuals` sets `PlanetSpecAPI.setCloudTexture` and calls `applySpecChanges` (skill `api/campaign/PlanetAPI.java:27-35`).

### Cloud Painter shield visuals

`PlanetaryShield.apply` calls `applyVisuals(planet)` only while the shield is functional (skill `api/impl/campaign/econ/impl/PlanetaryShield.java:33-35`). `CloudPainterShieldPatch` redirects that call:

- No functional `CloudPainter` on the market: calls down, vanilla visuals.
- Otherwise a market with none of the three tags gets `getDefaultTag()`: NONE if `IndEvo_CPShieldRemove`, else LOW if `IndEvo_CPShieldOverride`, else FULL. Markets that already carry a tag keep it.
- FULL calls down, LOW applies the `IndEvo` / `shield_texture` sprite, NONE calls `PlanetaryShield.unapplyVisuals`.
- Option "Change Shield Visuals" cycles FULL -> LOW -> NONE, then calls `reapply()` on the shield, so the redirect runs at once. Its tooltip lists full, faint and hidden visuals.
- Shield tooltip: `afterPostDemandSection` adds "Shield visuals: %s" (Full, Faint or Hidden; untagged markets show `getDefaultTag()`) under vanilla's ground defense line, with the same condition (tooltip mode other than `NORMAL`, or a functional shield) and only while a Cloud Painter works. Vanilla `PlanetaryShield` declares `addPostDemandSection` (skill `api/impl/campaign/econ/impl/PlanetaryShield.java:93-99`); the spot right below the description is `addRightAfterDescriptionSection`, a `BaseIndustry` method IE does not patch.

The rest of vanilla `apply` (ground defense x3, upkeep, meteor impact suppression) and the AI core stay untouched; the industry is the vanilla one.

### Colony screen planet

The colony screen draws the planet from its own background panel (obf `com.fs.starfarer.campaign.ui.marketinfo`, the core UI's `getPlanetBackground()`). Its `setPlanet` clones the planet renderer and builds the sprites once (skill `obf/campaign/ui/marketinfo/nullnew.java:60-90`), and `CampaignPlanet.applySpecChanges` hands the spec to the renderer, then swaps in a clone (skill `obf/campaign/CampaignPlanet.java:316-319`). So a spec change made while the screen is open (cloud texture, shield visuals) needs the panel rebuilt to show.

`PlanetBackgroundRefreshPatch` hooks the panel's `advanceImpl`: it keeps the planet's `getSpec()` from the last frame in the panel's `getData()` and calls `setPlanet` again when the identity changed. One identity check per frame while the panel is shown; covers spec changes from any source. Every market reapply of a planet with a Cloud Painter or Planetary Shield changes the spec (both call `applySpecChanges` in `apply`), so the background rebuilds once per reapply. `setPlanet` restarts a fader whose value the render code overwrites with 0 (`nullnew.java:200`), so the rebuild has no fade (inferred from code, not tested in game).

### Tree of Life

- `apply` suppresses `inimical_biosphere` while functional; `unapply` unsuppresses.
- `notifyBeingRemoved` (not building, not for upgrade) adds `inimical_biosphere` if absent, so removing the tree leaves the planet worse than before (inferred: intended cost).

## Contracts

- The condition is added but never removed by IE. It checks the system tag and a functional wonder of its own faction on each apply, so it goes inert when the wonder is gone.
- `unapply` removes `IndEvo_hasWonder` even if another wonder in the system is still functional; the next `apply` of that wonder adds it back (inferred: brief stability flicker).
- The cloud texture stays after the Cloud Painter is removed: `unapply` does not restore `originalClouds` (inferred from `CloudPainter.java:35`). Shield visuals go back to vanilla on the shield's next apply, the market tags stay.
- `WorldWonderCondition` keys its mod `"WorldWonder"`, so several wonders give +1 total, not +1 each.

## Known issues and dormant code

| Item | State |
|---|---|
| `$IndEvo_alternateWonderVisual` | read in `getCurrentImage`, never set; dormant |
| `WorldWonder.reportEconomyTick`, `upgradeFinished` | empty |
| Open issue | `Changelog_todo:33`: "World Wonder stability application is wonky and should be handled through a condition instead" |

## Maintenance

Update this doc when a wonder row, its build check, the condition or the alternate image keys change. List loading belongs in [Utils](../core/UTILS.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
