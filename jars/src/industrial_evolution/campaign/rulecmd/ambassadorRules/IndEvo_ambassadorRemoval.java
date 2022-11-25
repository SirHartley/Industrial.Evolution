package industrial_evolution.campaign.rulecmd.ambassadorRules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import industrial_evolution.campaign.ids.IndEvo_ids;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import industrial_evolution.plugins.ambassadorPlugins.IndEvo_ambassadorPersonManager;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;
import java.util.List;
import java.util.Map;

public class IndEvo_ambassadorRemoval extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        MarketAPI market = Global.getSector().getEconomy().getMarket(memoryMap.get(MemKeys.MARKET).getString("$id"));
        removeAmbassadorWithPenalty(market);
        return true;
    }

    public static void removeAmbassadorWithPenalty(MarketAPI market) {
        if(market == null) return;

        PersonAPI person = IndEvo_ambassadorPersonManager.getAmbassador(market);

        MarketAPI originalMarket = null;
        FactionAPI ambFaction = null;
        float penaltyAmount = 0f;

        if (person != null){
            originalMarket =  IndEvo_ambassadorPersonManager.getOriginalMarket(person);
            ambFaction = person.getFaction();
            penaltyAmount = getPenaltyFor(market.getFaction().getRelationship(ambFaction.getId()));
        }

        IndEvo_ambassadorPersonManager.removeAmbassadorFromMarket(market);

        if (originalMarket != null && IndEvo_ambassadorPersonManager.getAmbassador(originalMarket) != null) {
            IndEvo_ambassadorPersonManager.deleteAmbassador(originalMarket);
        }

        if (originalMarket != null) IndEvo_ambassadorPersonManager.addAmbassadorToMarket(person, originalMarket);

        market.getIndustry(IndEvo_ids.EMBASSY).setSpecialItem(null);

        if (penaltyAmount != 0) {
            market.getFaction().adjustRelationship(ambFaction.getId(), penaltyAmount);

            FactionAPI player = Global.getSector().getPlayerFaction();
            int repInt = (int) Math.ceil((Math.round(player.getRelationship(ambFaction.getId()) * 100f)));
            RepLevel level = player.getRelationshipLevel(ambFaction.getId());
            Color relColor = ambFaction.getRelColor(player.getId());
            String standing = "" + repInt + "/100" + " (" + level.getDisplayName().toLowerCase() + ")";

            MessageIntel intel = new MessageIntel("The %s Ambassador, " + person.getNameString() + ", has been dismissed.", Misc.getTextColor(), new String[]{(ambFaction.getDisplayName() + "")}, ambFaction.getColor());
            intel.addLine(BaseIntelPlugin.BULLET + "Relationship %s", Misc.getTextColor(), new String[]{("reduced by " + (int) (penaltyAmount * 100))}, Misc.getNegativeHighlightColor());
            intel.addLine(BaseIntelPlugin.BULLET + "Currently at %s", null, new String[]{standing}, relColor);
            intel.setIcon(ambFaction.getCrest());
            Global.getSector().getCampaignUI().addMessage(intel);
            Global.getSoundPlayer().playUISound("ui_rep_drop", 1, 1);
        }
    }

    private static boolean isBetween(float check, float lower, float higher) {
        return (check >= lower && check <= higher);
    }

    private static float getPenaltyFor(float rel) {
        if (isBetween(rel, -0.25F, -0.09F)) {
            return -0.1F;
        }
        if (isBetween(rel, -0.10F, 0.10F)) {
            return -0.15F;
        }
        if (isBetween(rel, 0.11F, 0.25F)) {
            return -0.25F;
        }
        if (isBetween(rel, 0.26F, 0.50F)) {
            return -0.40F;
        }
        if (isBetween(rel, 0.51F, 1F)) {
            return -0.60F;
        }

        return 0;
    }
}
