package com.fs.starfarer.api.artilleryStation.station;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.ids.IndEvo_ids;
import com.fs.starfarer.api.plugins.IndEvo_modPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.List;

public class IndEvo_WatchtowerEyeIndicator extends BaseCampaignEventListener implements CustomCampaignEntityPlugin {

    public static final String WAS_SEEN_BY_HOSTILE_ENTITY = "$IndEvo_WasSeenByOtherEntity";
    public static final float BASE_NPC_KNOWN_DURATION = 5f;
    public static final float MAX_TIME_TO_TARGET_LOCK = 20f; //10s per day
    public static final float DISTANCE_MOD = 2; //max mult for distance

    protected SectorEntityToken entity;
    private float elapsed = 0f;
    private boolean isLocked = false;
    public transient SpriteAPI sprite;

    public IntervalUtil checkInterval = new IntervalUtil(0.5f, 0.5f);

    public IndEvo_WatchtowerEyeIndicator() {
        super(false);
    }

    public static void register() {
        LocationAPI loc = Global.getSector().getPlayerFleet().getContainingLocation();
        if (loc == null) loc = Global.getSector().getStarSystems().get(0);

        if (loc.getEntitiesWithTag("IndEvo_eye").isEmpty()) {
            SectorEntityToken t = loc.addCustomEntity(Misc.genUID(), "", "IndEvo_Eye", null, null);
            Global.getSector().addListener((CampaignEventListener) t.getCustomPlugin());
        }
    }

    //check where watchtowers are every frame
    //if in range to non-hacked watchtower of same faction as any in-system artillery, display eye
    //increment suspicion level with distance mod
    //when at max, apply "seen" tag every frame
    //once out of range, decay after a delay (3 days)
    //change colour with level (white -> yellow -> orange -> red)

    public void init(SectorEntityToken entity, Object pluginParams) {
        this.entity = entity;
        readResolve();
    }

    public void reset() {
        elapsed = 0f;
        isLocked = false;
        Global.getSector().getPlayerFleet().getMemoryWithoutUpdate().unset(WAS_SEEN_BY_HOSTILE_ENTITY);
    }

    @Override
    public void reportFleetJumped(CampaignFleetAPI fleet, SectorEntityToken from, JumpPointAPI.JumpDestination to) {
        fleet.getMemoryWithoutUpdate().unset(WAS_SEEN_BY_HOSTILE_ENTITY);

        if (fleet.isPlayerFleet()) {
            elapsed = 0f;
            isLocked = false;
        }
    }

    public void advance(float amount) {
        checkInterval.advance(amount);

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();

        if (!entity.isInCurrentLocation()) {
            entity.getContainingLocation().removeEntity(entity);
            player.getContainingLocation().addEntity(entity);
            entity.setLocation(0, 0);
        }

        if (!checkInterval.intervalElapsed()) return;
        if (player.isInHyperspace()) return;

        amount += checkInterval.getIntervalDuration();
        LocationAPI loc = entity.getContainingLocation();
        if (loc.getEntitiesWithTag(IndEvo_ids.TAG_ARTILLERY_STATION).isEmpty()) return;

        cycleActions();

        boolean inFleetRange = false;
        for (CampaignFleetAPI f : loc.getFleets()) {
            //check if visible to other fleet
            for (CampaignFleetAPI otherFLeet : loc.getFleets()) {
                if (otherFLeet == f) continue;

                if (otherFLeet.isHostileTo(f)
                        && otherFLeet.getAI() != null
                        && !otherFLeet.isStationMode()
                        && Misc.getVisibleFleets(otherFLeet, false).contains(f)) {

                    if (f.isPlayerFleet()) {
                        elapsed = MAX_TIME_TO_TARGET_LOCK;
                        isLocked = true;
                        inFleetRange = true;

                        IndEvo_modPlugin.log("in fleet range, is seen");

                    } else f.getMemoryWithoutUpdate().set(WAS_SEEN_BY_HOSTILE_ENTITY, true, BASE_NPC_KNOWN_DURATION);
                    break;
                }
            }
        }

        List<SectorEntityToken> watchtowerList = loc.getEntitiesWithTag(IndEvo_ids.TAG_WATCHTOWER);
        if (watchtowerList.isEmpty()) return;

        float closestDist = Float.MIN_VALUE;
        SectorEntityToken closestTower = null;

        for (SectorEntityToken t : watchtowerList) {
            IndEvo_WatchtowerEntityPlugin p = (IndEvo_WatchtowerEntityPlugin) t.getCustomPlugin();

            float dist = Misc.getDistance(t, player);
            if (!p.isHacked() && p.isHostileTo(player) && dist < IndEvo_WatchtowerEntityPlugin.RANGE) {
                if (dist > closestDist) {
                    closestDist = dist;
                    closestTower = t;
                }
            }
        }

        float addition = 0f;

        if (closestTower == null && !inFleetRange) { //null means we are out of range of any tower
            addition = -amount;

        } else { //at this point we are in range because there is a closest hostile tower
            float distanceFraction = 1 - closestDist / IndEvo_WatchtowerEntityPlugin.RANGE;
            float mult = 1 + (DISTANCE_MOD - 1) * distanceFraction;
            addition = amount * mult;
        }

        elapsed = MathUtils.clamp(elapsed += addition, 0, MAX_TIME_TO_TARGET_LOCK);
    }

    private void cycleActions() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();

        if (elapsed == 0f) {
            isLocked = false; //if it's locked we stay locked until the level is 0
            player.getMemoryWithoutUpdate().unset(WAS_SEEN_BY_HOSTILE_ENTITY);
        } else if (elapsed == MAX_TIME_TO_TARGET_LOCK) {
            isLocked = true;
            player.getMemoryWithoutUpdate().set(WAS_SEEN_BY_HOSTILE_ENTITY, true, 0.5f);
        }

        IndEvo_modPlugin.log("Watchtower reporting " + elapsed + " locked " + isLocked);
    }

    Object readResolve() {
        sprite = Global.getSettings().getSprite("fx", "IndEvo_eye_3");
        return this;
    }

    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (elapsed == 0f) return;

        float level = elapsed / MAX_TIME_TO_TARGET_LOCK;

        Color color = Color.RED;
        sprite = Global.getSettings().getSprite("fx", "IndEvo_eye_3");

        if (level < 0.33f) {
            sprite = Global.getSettings().getSprite("fx", "IndEvo_eye_1");
            if (!isLocked) color = new Color(255, 255, 150, 255);
        } else if (level < 0.66f) {
            sprite = Global.getSettings().getSprite("fx", "IndEvo_eye_2");
            if (!isLocked) color = new Color(255, 200, 50, 255);
        } else if (!isLocked) color = new Color(255, 130, 50, 255);

        sprite.setAdditiveBlend();
        sprite.setAlphaMult(0.7f);
        sprite.setColor(color);

        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        Vector2f loc = fleet.getLocation();

        //min zoom 0.5f
        //max zoom 3.0f
        float zoom = Global.getSector().getCampaignUI().getZoomFactor();
        float size = 10 * zoom;
        sprite.setSize(size * 2, size);

        sprite.renderAtCenter(loc.x, loc.y + fleet.getRadius() + size + 10f);

    }

    public float getRenderRange() {
        return 9999999999999f;
    }

    public boolean hasCustomMapTooltip() {
        return false;
    }

    public float getMapTooltipWidth() {
        return 300f;
    }

    public boolean isMapTooltipExpandable() {
        return false;
    }

    public void createMapTooltip(TooltipMakerAPI tooltip, boolean expanded) {

    }

    public void appendToCampaignTooltip(TooltipMakerAPI tooltip, SectorEntityToken.VisibilityLevel level) {

    }
}
