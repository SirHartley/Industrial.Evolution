package com.fs.starfarer.api.artilleryStation.station;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.impl.campaign.BaseCampaignObjectivePlugin;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.IndEvo_ids;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.loading.CampaignPingSpec;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import static com.fs.starfarer.api.artilleryStation.IndEvo_FleetVisibilityManager.WAS_SEEN_BY_HOSTILE_ENTITY;

public class IndEvo_WatchtowerEntityPlugin extends BaseCampaignObjectivePlugin {
    //this flags any fleets around it as seen

    // TODO: 22.08.2022 should always have some kinda defender fleet according to the controlling faction
    //can be disabled for a month

    public static final float RANGE = 3000f;
    public static float PINGS_PER_SECOND = 0.05f;
    public static float WATCHTOWER_FLEET_SEEN_DURATION_SECONDS = 30f;
    public static final float HACK_DURATION_DAYS_WT = 30f;

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
            if(!isHacked()) showRangePing();

            for (CampaignFleetAPI f : Misc.getNearbyFleets(entity, RANGE))  {
                if (isHostileTo(f)){

                    if (isHacked() && f.isPlayerFleet()) continue;

                    boolean showMessage = !f.getMemoryWithoutUpdate().getBoolean(WAS_SEEN_BY_HOSTILE_ENTITY);
                    f.getMemoryWithoutUpdate().set(WAS_SEEN_BY_HOSTILE_ENTITY, true, WATCHTOWER_FLEET_SEEN_DURATION_SECONDS);

                    if(showMessage) f.addFloatingText("Detected by Watchtower", Misc.getNegativeHighlightColor(), 1f);
                }
            }

            phase--;
        }
    }

    public boolean isHostileTo(CampaignFleetAPI target) {
        FactionAPI faction = entity.getFaction();
        FactionAPI targetFaction = target.getFaction();

        if (faction == null) return true;
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
        custom.setDuration(3f);
        custom.setAlphaMult(0.25f);
        custom.setInFraction(0.2f);
        custom.setNum(1);

        Global.getSector().addPing(entity, custom);
    }

    public void setHacked(boolean hacked) {
        setHacked(hacked, HACK_DURATION_DAYS_WT + (float) Math.random() * 0.5f * HACK_DURATION_DAYS_WT);
    }

    public void printEffect(TooltipMakerAPI text, float pad) {
        text.addPara(BaseIntelPlugin.INDENT + "Transmits target telemetry within %s range",
                pad, Misc.getHighlightColor(), Math.round(RANGE) + " su");

        if (isReset()) {
            text.addPara(BaseIntelPlugin.INDENT + "Auto-calibrating after factory reset; non-functional", 3f);
        }
    }

    public void printNonFunctionalAndHackDescription(TextPanelAPI text) {
        if (entity.getMemoryWithoutUpdate().getBoolean(MemFlags.OBJECTIVE_NON_FUNCTIONAL)) {
            text.addPara("This one, however, does not appear to be transmitting a target telemetry. The cause of its lack of function is unknown.");
        }
        if (isHacked()) {
            text.addPara("You have a hack running on this watchtower.");
        }
    }

    @Override
    public void addHackStatusToTooltip(TooltipMakerAPI text, float pad) {
        text.addPara("%s your fleet",
                pad, Misc.getHighlightColor(), "Ignores");

        super.addHackStatusToTooltip(text, pad);
    }
}
