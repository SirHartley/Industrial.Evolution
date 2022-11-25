package industrial_evolution.campaign.rulecmd.salvage.special;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.ai.FleetAIFlags;
import com.fs.starfarer.api.campaign.ai.ModularFleetAIAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageSpecialInteraction;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.BaseSalvageSpecial;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

public class IndEvo_CreditStashSpecial extends BaseSalvageSpecial {
/*
 A datachip with somebody's old bank account details ("You empty the old spacer's bank account, as well as 50 years of compound interest.")
        variation: someones current bank details, results in a visit from an unhappy fleet
        CreditStashSpecialCreator(random, 0.5f, FleetTypes.PATROL_SMALL, trapFactions, 4, 25), 10f);
*/

    public static final String OPEN = "open";
    public static final String NOT_NOW = "not_now";

    public static class CreditStashSpecialData implements SalvageSpecialInteraction.SalvageSpecialData {
        public float prob = 0.5f;
        public float creditAmt;

        public String nearbyFleetFaction = null;
        public Boolean useAllFleetsInRange = null;

        public FleetParamsV3 params;

        public final float minRange = 2500;
        public final float maxRange = 5000;

        public CreditStashSpecialData() {
        }

        public CreditStashSpecialData(float creditAmt, FleetParamsV3 params) {
            this.creditAmt = creditAmt;
            this.params = params;
        }

        public SalvageSpecialInteraction.SalvageSpecialPlugin createSpecialPlugin() {
            return new IndEvo_CreditStashSpecial();
        }
    }

    private IndEvo_CreditStashSpecial.CreditStashSpecialData data;

    public IndEvo_CreditStashSpecial() {
    }


    @Override
    public void init(InteractionDialogAPI dialog, Object specialData) {
        super.init(dialog, specialData);

        data = (IndEvo_CreditStashSpecial.CreditStashSpecialData) specialData;
        String s = entity instanceof PlanetAPI ? "facility" : "$shortName";

        addText("Your salvage crews discover a working console, giving you access to a depot of quite a tidy sum of credits.\n" +
                "Judging by the general state of disrepair the " + s + " is in, it is unlikely anyone will miss it.");

        options.clearOptions();
        options.addOption("Access the deposit", OPEN);
        options.addOption("Not now", NOT_NOW);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (optionData.equals(OPEN)) {
            initEntityLocation();
            setDone(true);
            setEndWithContinue(true);
            setShowAgain(false);
        } else {
            setDone(true);
            setEndWithContinue(false);
            setShowAgain(true);
        }
    }

    private void initEntityLocation() {
        if (random.nextFloat() > data.prob) {
            addText("You clean out the spacers wallet, including around 50 years of compound interest. Seems like everything is coming up your way this cycle!");
        } else {
            addText("You clean out the spacers wallet. Weirdly enough, some transaction data shows recent deposits - this account might not be quite as abandoned as you thought.");
            transmitterActivated();
        }

        playerFleet.getCargo().getCredits().add(data.creditAmt);
        AddRemoveCommodity.addCreditsGainText(Math.round(data.creditAmt), text);
    }

    public static final String STOLEN_CREDIT_AMT_KEY = "$IndEvo_stolen_credit_amt";

    public void transmitterActivated() {
        if (data == null) return;
        if (entity == null) return;

        if (data.params != null) {
            CampaignFleetAPI fleet = FleetFactoryV3.createFleet(data.params);
            if (fleet == null || fleet.isEmpty()) return;

            fleet.setTransponderOn(false);
            fleet.setNoFactionInName(true);

            fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PIRATE, true);
            fleet.setName("Impoverished " + (data.params.factionId.equals(Factions.PIRATES) ? "Pirate" : "Mercenary"));
            fleet.getMemoryWithoutUpdate().set("$IndEvo_poor_spacer", true);
            fleet.getMemoryWithoutUpdate().set(STOLEN_CREDIT_AMT_KEY, data.creditAmt);
            Misc.makeLowRepImpact(fleet, "indEvo_creditSpecial");

            float range = data.minRange + random.nextFloat() * (data.maxRange - data.minRange);
            Vector2f loc = Misc.getPointAtRadius(entity.getLocation(), range);

            entity.getContainingLocation().addEntity(fleet);
            fleet.setLocation(loc.x, loc.y);

            makeFleetInterceptPlayer(fleet, true, true, true, 30f);
            Misc.giveStandardReturnToSourceAssignments(fleet, false);
        }
    }

    public static void makeFleetInterceptPlayer(CampaignFleetAPI fleet, boolean makeAggressive, boolean makeLowRepImpact, boolean makeHostile, float interceptDays) {
        fleet.addAbility(Abilities.EMERGENCY_BURN);
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();

        if (fleet.getAI() == null) {
            fleet.setAI(Global.getFactory().createFleetAI(fleet));
            fleet.setLocation(fleet.getLocation().x, fleet.getLocation().y);
        }

        if (makeAggressive) {
            float expire = fleet.getMemoryWithoutUpdate().getExpire(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE);
            fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true, Math.max(expire, interceptDays));
            fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE_ONE_BATTLE_ONLY, true, Math.max(expire, interceptDays));
        }

        if (makeHostile) {
            fleet.getMemoryWithoutUpdate().unset(MemFlags.MEMORY_KEY_MAKE_ALLOW_DISENGAGE);
            fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOSTILE, true, interceptDays);
        }
        fleet.getMemoryWithoutUpdate().set(FleetAIFlags.PLACE_TO_LOOK_FOR_TARGET, new Vector2f(playerFleet.getLocation()), interceptDays);

        if (makeLowRepImpact) {
            Misc.makeLowRepImpact(playerFleet, "csSpecial");
        }

        if (fleet.getAI() instanceof ModularFleetAIAPI) {
            ((ModularFleetAIAPI) fleet.getAI()).getTacticalModule().setTarget(playerFleet);
        }

        fleet.addAssignmentAtStart(FleetAssignment.INTERCEPT, playerFleet, interceptDays, null);
    }

}