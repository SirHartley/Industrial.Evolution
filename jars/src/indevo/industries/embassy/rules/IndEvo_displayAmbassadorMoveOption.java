package indevo.industries.embassy.rules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import indevo.ids.Ids;
import indevo.industries.embassy.industry.Embassy;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import indevo.industries.embassy.listeners.AmbassadorPersonManager;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

public class IndEvo_displayAmbassadorMoveOption extends BaseCommandPlugin {
    private static final String OPTION_ID = "IndEvo_ambMoveOption";

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        OptionPanelAPI panel = dialog.getOptionPanel();

        MarketAPI market = Global.getSector().getEconomy().getMarket(memoryMap.get(MemKeys.MARKET).getString("$id"));
        MarketAPI closestEmbassy = AmbassadorPersonManager.getClosestEmptyEmbassyToMarket(market);

        PersonAPI person = AmbassadorPersonManager.getAmbassador(market);
        String pronoun = person.getGender() == FullName.Gender.MALE ? "him" : "her";

        panel.addOption("Ask " + pronoun + " to relocate to the closest empty Embassy", OPTION_ID);

        if (closestEmbassy == null) {
            panel.setEnabled(OPTION_ID, false);
            panel.setTooltip(OPTION_ID, "You do not have an open Embassy slot anywhere.");
            return false;
        }

        Embassy embassy = (Embassy) market.getIndustry(Ids.EMBASSY);

        if (!embassy.isAmbMovementAllowed()) {
            panel.setEnabled(OPTION_ID, false);
            panel.setTooltip(OPTION_ID, "The minimum office period has not passed yet - "
                    + (3 - embassy.getMonthsPassed()) + " Months remain before relocation is possible.");
            return false;
        }

        panel.addOptionConfirmation(OPTION_ID, "Moving the Ambassador to " + closestEmbassy.getName()
                        + " in the " + closestEmbassy.getStarSystem().getName()
                        + " will cost " + Misc.getDGSCredits(getTransoportCost(market, closestEmbassy))
                        + " in transport and escort fees.",
                "Confirm",
                "Return");

        return true;
    }

    public static float getTransoportCost(MarketAPI from, MarketAPI to) {
        float ly = Misc.getDistanceLY(from.getLocation(), to.getLocation());
        float lyMult = (float) Math.round((1f + ((ly * Math.pow(1000f, 1f + (ly / 250f))) / 10000f)) * 10f) / 10f;
        return 5000f * lyMult;
    }

    @Override
    public boolean doesCommandAddOptions() {
        return true;
    }

    @Override
    public int getOptionOrder(List<Misc.Token> params, final Map<String, MemoryAPI> memoryMap) {
        return (int) params.get(0).getFloat(memoryMap);
    }
}