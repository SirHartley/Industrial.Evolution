# Distress calls and ghost ships

The unfinished port of Vayra's distress call and ghost ship system in `indevo/exploration/distress/`. Read it before reviving, deleting or touching that package, the ghost ship hullmod rows or `distress_call_data.csv`. It does not cover the live salvage specials; see [Salvage](SALVAGE.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

Planned design: while the player is in hyperspace, a manager rolls distress calls in nearby empty systems; the ghost ship call spawns a wreck with a boarding special that can leave one of six hidden or known "ghost ship" hullmods (rogue AI, dimensional anomalies, xenolife, plague, nanites, cannibals) on the recovered ship, each with campaign or combat side effects and an intel report.

Real status: not built and not running.

- `IndEvo.iml` excludes the whole folder (`excludeFolder .../exploration/distress`), and the IntelliJ artifact `.idea/artifacts/IndEvo_jar.xml` packs module output only, so none of these classes are in `jars/IndEvo.jar`.
- The code does not compile as is: it uses `GhostShipIntel`, `DistressCallIntel`, `ALIEN_ATTACK_TIMER`, `TIMER_HULLMOD_1..6`, `_DEBUG`, `PLAGUE_CONDITION` and intel type constants (`PLAGUE_PLANET`, `GATE_LOST`, `PLAGUE_THRESHOLD`, `NANITE_TRANSMISSION`, `CANNIBAL_ATTACK`), none of which exist; `PlaguePlanetIntel.java:15` calls a `GhostShipIntelBase` constructor that does not exist.
- The only references from outside are commented out: `//DistressCallManager.getInstanceOrRegister();` and `//HullmodTimeTracker.getInstanceOrRegister();` (`indevo/utils/ModPlugin.java:510-511`).
- `data/config/indEvo/distress_call_data.csv` is read only by `DistressCallManager.loadEvents()` (`events/DistressCallManager.java:32`, `:75`), so it is never read. Its one row points at Vayra's `data.scripts.campaign.events.VayraGhostShipDistressCall`, not at `GhostShipDistressCall`.
- Live leftovers in loaded data: ghost ship hullmod rows, ghost ship rules, the `Ids` constants. See Data and ids.

## Files

| File | Owner / role |
|---|---|
| `events/DistressCallManager.java` | Sector script, singleton in sector memory `indEvo_distressCallManager`. Loads the CSV, rolls a call every 14..35 days in hyperspace, picks system, sets the event id on system memory for 3 days, instantiates the script class by name, adds `DistressCallIntel` |
| `events/GhostShipDistressCall.java` | Event script (id `$indevo_ghostship`): spawns a `WRECK` near the distress jump point (combat/carrier hull, min crew >= 50, boardable), attaches `GhostShipSpecialData`, expires after 60 days |
| `events/GhostShipSpecial.java` | `BaseSalvageSpecial`: rolls danger (`NONE` 2, `UNIDENTIFIED` 4, `TOKEN` 3, `SIGNIFICANT` 2, `OVERWHELMING` 1) and type; board / demolish / leave; marine losses; hands over to vanilla `ShipRecoverySpecial` or flags destroy |
| `hullmods/GhostShip.java` | One hullmod plugin for all ghost ship ids: CR penalty from missing marines, combat effects (rogue AI defects vs Remnants, hyperlost phase slips, nanite swarm), campaign effects (crew/supply loss, plague raid listener, nanite spread, cannibal attack) |
| `hullmods/AlienHullmodEffect.java` | Split out xenolife effect: attack every 200..400 days via `HullmodTimeTracker`, `AlienAttackIntel` |
| `hullmods/CannibalHullmodEffect.java`, `NaniteHullmodEffect.java`, `HyperlostHullmodEffect.java`, `RogueAIHullmodEffect.java`, `PlagueHullmodEffect.java` | Empty class stubs |
| `listener/HullmodTimeTracker.java` | `NewDayListener` with per member day counters |
| `listener/EngineeredPlagueListener.java` | Raid listener: a raid with a plague ship adds the plague condition to the market |
| `intel/GhostShipIntelBase.java` | Abstract `FleetLogIntel` (text, image, bullets) |
| `intel/AlienAttackIntel.java` | Rolls the fight from marines vs min crew and removes crew and marines |
| `intel/GateLostIntel.java`, `AIBetrayalIntel.java`, `PlagueThresholdIntel.java`, `PlaguePlanetIntel.java`, `NaniteTransmissioIntel.java`, `CannibalAttackIntel.java` | Same shape: name, icon, text, crew loss bullet |
| `ruleCMD/HyperlostGateInteraction.java` | Rule command: crossing a gate with a hyperlost ship either cleanses it or loses it (50%) |
| `hull_mods.csv` | Vayra's original hullmod sheet. Not in `data/`, never loaded |
| `todo`, `intel/todo`, `credits` | Notes: planned events, "most of these classes are incomplete", credit to Vayra |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Hullmods (loaded, hidden) | `indEvo_ghost_ship_mystery_nothing`, `_mystery_rogue_ai`, `_mystery_hyperlost`, `_mystery_alien`, `_mystery_plague`, `_mystery_nanite`, `_mystery_cannibal`, `_rogue_ai`, `_hyperlost`, `_alien`, `_plague`, `_nanite`, `_cannibal`, `_unique_hyperlost` | `data/hullmods/hull_mods.csv:19-59`, script column `indevo.industries.petshop.hullmods.PetHullmod` (placeholder) |
| Hullmod id without row | `vayra_ghost_ship_mystery` (`MYSTERY_HULLMOD`), `vayra_ghost_ship_mystery_nothing` (`MYSTERY_NOTHING_HULLMOD`, row uses the `indEvo_` id), `indEvo_anti_robot` (`ANTI_AI_HULLMOD`) | `indevo/ids/Ids.java:105-122` |
| Constants | ghost ship hullmod ids, `COND_PLAGUE` `IndEvo_engineered_plague` | `Ids.java:96-122` |
| Market condition | `IndEvo_engineered_plague` (row commented out) | `data/campaign/market_conditions.csv:24` |
| Rules (loaded) | `indevo_ghostShipContinueSelected`, `indevo_ghostShipContinueSelectedDestroyed`, `indevo_ghostShipFinishedNoContinueDestroyed` on `$indevo_checkSalvageSpecialAgain` / `$indevo_destroyGhostShip` | `data/campaign/rules.csv:966-970` |
| Memory key | `$indevo_checkSalvageSpecialAgain`, `$indevo_destroyGhostShip` (entity) | `GhostShipSpecial.java:28-29` |
| Memory key | `$indevo_hasHyperlostGhostShip` (global), `$indevo_ghostShipTriggeredGate` | `GhostShip.java:86-87` |
| Config | `distress_call_id`, `distress_call_script` | `data/config/indEvo/distress_call_data.csv` |
| Setting | `distressCallEventRangeLY` (no key in `settings.json`) | `DistressCallManager.java:129` |

## Registration and lifecycle

None live. Intended chain:

`ModPlugin.setListenersIfNeeded() -> DistressCallManager.getInstanceOrRegister() -> loadEvents() -> advance() -> spawnDistressCallIfPossible() -> GhostShipDistressCall -> GhostShipSpecial -> ShipRecoverySpecial -> GhostShip hullmod`

The ghost ship rules in `rules.csv` load, but only match on memory keys set by `GhostShipSpecial`, so they never fire.

## Flow (as written)

- Distress roll (`DistressCallManager.java:123`): player in hyperspace, not transitioning. Systems within `distressCallEventRangeLY` with an allowed theme (`THEME_MISC`, `THEME_MISC_SKIP`, `THEME_RUINS`, remnant suppressed/destroyed/no fleets, `THEME_DERELICT`), no pulsar, not visited for 90 days, no market, no fleet non-hostile to independents. Chance `0.1 * systems`, max 0.2.
- Boarding (`GhostShipSpecial.optionSelected`): optimal party = min crew in crew and in marines; 10% of min crew is the minimum to try. `NONE`/`UNIDENTIFIED` roll success on crew ratio and may add a mystery hullmod (10% of unidentified are `_mystery_nothing`); higher dangers roll losses on marine ratio, add the known hullmod, then recover.
- Hullmod CR penalty (`GhostShip.applyEffectsBeforeShipCreation`): up to 10% max CR from marines in cargo vs summed min crew of ghost ships.
- Mystery hullmods are swapped for the known id when the effect first triggers.

## Contracts

- `GhostShip` keeps per ship combat and campaign state in instance fields (`hyperlost`, `naniteHosts`, `campaignGhostShipTimers` and others). Hullmod plugins are one shared instance per hullmod id (starsector-knowledge `SKILL.md` top trap 4, `docs/concepts/combat-frame.md`), so this state is shared across all ships and is not saved (inferred: hullmod plugin instances are not part of the save); it needs `ship.getCustomData()` or a sector-level store.
- Base classes from other features: `SelfRepairingBuiltInHullmod` and `PetHullmod` ([Pets](../industries/PETS.md)).
- `GhostShip.java` uses MagicLib (`MagicTrailPlugin`, `MagicRender`) and imports obfuscated `com.fs.starfarer.campaign.fleet.FleetMember`.

## Known issues and dormant code

| Item | State |
|---|---|
| Whole package | Excluded by `IndEvo.iml`; does not compile; unregistered (`ModPlugin.java:510-511` commented) |
| `distress_call_data.csv` | Never read; script class is Vayra's, not in IE |
| `HullmodTimeTracker.getInstanceOrRegister()` | Checks `hasListenerOfClass(AdminGovernTimeTracker.class)` instead of its own class (`:19`); would register a new tracker every call |
| `GhostShip.advanceInCombat()` | Hyperlost and nanite branches are nested inside the rogue AI `if` (`:158-225`), so they only run on rogue AI ships |
| `GhostShipSpecial` int division | `crew / optimal`, `marines / optimal` are `int` (`:215`, `:237`, `:288`, `:311`); any shortfall gives chance 0 |
| `AlienAttackIntel` bullets | Says "<ship> lost" even when the fight is won (`:90`) |
| `GhostShipDistressCall.setup()` | Calls `DistressCallManager.getInstanceOrRegister()`, which would add a second manager if none exists |
| Hullmod rows | Live in `hull_mods.csv` with `PetHullmod` as script; nothing adds them in a normal game |
| Changelog_todo | "Adapted and expanded Vayras Sector distress call system", "The original 6 Ghost Ship distress calls", "rewrote the implementation from the ground up" (`Changelog_todo:129-132`) |

## Maintenance

Update this doc when the package is removed from the `IndEvo.iml` exclusions, registered in `ModPlugin`, or when the ghost ship rows, rules or `Ids` constants change. Live salvage specials belong in [Salvage](SALVAGE.md). Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
