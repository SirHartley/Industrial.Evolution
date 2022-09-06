package com.fs.starfarer.api.artilleryStation.station;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.artilleryStation.IndEvo_FleetVisibilityManager;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.impl.campaign.MilitaryResponseScript;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.IndEvo_ids;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.loading.CampaignPingSpec;
import com.fs.starfarer.api.util.Misc;

import static com.fs.starfarer.api.artilleryStation.IndEvo_FleetVisibilityManager.WAS_SEEN_BY_HOSTILE_ENTITY;

public class IndEvo_WatchtowerEntityPlugin extends BaseCustomEntityPlugin {
    //this flags any fleets around it as seen

    // TODO: 22.08.2022 should always have some kinda defender fleet according to the controlling faction
    //can be disabled for a month

    public static final float RANGE = 2000f;
    public static float PINGS_PER_SECOND = 0.1f;
    public static float WATCHTOWER_FLEET_SEEN_DURATION = 30f;

    protected float phase = 0f;

    public static SectorEntityToken spawn(SectorEntityToken primaryEntity, FactionAPI faction){

        if (faction == null) faction = Global.getSector().getFaction(IndEvo_ids.DERELICT);
        SectorEntityToken t = primaryEntity.getContainingLocation().addCustomEntity(Misc.genUID(), faction.getDisplayName() + " Watchtower", "IndEvo_Watchtower",faction.getId(),null);

        float orbitRadius = primaryEntity.getRadius() + 250f;
        t.setCircularOrbitWithSpin(primaryEntity, (float) Math.random() * 360f, orbitRadius, orbitRadius / 10f, 5f, 5f);

        return t;
    }

    public void advance(float amount) {
        super.advance(amount);

        phase += amount * PINGS_PER_SECOND;
        if(phase >= 1) {
            showRangePing();

            for (CampaignFleetAPI f : Misc.getNearbyFleets(entity, RANGE))  {
                if (isHostileTo(f)){
                    boolean showMessage = !f.getMemoryWithoutUpdate().getBoolean(WAS_SEEN_BY_HOSTILE_ENTITY);
                    f.getMemoryWithoutUpdate().set(WAS_SEEN_BY_HOSTILE_ENTITY, true, WATCHTOWER_FLEET_SEEN_DURATION);

                    if(showMessage) f.addFloatingText("Detected by Watchtower", Misc.getNegativeHighlightColor(), 1f);
                }
            }

            phase--;
        }
    }

    public boolean isHostileTo(CampaignFleetAPI target) {
        FactionAPI faction = entity.getFaction();
        FactionAPI targetFaction = target.getFaction();

        if (faction == null || faction.getId().equals(IndEvo_ids.DERELICT)) return true;
        else return faction.isHostileTo(targetFaction);
    }

    protected void showRangePing() {
        SectorEntityToken.VisibilityLevel vis = entity.getVisibilityLevelToPlayerFleet();
        if (vis == SectorEntityToken.VisibilityLevel.NONE || vis == SectorEntityToken.VisibilityLevel.SENSOR_CONTACT) return;

        FactionAPI f = entity.getFaction();
        if (!f.isHostileTo(Factions.PLAYER)) return;

        CampaignPingSpec custom = new CampaignPingSpec();
        custom.setColor(f.getColor());
        custom.setWidth(7);
        custom.setMinRange(RANGE - 100f);
        custom.setRange(RANGE);
        custom.setDuration(2f);
        custom.setAlphaMult(0.25f);
        custom.setInFraction(0.2f);
        custom.setNum(1);

        Global.getSector().addPing(entity, custom);
    }
}
