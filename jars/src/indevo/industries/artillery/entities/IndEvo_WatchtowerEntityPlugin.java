package indevo.industries.artillery.entities;

import com.fs.starfarer.api.Global;
import indevo.industries.artillery.scripts.IndEvo_EyeIndicatorScript;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.BaseCampaignObjectivePlugin;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import indevo.ids.IndEvo_ids;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.procgen.themes.MiscellaneousThemeGenerator;
import com.fs.starfarer.api.loading.CampaignPingSpec;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;

import static indevo.industries.artillery.scripts.IndEvo_EyeIndicatorScript.WAS_SEEN_BY_HOSTILE_ENTITY;

public class IndEvo_WatchtowerEntityPlugin extends BaseCampaignObjectivePlugin {
    //this flags any fleets around it as seen

    /*
    watchtower faction is irrellevant atm, make it care about which faction saw you
    make watchtower sensor profile in line with vanilla

    ok no fuck that
    you go do that future me
    */

    public static final float RANGE = Global.getSettings().getFloat("IndEvo_Artillery_WatchtowerRange");
    public static final String MEM_SENSOR_LOCK_ACTIVE = "$IndEvo_isArtilleryActive";
    public static float PINGS_PER_SECOND = 0.05f;
    public static float WATCHTOWER_FLEET_SEEN_DURATION_DAYS = 5f;
    public static final float HACK_DURATION_DAYS_WT = 30f;

    protected float phase = 0f;

    public static SectorEntityToken spawn(SectorEntityToken primaryEntity, FactionAPI faction){

        if (faction == null) faction = Global.getSector().getFaction(IndEvo_ids.DERELICT_FACTION_ID);
        SectorEntityToken t = primaryEntity.getContainingLocation().addCustomEntity(Misc.genUID(), "Watchtower", "IndEvo_Watchtower",faction.getId(),null);

        float orbitRadius = primaryEntity.getRadius() + 250f;
        t.setCircularOrbitWithSpin(primaryEntity, (float) Math.random() * 360f, orbitRadius, orbitRadius / 10f, 5f, 5f);
        if (Misc.getMarketsInLocation(primaryEntity.getContainingLocation()).isEmpty()) MiscellaneousThemeGenerator.makeDiscoverable(t, 200f, 2000f);

        return t;
    }

    @Override
    public void init(SectorEntityToken entity, Object pluginParams) {
        super.init(entity, pluginParams);

        checkSensorLockActive();
        /*entity.setSensorStrength(RANGE);
        entity.getMemoryWithoutUpdate().set(MemFlags.SENSOR_INDICATORS_OVERRIDE, 2);
        entity.getMemoryWithoutUpdate().set(MemFlags.EXTRA_SENSOR_INDICATORS, 2);*/
    }

    //render eye above fleet
    //slowly opens while in range
    //faster the closer you are to watchtower
    //sensor range depending on hidden fleet
    //when going out of range, slowly close eye
    //when eye completely open, render circle + fading eye silhouette pulse to denote having been seen
    //last for 5 days
    //when out of watchtower range +5d slowly close eye

    public boolean checkSensorLockActive(){
        if(entity.getContainingLocation() == null) return false;

        MemoryAPI mem = entity.getMemoryWithoutUpdate();

        for (SectorEntityToken t : entity.getContainingLocation().getEntitiesWithTag(IndEvo_ids.TAG_ARTILLERY_STATION)){
            String faction = t.getFaction().getId();

            if (IndEvo_ids.DERELICT_FACTION_ID.equals(faction) || Factions.REMNANTS.equals(faction)) {
                mem.set(MEM_SENSOR_LOCK_ACTIVE, true);
                return true;
            }
        }

        return false;
    }

    public void advance(float amount) {
        super.advance(amount);
        if (!entity.isInCurrentLocation()) return;

        phase += amount * PINGS_PER_SECOND;

        setFunctional(!entity.getContainingLocation().getMemoryWithoutUpdate().getBoolean(IndEvo_ids.MEM_SYSTEM_DISABLE_WATCHTOWERS));

        // TODO: 19/10/2022 change this to an interval instead of this janky shit
        if(phase >= 1 * MathUtils.getRandomNumberInRange(1, 1.1f)) {
            String factionID = entity.getFaction().getId();
            boolean isAI = factionID.equals(IndEvo_ids.DERELICT_FACTION_ID) || factionID.equals(Factions.REMNANTS);
            boolean isLocked = checkSensorLockActive();

            if(isAI){
                if (isLocked && !isHacked() && isFunctional()) showRangePing();
            } else if(!isHacked() && isFunctional()) showRangePing();

            if(isFunctional()) for (CampaignFleetAPI f : Misc.getNearbyFleets(entity, RANGE))  {
                if (isHostileTo(f)){ //&& f.getVisibilityLevelTo(entity) == SectorEntityToken.VisibilityLevel.SENSOR_CONTACT){
                    if (f.isPlayerFleet()) continue;

                    boolean showMessage = !f.getMemoryWithoutUpdate().getBoolean(WAS_SEEN_BY_HOSTILE_ENTITY);
                    f.getMemoryWithoutUpdate().set(WAS_SEEN_BY_HOSTILE_ENTITY, true, WATCHTOWER_FLEET_SEEN_DURATION_DAYS);

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
        if (!f.isHostileTo(Factions.PLAYER) || isHacked()) return;

        float range = RANGE / 2f;

        CampaignPingSpec custom = new CampaignPingSpec();
        custom.setColor(f.getColor());
        custom.setWidth(7);
        custom.setMinRange(range - 100f);
        custom.setRange(range);
        custom.setDuration(4f);
        custom.setAlphaMult(0.25f);
        custom.setInFraction(0.2f);
        custom.setNum(3);
        custom.setDelay(0.25f);

        Global.getSector().addPing(entity, custom);
    }

    public void setHacked(boolean hacked) {
        setHacked(hacked, HACK_DURATION_DAYS_WT + (float) Math.random() * HACK_DURATION_DAYS_WT);
        IndEvo_EyeIndicatorScript.getInstance().reset();
    }

    public void printEffect(TooltipMakerAPI text, float pad) {
        if(isHacked()) {
            text.addPara(BaseIntelPlugin.INDENT + "%s, ignoring your fleet",
                    pad, Misc.getHighlightColor(), "Hacked");

        } else text.addPara(BaseIntelPlugin.INDENT + "Transmits target telemetry within %s range",
                pad, Misc.getHighlightColor(), Math.round(RANGE) + " su");

        if (!isFunctional() || isReset()) {
            text.addPara(BaseIntelPlugin.INDENT + "Not functional for unknown reasons", 3f);
        }
    }

    public boolean isFunctional(){
        return entity.getMemoryWithoutUpdate().getBoolean(MemFlags.OBJECTIVE_NON_FUNCTIONAL);
    }

    public void setFunctional(boolean functional){
        entity.getMemoryWithoutUpdate().set(MemFlags.OBJECTIVE_NON_FUNCTIONAL, functional);
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
        if(isHacked()) text.addPara("%s your fleet",
                pad, Misc.getHighlightColor(), "Ignores");
        else if(entity.isInCurrentLocation() && Misc.getDistance(entity, Global.getSector().getPlayerFleet()) < RANGE) text.addPara("%s your fleet",
                pad, Misc.getHighlightColor(), "Tracking");
        else text.addPara("Out of range", pad);

        super.addHackStatusToTooltip(text, pad);
    }
}
