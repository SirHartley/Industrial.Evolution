# Skillshot Framework

Aimed campaign abilities for Starsector. Hold an ability hotkey to aim and release it to fire. Clicking the ability instead starts aiming; the next map click fires.

The framework started as IE's consumable missile targeting, was extracted and improved in Catch.Release (`catchrelease.skillshot`) and ported back here as `indevo.skillshot`. The two copies keep the same file layout so fixes port by renaming the package. IE's consumers are the missiles in [Consumables](../../../../docs/items/CONSUMABLES.md); this guide owns the framework's hooks, registration and lifecycle. Player-facing text follows the house-style `player-text.md`.

Read this guide before implementing an ability or changing targeting hooks, registration or lifecycle.

## Requirements

- Starsector 0.98a API
- [LunaLib](https://github.com/Lukas22041/LunaLib) for campaign rendering (`LunaCampaignRenderer`). It is the only third-party dependency.

## Install

1. The Java package is `jars/src/indevo/skillshot/`. Its assets are IE's own, set in `SkillshotSettings`: sprites `fx/IndEvo_missile_targetting_reticule`, `fx/IndEvo_missile_targetting_arrow`, `fx/IndEvo_skillshot_target` (`data/config/settings.json`, same art as Catch.Release's `skillshot_*` sprites) and sound `IndEvo_denied_buzzer` (`data/config/sounds.json`).

2. Call the framework from the `ModPlugin`:

   ```java
   @Override
   public void onGameLoad(boolean newGame) {
       SkillshotFramework.register();
   }

   @Override
   public void beforeGameSave() {
       SkillshotFramework.reset();
   }
   ```

   `register()` adds the hotkey listener and the activation manager and is idempotent. Both are transient, so nothing of the framework is saved. `reset()` cancels a half-aimed shot and releases the cross mod lock before the save. IE calls them from `ModPlugin.setListenersIfNeeded()` and `ModPlugin.beforeGameSave()`.

Never ship another mod's copy under its package name: all mod jars share one classloader, so a second `catchrelease.skillshot` would collide with Catch.Release's.

## Writing an ability

Extend `BaseSkillshotAbility` and implement three methods:

```java
public class MyMissileAbility extends BaseSkillshotAbility {

    @Override
    public SkillshotRenderer createReticule() {
        return new DirectionReticuleRenderer();
    }

    @Override
    protected void onSkillshotFired(Vector2f worldTarget, float angleFromFleet) {
        // spawn the missile, drop the mine, call the artillery
    }

    @Override
    public void addTooltip(TooltipMakerAPI tooltip) {
        tooltip.addPara("Does something at the place you point at.", 10f);
    }
}
```

Then add a row to `abilities.csv` with **`IndEvo_skillshot` in the tags column** and the class in the plugin column. The tag is how the hotkey listener finds the ability in the ability bar; without it only the click path works. A tagged ability whose plugin is not a `SkillshotAbility` throws `IllegalStateException` on its hotkey, since only an IE bug gets there.

Hooks on `BaseSkillshotAbility`:

| Method | Purpose |
| --- | --- |
| `createReticule()` | New reticule per targeting session |
| `onSkillshotFired(target, angle)` | Action after a targeted shot |
| `getActivationVolume()` | Activation sound volume, default 1 |
| `onConsume()` | Runs once per successful shot: remove the item, spend a charge |
| `isTargetingBlocked()` | Why aiming can't start right now; override to add conditions |
| `addTooltip(tooltip)` | Tooltip body; `createTooltip` appends the blocked reason lines |
| `showReticuleOnActivation()` | Whether this press is aimed at all, default true |
| `onActivatedWithoutReticule()` | Action for an unaimed press |

`forceActivation()` fires: `activateImpl()` (cursor position and angle into `onSkillshotFired`), `onConsume()`, then the cooldown from the `deactivationCooldown` column. The cooldown is the rearm time and starts on fire, not on button press.

## Abilities that are only sometimes aimed

Override `showReticuleOnActivation()`. It is asked on every press, so it can depend on game state. While it returns false the framework stays out of the way: the hotkey is left unconsumed, `pressButton()` falls through to vanilla `BaseDurationAbility`, no reticule is created, `isTargetingBlocked()` stops restricting the ability (it works from core UI tabs like a vanilla ability) and the blocked reason lines disappear. The activation lands in `onActivatedWithoutReticule()` instead of `onSkillshotFired(...)`, since there is no aim point.

## Reticules

| Class | Looks like | For |
| --- | --- | --- |
| `DirectionReticuleRenderer` | Arrow at the cursor | Things fired in a direction |
| `AreaReticuleRenderer(size)` | Circle at the cursor, sized to the effect | Things that land on a spot |
| `ValidatedAreaReticuleRenderer(size, validator)` | Same, turns red and refuses to fire on rejected positions | Restricted targeting |

A `PositionValidator` is one method, `boolean isValid(Vector2f worldPos)`. `MarketProximityValidator(safeRadius)` rejects points within primary entity radius plus `safeRadius` of any market in the location. For anything else, subclass `BaseReticuleRenderer` and implement `renderCursorBoundObject(...)`. The fleet ring, cursor tracking and the valid or invalid tint are handled; `cursorPos` holds the aim point of the last render and is safe to read from `isValidPosition()`.

### Guide lines

Any reticule can draw lines out from the fleet along the aim direction. All are opt in:

```java
new DirectionReticuleRenderer().withTrajectory()   // one line: where the shot goes
new AreaReticuleRenderer(400f).withBounds(30f)     // two lines: the edges of a 30 degree spread
new DirectionReticuleRenderer().withTrajectory().withBounds(30f).withLength(2000f)
```

`withLength(range)` stops the lines at that range; without it they run to the cursor. Lines start outside the fleet ring and stop short of the cursor sprite (`getGuideLineEndPadding()`: the area circle's edge, 0 for the arrow). `withLineStyle(GuideLineStyle.DASHED)` picks `SOLID`, `DASHED` or `DOTTED`. Dash lengths are screen pixels, converted through the viewport by `SkillshotUtils.cutDashes()`; no GL stippling. No IE ability uses guide lines yet.

## Configuration

`SkillshotSettings` holds every sprite id, sound id, size and colour, the tag, the lock owner id and `LOG_DEBUG` (default false; logs state changes to `starsector.log`). All fields except `LOCK_KEY` are writable at startup.

| Setting | IE value |
| --- | --- |
| `TAG_SKILLSHOT` | `IndEvo_skillshot` |
| `DIRECTION_ARROW_SIZE` | `170` |
| `DEFAULT_AREA_SIZE` | `400` |
| `FLEET_RETICULE_PADDING` | `250` |
| `GUIDE_LINE_*` | Width 2 px, alpha x0.45, dash 24/24 px, dot 4/28 px |

## How it works

**Hotkey.** `OnKeyPressSkillshotListener` is one transient `CampaignInputListener` at priority 0. It takes keys 1 to 9 before the UI turns them into a button press (that would fire the ability at once, with nothing to aim), checks the slotted ability (the hyperspace ability while in hyperspace) for the tag and opens a session. Releasing the same key fires the ability seen on key down. A repeat key down while aiming is consumed and the scan goes on.

**Click.** `BaseSkillshotAbility.pressButton()` plays the activation sound and queues the ability on `SkillshotActivationManager`. The manager starts an `OnClickSkillshotListener` on its next `advance`, because a listener added inside `pressButton()` would eat the bar click that opened it. The listener is transient and removes itself on every exit (fire, abort, dialog, reset); input listeners run from a copy of the list (`ListenerUtil.getSortedInputListeners`, `ListenerManager.getListeners` in the knowledge skill), so it removes itself during input. The next left click on the map fires; the mouse down is consumed so it can't move the fleet.

Both paths ask `showReticuleOnActivation()` first and do nothing if it says no. Either cancels on an open dialog, on ctrl (the vanilla slot reassign modifier, hotkey path) or on another key down (click path; space and left shift pass). Firing at a position the reticule rejects plays `SOUND_INVALID_TARGET`; the hotkey session ends, the click session keeps aiming.

`SkillshotActivationManager` is a transient `EveryFrameScript` that runs while paused. It holds the one live session, clears it when it goes inactive and backs `isTargetingBlocked()` across abilities.

## Cross mod lock

Catch.Release ships the same framework under `catchrelease.skillshot` and also listens to keys 1 to 9. One sector memory key stops the two from aiming at once. Plain values only, so neither mod references the other's classes.

| Key | Value | Set | Read |
| --- | --- | --- | --- |
| `$sirhartley_skillshotActive` | Owner mod id: `IndEvo` or `catchrelease` | `SkillshotActivationManager.setCurrentListener` (session start) | `hasActiveSession()`: a value that is not the own id blocks targeting and shows "Conflicting ability in use!" |

- A mod unsets the key only when the value is its own id: on the manager `advance` after its session goes inactive, and in `reset()` before a save.
- `register()` clears the key on load whatever its value; nothing aims during a load, so a key found then is stale.
- No memory expiry: it does not tick while the game is paused, and aiming is mostly done paused.
- The tags differ (`IndEvo_skillshot`, Catch.Release `skillshot`) and both check `instanceof SkillshotAbility` of their own package, so each framework only handles its own abilities.
- Owner: `SkillshotLock`. Catch.Release needs the same key and checks to take part; until it has them, IE sets and clears the key alone and the lock has no effect.

## Differences from Catch.Release

- Listener and manager are transient and found by type on each load (Catch.Release: persistent, saved).
- The click session starts from the manager's `advance`, not a `DelayedActionScriptRunWhilePaused`.
- The click listener is transient and removes itself on every exit (Catch.Release: persistent `addListener(this)`, removed only on the key abort path, so each fired shot leaves a dead listener in the save).
- Key up fires the ability seen on key down; a tagged non skillshot plugin throws.
- `forceActivation()` sets the cooldown; the listeners don't.
- Cross mod lock, `SkillshotLock`.
- No `PondProximityValidator` (Catch.Release rod code); `LOG_DEBUG` defaults to false.

## Maintenance

Update this README with changes to hooks, registration, assets, the lock or lifecycle, and the [Consumables](../../../../docs/items/CONSUMABLES.md) doc when a consumer changes. A fix that also applies to Catch.Release's copy is noted for that repo; IE never patches Catch.Release. Follow [CLAUDE.md](../../../../CLAUDE.md#documentation-upkeep).
