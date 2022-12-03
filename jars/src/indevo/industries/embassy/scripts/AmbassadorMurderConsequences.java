package indevo.industries.embassy.scripts;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.fleets.AutoDespawnScript;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import indevo.utils.helper.StringHelper;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.ai.CampaignFleetAIAPI.EncounterOption;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.FullName.Gender;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.DebugFlags;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.util.Random;

import static indevo.exploration.salvage.specials.CreditStashSpecial.makeFleetInterceptPlayer;

public class AmbassadorMurderConsequences implements EveryFrameScript, FleetEventListener {

    public static final float RADIUS_FROM_CORE = 30000f; // may send fleet when within this radius from core
    public static final float DAYS_IN_SYSTEM = 7f;

    protected float delayDays;
    protected boolean sentFleet;
    protected final String name;
    protected final Gender gender;
    protected final FactionAPI faction;
    protected final long seed;

    public AmbassadorMurderConsequences(PersonAPI ambassador) {
        name = ambassador.getNameString();
        gender = ambassador.getGender();
        faction = ambassador.getFaction();

        seed = Misc.genRandomSeed();

        if (Global.getSettings().isDevMode()) delayDays = 1;

        if (DebugFlags.BAR_DEBUG || Global.getSettings().isDevMode()) {
            delayDays = 0f;
        }
    }

    protected StarSystemAPI systemPlayerIsIn = null;
    protected float daysInSystem = 0f;

    public void advance(float amount) {
        if (sentFleet) return;

        float days = Misc.getDays(amount);
        //days *= 1000f;
        delayDays -= days;
        if (delayDays > 0) return;


        final CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        float distFromCore = playerFleet.getLocationInHyperspace().length();
        if (distFromCore > RADIUS_FROM_CORE) {
            daysInSystem = 0f;
            systemPlayerIsIn = null;
            return;
        }

        if (!(playerFleet.getContainingLocation() instanceof StarSystemAPI)) {
            if ((daysInSystem > DAYS_IN_SYSTEM || DebugFlags.BAR_DEBUG) && systemPlayerIsIn != null) {
                float dist = Misc.getDistance(systemPlayerIsIn.getLocation(), playerFleet.getLocationInHyperspace());
                if (dist < 3000f) {
                    sendFleet();
                }
            }
            daysInSystem = 0f;
            systemPlayerIsIn = null;
            return;
        }

        systemPlayerIsIn = (StarSystemAPI) playerFleet.getContainingLocation();
        daysInSystem += days;
    }

    protected void sendFleet() {
        if (sentFleet) return;
        sentFleet = true;
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        CampaignFleetAPI hunter = createBountyHunter();
        if (hunter != null) {
            Global.getSector().getHyperspace().addEntity(hunter);
            Vector2f hunterLoc = Misc.getPointAtRadius(playerFleet.getLocationInHyperspace(), 500f);
            hunter.setLocation(hunterLoc.x, hunterLoc.y);
            hunter.getAI().addAssignmentAtStart(FleetAssignment.INTERCEPT, playerFleet, 1000f, null);
            Misc.giveStandardReturnToSourceAssignments(hunter, false);
        }

    }

    protected CampaignFleetAPI createBountyHunter() {
        Random random = new Random(seed);

        float pts = Global.getSector().getPlayerFleet().getFleetPoints();
        pts *= 1.2f;
        if (pts < 30) pts = 30;
        float qMod = 0.4f + Misc.getFactionMarkets(faction).size() * 0.1f; //the larger the faction, the more hurt

        FleetParamsV3 params = new FleetParamsV3(
                null,
                Global.getSector().getPlayerFleet().getLocationInHyperspace(),
                faction.getId(),
                null,
                FleetTypes.MERC_BOUNTY_HUNTER,
                pts, // combatPts
                0f, // freighterPts
                pts * 0.1f, // tankerPts
                0f, // transportPts
                0f, // linerPts
                0f, // utilityPts
                qMod // qualityMod
        );

        params.random = random;

        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        if (fleet.isEmpty()) fleet = null;

        EncounterOption option = fleet.getAI().pickEncounterOption(null, Global.getSector().getPlayerFleet());
        if (option == EncounterOption.DISENGAGE) {
            fleet = null;
        }

        if (fleet != null) {
            //fleet.setFaction(Factions.INDEPENDENT, true);
            Misc.makeLowRepImpact(fleet, "IndEvo");

            fleet.addScript(new AutoDespawnScript(fleet));
            fleet.addEventListener(this);

            MemoryAPI memory = fleet.getMemoryWithoutUpdate();
            String himOrHer = StringHelper.getHimOrHer(gender);

            memory.set("$IndEvo_bountyHunter", true);
            memory.set("$IndEvo_himOrHer", himOrHer);
            memory.set("$IndEvo_name", name);
            memory.set("$IndEvo_theFaction", Misc.ucFirst(this.faction.getDisplayNameWithArticle()));

            makeFleetInterceptPlayer(fleet, true, true, true, 31f);
        }

        return fleet;
    }


    public boolean isDone() {
        return sentFleet;
    }

    public boolean runWhilePaused() {
        return false;
    }

    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {
        MemoryAPI memory = fleet.getMemoryWithoutUpdate();
        memory.unset(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE);
    }

    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, FleetDespawnReason reason, Object param) {

    }

}
