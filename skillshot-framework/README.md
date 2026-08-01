# Skillshot Framework

Campaign-layer aimed abilities for Starsector. Hold an ability's hotkey (or click it), a reticule
appears around your fleet pointing at the cursor, release/click and the ability fires at that spot.

Extracted from Industrial.Evolution's consumable missiles, with the IndEvo-specific parts stripped
out. Everything the framework needs is in this folder — source, sprites, sound, and the data entries
that register them.

## Requirements

- Starsector 0.98a API
- [LunaLib](https://github.com/Lukas22041/LunaLib) — used for campaign rendering
  (`LunaCampaignRenderer`). It is the only third-party dependency.

## Install

1. Copy the contents of this folder into your mod root, so you end up with:

   ```
   yourmod/
     data/campaign/abilities.csv
     data/config/settings.json
     data/config/sounds.json
     graphics/fx/skillshot_*.png
     skillshot/sounds/skillshot_denied.ogg
     jars/src/skillshot/...          <- move src/skillshot here, or wherever your sources live
   ```

   If your mod already has `settings.json`, `sounds.json` or `abilities.csv`, merge the entries
   rather than overwriting — the files here contain nothing but the framework's own additions.

2. Add LunaLib to `mod_info.json` dependencies if it isn't there already.

3. Call the framework from your `ModPlugin`:

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

   `register()` installs the hotkey listener and is idempotent. `reset()` cancels any half-aimed
   shot so it never ends up in the save.

## Writing an ability

Extend `BaseSkillshotAbility` and implement two methods:

```java
public class MyMissileAbility extends BaseSkillshotAbility {

    @Override
    public SkillshotRenderer createReticule() {
        return new DirectionReticuleRenderer();
    }

    @Override
    protected void onSkillshotFired(Vector2f worldTarget, float angleFromFleet) {
        // spawn your missile, drop your mine, call your artillery
    }

    @Override
    public void addTooltip(TooltipMakerAPI tooltip) {
        tooltip.addPara("Does something at the place you point at.", 10f);
    }
}
```

Then add a row to `abilities.csv` with **`skillshot` in the tags column** and your class in the
plugin column. The tag is how the hotkey listener recognises the ability in the ability bar; without
it only the click path works.

`skillshot.example.ExampleSkillshotAbility` and the `skillshot_example` row in `abilities.csv` are a
complete working ability you can grant yourself and try immediately.

Useful hooks on `BaseSkillshotAbility`:

| Method | Purpose |
| --- | --- |
| `createReticule()` | New reticule per targeting session |
| `onSkillshotFired(target, angle)` | The payload |
| `onConsume()` | Runs once per successful shot — remove the item, spend a charge |
| `isTargetingBlocked()` | Why aiming can't start right now; override to add your own conditions |
| `addTooltip(tooltip)` | Ability tooltip body; the framework appends its own blocked-reason lines |

The `deactivationCooldown` column in `abilities.csv` is the rearm time — the framework applies it on
fire, not on button press.

## Reticules

| Class | Looks like | For |
| --- | --- | --- |
| `DirectionReticuleRenderer` | Arrow at the cursor | Things fired in a direction |
| `AreaReticuleRenderer(size)` | Circle at the cursor, sized to the effect | Things that land on a spot |
| `ValidatedAreaReticuleRenderer(size, validator)` | Same, but turns red and refuses to fire on rejected positions | Restricted targeting |

A `PositionValidator` is one method — `boolean isValid(Vector2f worldPos)`. `MarketProximityValidator`
ships with the framework and rejects aim points near inhabited worlds:

```java
return new ValidatedAreaReticuleRenderer(400f, new MarketProximityValidator(500f));
```

For anything else, subclass `BaseReticuleRenderer` and implement `renderCursorBoundObject(...)`. The
fleet ring, the cursor tracking, and the valid/invalid tint are handled for you; `cursorPos` holds
the current aim point and is safe to read from `isValidPosition()`.

## Configuration

`SkillshotSettings` holds every sprite id, sound id, size and colour, plus the ability tag name.
All fields are writable — rewrite them once during startup to point at your own assets:

```java
SkillshotSettings.SPRITE_DIRECTION_ARROW = "mymod_arrow";
SkillshotSettings.LOG_DEBUG = true; // state transitions to starsector.log
```

## How it works

Two input paths lead to the same place.

**Hotkey** — `OnKeyPressSkillshotListener` is a single long-lived `CampaignInputListener` at priority
0. It intercepts the 1-9 keys before the UI can turn them into a button press (a button press would
fire the ability instantly, leaving nothing to aim), checks the slotted ability for the skillshot tag,
and opens a session. Releasing the same key fires.

**Click** — `BaseSkillshotAbility.pressButton()` starts a per-session `OnClickSkillshotListener`.
Registration is deferred by one frame via `DelayedActionScriptRunWhilePaused`, because the UI outranks
priority-0 listeners and the new listener would otherwise eat the very click that created it. The next
left click on the map fires.

`SkillshotActivationManager` is an `EveryFrameScript` (running while paused) that allows exactly one
live session and clears it when it goes inactive — that's what makes `isTargetingBlocked()` work
across abilities.

Either path cancels cleanly on an opened dialog, on ctrl (the vanilla slot-reassign modifier), or on
any stray keypress. Firing at a position the reticule rejects plays `skillshot_denied` instead.

## Differences from the IndEvo original

- Reticule comes from `SkillshotAbility.createReticule()` instead of being picked from `aoe` /
  `artillery` tags inside the listeners. Attachment is still by tag; only the sprite choice moved.
- The artillery no-fire-near-markets rule became `PositionValidator` / `MarketProximityValidator`.
- `isValidPosition()` now sees the current frame's cursor position. In the original the aim point was
  written after validation ran, so validation lagged a frame.
- The hotkey path resolves the slotted ability the same way on key-down and key-up; the original used
  the hyperspace-aware id on the way down and the normal one on the way up, which could fire the
  wrong ability in hyperspace.
- Consumable-item plumbing (cargo counts, item removal, fleet inventory) is gone, replaced by the
  `onConsume()` hook.
