package indevo.industries.artillery.entities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.BaseCampaignObjectivePlugin;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.procgen.themes.MiscellaneousThemeGenerator;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.ids.Ids;
import indevo.industries.artillery.scripts.EyeIndicatorScript;
import indevo.industries.artillery.utils.ArtilleryStationPlacer;
import org.lazywizard.lazylib.MathUtils;

import static indevo.industries.artillery.scripts.EyeIndicatorScript.WAS_SEEN_BY_HOSTILE_ENTITY;

public class WatchtowerEntityPlugin extends BaseCampaignObjectivePlugin {
    //this flags any fleets around it as seen

    /*
    watchtower faction is irrellevant atm, make it care about which faction saw you
    make watchtower sensor profile in line with vanilla

    ok no fuck that
    you go do that future me
    */

    public static final String ENTITY_ID = "IndEvo_Watchtower";
    public static final String MEM_SENSOR_LOCK_ACTIVE = "$IndEvo_isArtilleryActive";
    public static float PINGS_PER_SECOND = 0.05f;
    public static float WATCHTOWER_FLEET_SEEN_DURATION_DAYS = 5f;
    public static final float HACK_DURATION_DAYS_WT = 30f;

    protected float phase = 0f;

    public static SectorEntityToken spawn(SectorEntityToken primaryEntity, FactionAPI faction) {
        return spawn(primaryEntity, faction, ENTITY_ID);
    }

    public static SectorEntityToken spawn(SectorEntityToken primaryEntity, FactionAPI faction, String entityType) {

        if (faction == null) faction = Global.getSector().getFaction(Ids.DERELICT_FACTION_ID);
        SectorEntityToken t = primaryEntity.getContainingLocation().addCustomEntity(Misc.genUID(), null, entityType, faction.getId(), null);

        float orbitRadius = primaryEntity.getRadius() + 250f;
        t.setCircularOrbitWithSpin(primaryEntity, (float) Math.random() * 360f, orbitRadius, orbitRadius / 10f, 5f, 5f);
        if (Misc.getMarketsInLocation(primaryEntity.getContainingLocation()).isEmpty())
            MiscellaneousThemeGenerator.makeDiscoverable(t, 200f, 2000f);

        return t;
    }

    @Override
    public void init(SectorEntityToken entity, Object pluginParams) {
        super.init(entity, pluginParams);

        checkSensorLockActive();
        /*entity.setSensorStrength(Setting.ARTILLERY_WATCHTOWER_RANGE.value());
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

    public boolean checkSensorLockActive() {
        if (entity.getContainingLocation() == null) return false;

        MemoryAPI mem = entity.getMemoryWithoutUpdate();

        for (SectorEntityToken t : entity.getContainingLocation().getEntitiesWithTag(Ids.TAG_ARTILLERY_STATION)) {
            String faction = t.getFaction().getId();

            if (Ids.DERELICT_FACTION_ID.equals(faction) || Factions.REMNANTS.equals(faction)) {
                mem.set(MEM_SENSOR_LOCK_ACTIVE, true);
                return true;
            }
        }

        return false;
    }

    public void advance(float amount) {
        super.advance(amount);
        if (!entity.isInCurrentLocation()) return;

        setFunctional(!entity.getContainingLocation().getMemoryWithoutUpdate().getBoolean(Ids.MEM_SYSTEM_DISABLE_WATCHTOWERS));
        advanceVision(amount);
    }

    protected void advanceVision(float amount) {
        phase += amount * PINGS_PER_SECOND;

        // TODO: 19/10/2022 change this to an interval instead of this janky shit
        if (phase >= 1 * MathUtils.getRandomNumberInRange(1, 1.1f)) {
            if (isFunctional()) for (CampaignFleetAPI f : Misc.getNearbyFleets(entity, Setting.ARTILLERY_WATCHTOWER_RANGE.value())) {
                if (isHostileTo(f)) { //&& f.getVisibilityLevelTo(entity) == SectorEntityToken.VisibilityLevel.SENSOR_CONTACT){
                    if (f.isPlayerFleet()) continue;
                    markSeen(f);
                }
            }

            phase--;
        }
    }

    // no single callback covers colonizing and taking a market, checked on the tower's vision tick
    protected boolean replacedForPlayerColony() {
        if (!entity.hasTag(Ids.TAG_WATCHTOWER)) return true;

        for (MarketAPI market : Misc.getMarketsInLocation(entity.getContainingLocation())) {
            if (!market.isPlayerOwned()) continue;
            ArtilleryStationPlacer.replaceWithWatchtower(entity);
            return true;
        }

        return false;
    }

    // lights of a lighthouse or searchlight tower are on: working, not hacked, hostile to the player, and an artillery needs the vision
    protected boolean shouldBeLit() {
        if (!isFunctional() || isHacked()) return false;
        if (!entity.getFaction().isHostileTo(Factions.PLAYER)) return false;

        return ArtilleryStationEntityPlugin.needsVisionAgainstPlayer(entity.getContainingLocation());
    }

    protected void markSeen(CampaignFleetAPI f) {
        boolean showMessage = !f.getMemoryWithoutUpdate().getBoolean(WAS_SEEN_BY_HOSTILE_ENTITY);
        f.getMemoryWithoutUpdate().set(WAS_SEEN_BY_HOSTILE_ENTITY, true, WATCHTOWER_FLEET_SEEN_DURATION_DAYS);

        if (showMessage) f.addFloatingText(getSeenText(), Misc.getNegativeHighlightColor(), 1f);
    }

    protected String getSeenText() {
        return "Detected by Watchtower";
    }

    public boolean isHostileTo(CampaignFleetAPI target) {
        FactionAPI faction = entity.getFaction();
        FactionAPI targetFaction = target.getFaction();

        if (faction == null) return true;
        else return faction.isHostileTo(targetFaction);
    }

    public void setHacked(boolean hacked) {
        setHacked(hacked, HACK_DURATION_DAYS_WT + (float) Math.random() * HACK_DURATION_DAYS_WT);
        // https://fractalsoftworks.com/forum/index.php?topic=18011.msg411734#msg411734
        if (EyeIndicatorScript.getInstance() != null)
            EyeIndicatorScript.getInstance().reset();
    }

    public void printEffect(TooltipMakerAPI text, float pad) {
        if (!isFunctional() || isReset()) {
            text.addPara(BaseIntelPlugin.INDENT + "Not functional for unknown reasons", 3f);
            return;
        }

        if (isHacked()) {
            text.addPara(BaseIntelPlugin.INDENT + "%s, ignoring your fleet",
                    pad, Misc.getHighlightColor(), "Hacked");

        } else text.addPara(BaseIntelPlugin.INDENT + "Transmits target telemetry within %s range",
                pad, Misc.getHighlightColor(), Math.round(Setting.ARTILLERY_WATCHTOWER_RANGE.value()) + " units");
    }

    public boolean isFunctional() {
        return !entity.getMemoryWithoutUpdate().getBoolean(MemFlags.OBJECTIVE_NON_FUNCTIONAL);
    }

    public void setFunctional(boolean functional) {
        entity.getMemoryWithoutUpdate().set(MemFlags.OBJECTIVE_NON_FUNCTIONAL, !functional);
    }

    public void printNonFunctionalAndHackDescription(TextPanelAPI text) {
        if (!isFunctional()) {
            text.addPara("This one, however, does not appear to be transmitting a target telemetry. The cause of its lack of function is unknown.");
        }
        if (isHacked()) {
            text.addPara("You have a hack running on this watchtower.");
        }
    }

    @Override
    public void addHackStatusToTooltip(TooltipMakerAPI text, float pad) {
        if (isHacked()) text.addPara("%s your fleet",
                pad, Misc.getHighlightColor(), "Ignores");
        else if (entity.isInCurrentLocation() && Misc.getDistance(entity, Global.getSector().getPlayerFleet()) < Setting.ARTILLERY_WATCHTOWER_RANGE.value())
            text.addPara("%s your fleet",
                    pad, Misc.getHighlightColor(), "Tracking");
        else text.addPara("Out of range", pad);

        super.addHackStatusToTooltip(text, pad);
    }
}
