package industrial_evolution.items.consumables.entityAbilities;

import com.fs.starfarer.api.Global;
import industrial_evolution.artillery.entities.IndEvo_ArtilleryStationEntityPlugin;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.ai.FleetAIFlags;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.abilities.BaseDurationAbility;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Pings;
import com.fs.starfarer.api.loading.CampaignPingSpec;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class DecoyMineAbility extends BaseDurationAbility {

    public static final float BASE_RANGE = 3000f;
    public static final float BASE_SEARCH_DAYS = 5f;
    public static final float IGNORE_CHANCE = 0.3f;
    public static final float ARTILLERY_STATION_TIMEOUT = 20f;

    @Override
    protected void activateImpl() {
        entity.setFaction(Factions.PLAYER);
        Global.getSector().addPing(entity, Pings.DISTRESS_CALL);
        primed = true;
    }

    @Override
    protected void applyEffect(float amount, float level) {
        if (level > 0 && level < 1 && amount > 0) {
            showRangePing(amount);
            return;
        }

        if (level == 1 && primed != null) {
            for(SectorEntityToken t : IndEvo_ArtilleryStationEntityPlugin.getArtilleriesInLoc(entity.getContainingLocation())){
                IndEvo_ArtilleryStationEntityPlugin p = (IndEvo_ArtilleryStationEntityPlugin) t.getCustomPlugin();
                p.forceTarget(entity, ARTILLERY_STATION_TIMEOUT);
            }

            for (CampaignFleetAPI other : entity.getContainingLocation().getFleets()) {
                if (other == entity || other.isPlayerFleet()) continue;
                if (other.isInHyperspaceTransition()) continue;

                float dist = Misc.getDistance(entity.getLocation(), other.getLocation());
                if (dist > BASE_RANGE) continue;

                SectorEntityToken.VisibilityLevel vis = other.getVisibilityLevelToPlayerFleet();

                MemoryAPI mem = other.getMemoryWithoutUpdate();
                boolean patrol = mem.getBoolean(MemFlags.MEMORY_KEY_PATROL_FLEET);
                boolean warFleet = mem.getBoolean(MemFlags.MEMORY_KEY_WAR_FLEET);
                boolean pirate = mem.getBoolean(MemFlags.MEMORY_KEY_PIRATE);
                boolean chance = Math.random() > IGNORE_CHANCE;
                boolean validTarget = chance && !patrol && !warFleet && !pirate && !Misc.isBusy(other);

                // the triggerIntercept method sets this as well so it should be fine? Have to check if it unsets at some point
                if(!validTarget){
                    if (vis == SectorEntityToken.VisibilityLevel.COMPOSITION_AND_FACTION_DETAILS ||
                            vis == SectorEntityToken.VisibilityLevel.COMPOSITION_DETAILS) {
                        other.addFloatingText("Ignoring signal" , Misc.getGrayColor(), 1f, true);
                    }
                } else {
                    float radius = 700f;
                    Vector2f approximatePlayerLoc = Misc.getPointAtRadius(entity.getLocation(), radius);
                    float days = Math.round(BASE_SEARCH_DAYS + (BASE_SEARCH_DAYS * Math.random()));
                    other.getMemoryWithoutUpdate().set(FleetAIFlags.PLACE_TO_LOOK_FOR_TARGET, approximatePlayerLoc, days);

                    other.addAssignmentAtStart(FleetAssignment.INTERCEPT, null, days, "Searching for cause of signal", null);
                    other.addAssignmentAtStart(FleetAssignment.GO_TO_LOCATION, entity.getContainingLocation().createToken(approximatePlayerLoc), days, "Investigating Signal", null);

                    if (vis == SectorEntityToken.VisibilityLevel.COMPOSITION_AND_FACTION_DETAILS ||
                            vis == SectorEntityToken.VisibilityLevel.COMPOSITION_DETAILS) {

                        other.addScript(new InterdictionMineAbility.GoSlowScript(other));
                        other.addFloatingText("Investigating signal" , getColor(), 1f, true);
                        //Global.getSoundPlayer().playSound("world_interdict_hit", 1f, 1f, other.getLocation(), other.getVelocity());
                    }
                }
            }

            primed = null;
            elapsed = null;
            numFired = null;

            Global.getSoundPlayer().playSound("ui_intel_distress_call", 1f, 1f, entity.getLocation(), entity.getVelocity());
        }

    }


    public Color getColor(){
        return new Color(20, 160, 200, 255);
    }

    @Override
    protected void deactivateImpl() {
        cleanupImpl();
    }

    @Override
    protected void cleanupImpl() {
        Misc.fadeAndExpire(entity);
        primed = null;
    }

    @Override
    public boolean isUsable() {
        return super.isUsable() &&
                entity != null;
    }


    protected Boolean primed = null;
    protected Float elapsed = null;
    protected Integer numFired = null;

    protected void showRangePing(float amount) {
        //SectorEntityToken.VisibilityLevel vis = entity.getVisibilityLevelToPlayerFleet();
        //if (vis == SectorEntityToken.VisibilityLevel.NONE || vis == SectorEntityToken.VisibilityLevel.SENSOR_CONTACT) return;

        if(!entity.isInCurrentLocation()) return;

        boolean fire = false;
        if (elapsed == null) {
            elapsed = 0f;
            numFired = 0;
            fire = true;
        }
        elapsed += amount;
        if (elapsed > 0.2f && numFired < 4) {
            elapsed -= 0.2f;
            fire = true;
        }

        if (fire) {
            numFired++;

            CampaignPingSpec custom = new CampaignPingSpec();
            custom.setUseFactionColor(true);
            custom.setWidth(7);
            //custom.setMinRange(BASE_RANGE - 100f);
            custom.setRange(BASE_RANGE);
            custom.setDuration(10f);
            custom.setAlphaMult(0.25f);
            custom.setInFraction(0.2f);
            custom.setNum(1);

            Global.getSector().addPing(entity, custom);
        }

    }
}
