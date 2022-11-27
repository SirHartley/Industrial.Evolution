package indevo.submarkets;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin;
import indevo.utils.helper.IndEvo_IndustryHelper;
import indevo.utils.helper.IndEvo_StringHelper;
import com.fs.starfarer.api.campaign.CoreUIAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.DModManager;
import indevo.industries.IndEvo_dryDock;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Highlights;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static indevo.ids.IndEvo_ids.REPAIRDOCKS;

public class IndEvo_RepairStoragePlugin extends BaseSubmarketPlugin implements IndEvo_DynamicSubmarket {

    public boolean isSetForRemoval = false;
    private static final String IDENT = "IndEvo_dryDock";

    public void init(SubmarketAPI submarket) {
        super.init(submarket);
        this.submarket.setFaction(Global.getSector().getFaction("dryDockButtonColor"));
    }

    public void updateCargoPrePlayerInteraction() {

    }

    public void prepareForRemoval() {
        this.isSetForRemoval = true;
    }

    @Override
    public boolean isIllegalOnSubmarket(FleetMemberAPI member, TransferAction action) {
        if (market.hasIndustry(REPAIRDOCKS)) {
            IndEvo_dryDock dryDock = (IndEvo_dryDock) market.getIndustry(REPAIRDOCKS);

            return action != TransferAction.PLAYER_BUY && IndEvo_dryDock.getListNonBuiltInDMods(member.getVariant()).isEmpty();
        }

        return true;
    }

    @Override
    public boolean isParticipatesInEconomy() {
        return false;
    }

    @Override
    public float getTariff() {
        return 0f;
    }

    @Override
    public boolean isFreeTransfer() {
        return true;
    }

    @Override
    public String getBuyVerb() {
        return IndEvo_StringHelper.getString("take");
    }

    @Override
    public String getSellVerb() {
        return IndEvo_StringHelper.getString("leave");
    }

    @Override
    public String getIllegalTransferText(FleetMemberAPI member, TransferAction action) {
        if (DModManager.getNumNonBuiltInDMods(member.getVariant()) == 0 && DModManager.getNumDMods(member.getVariant()) > 0)
            return IndEvo_StringHelper.getString(IDENT, "cannotRepair");
        else return IndEvo_StringHelper.getString(IDENT, "cannotTransfer");
    }

    @Override
    public boolean isEnabled(CoreUIAPI ui) {
        return true;
    }

    public OnClickAction getOnClickAction(CoreUIAPI ui) {
        return OnClickAction.OPEN_SUBMARKET;
    }

    public String getTooltipAppendix(CoreUIAPI ui) {
        return null;
    }

    public Highlights getTooltipAppendixHighlights(CoreUIAPI ui) {
        return null;
    }

    public boolean showInFleetScreen() {
        return !isSetForRemoval;
    }

    public boolean showInCargoScreen() {
        return false;
    }

    public boolean isTooltipExpandable() {
        return true;
    }

    public float getTooltipWidth() {
        return 400f;
    }

    //tooltip should be properly integrated into docks
    //fuck that, it's good enough

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        float opad = 10f;
        Color hlColor = Misc.getHighlightColor();

        IndEvo_dryDock dryDock = (IndEvo_dryDock) market.getIndustry(REPAIRDOCKS);
        dryDock.publicAddRightAfterDescriptionSection(tooltip, Industry.IndustryTooltipMode.NORMAL);

        if (expanded) {
            String aiCoreId = IndEvo_IndustryHelper.getAiCoreIdNotNull(dryDock);
            HashMap<FleetMemberAPI, Integer> maxDModMemory = (HashMap<FleetMemberAPI, Integer>) Global.getSector().getMemoryWithoutUpdate().get("$IndEvo_maxDModMemory");

            List<FleetMemberAPI> fleet = dryDock.getEligibleShips(submarket);

            if (fleet.isEmpty()) {
                tooltip.addPara("%s", opad, Misc.getNegativeHighlightColor(), IndEvo_StringHelper.getString(IDENT, "costPredictionShips"));
                return;
            }

            String subst = IndEvo_StringHelper.getString(IDENT, "shipPredictionHL");
            tooltip.addPara(IndEvo_StringHelper.getStringAndSubstituteToken(IDENT, "shipPrediction", "$shipPredictionHL", subst), opad, hlColor, subst);

            String hullType = IndEvo_StringHelper.getString("hulltype");
            String dMod = IndEvo_StringHelper.getString("dmod");
            String perDMod = IndEvo_StringHelper.getString("perDMod");
            String totalMax = IndEvo_StringHelper.getString("totalMax");

            tooltip.beginTable(market.getFaction(), 20f, hullType, 140f, dMod, 50f, perDMod, 100f, totalMax, 100f);
            float total = 0;
            int totalDMods = 0;

            for (FleetMemberAPI ship : fleet) {
                String shipName = ship.getHullSpec().getHullName();
                int currentDModAmount = IndEvo_dryDock.getListNonBuiltInDMods(ship.getVariant()).size();
                float costPerDMod = dryDock.getSingleDModRepairCost(ship, maxDModMemory);
                float totalMaximumCost = costPerDMod * currentDModAmount;
                totalDMods += IndEvo_dryDock.getListNonBuiltInDMods(ship.getVariant()).size();

                if (aiCoreId.equals(Commodities.GAMMA_CORE)) {
                    total += costPerDMod;
                }

                tooltip.addRow(shipName, Integer.toString(currentDModAmount), Misc.getDGSCredits(costPerDMod), Misc.getDGSCredits(totalMaximumCost));
            }

            tooltip.addTable(IndEvo_StringHelper.getString(IDENT, "noShips"), 0, opad);
            if (aiCoreId.equals(Commodities.BETA_CORE)) {
                int dModCounter = 0;

                HashMap<FleetMemberAPI, Integer> eligibleShipsDNum = new HashMap<>();
                for (FleetMemberAPI s : fleet) {
                    eligibleShipsDNum.put(s, dryDock.getNumNonBuiltInDMods(s.getVariant()));
                }

                while (dModCounter < market.getSize() && !eligibleShipsDNum.isEmpty()) {
                    //get ship with currently lowest amount of dmods
                    Map.Entry<FleetMemberAPI, Integer> min = null;

                    for (Map.Entry<FleetMemberAPI, Integer> entry : eligibleShipsDNum.entrySet()) {
                        if (min == null || min.getValue() > entry.getValue()) {
                            min = entry;
                        }
                    }

                    if (min == null) {
                        tooltip.addPara("%s", 10f, Misc.getNegativeHighlightColor(), IndEvo_StringHelper.getString(IDENT, "shitBroke"));
                        return;
                    }

                    //calculate cost
                    total += dryDock.getSingleDModRepairCost(min.getKey(), maxDModMemory);
                    dModCounter++;
                    //remove counted d-mod from list - delete the entry or put the incremented entry back on the list
                    if (min.getValue() - 1 < 1) {
                        eligibleShipsDNum.remove(min.getKey());
                    } else {
                        eligibleShipsDNum.put(min.getKey(), min.getValue() - 1);
                    }
                }
            }

            String exactPredictionHL = IndEvo_StringHelper.getString(IDENT, "exactPredictionHL");
            int cap = dryDock.getMaxDModRepairAmt();

            switch (aiCoreId) {
                case Commodities.GAMMA_CORE:
                    if (totalDMods > cap) {
                        tooltip.addPara(IndEvo_StringHelper.getStringAndSubstituteToken(IDENT, "gCoreNoPred", "$exactPredictionHL", exactPredictionHL),
                                opad, hlColor, exactPredictionHL);
                    } else {
                        tooltip.addPara(IndEvo_StringHelper.getStringAndSubstituteToken(IDENT, "pred", "$exactPredictionHL", exactPredictionHL),
                                opad, hlColor, exactPredictionHL);
                    }
                    break;
                case Commodities.BETA_CORE:
                    tooltip.addPara(IndEvo_StringHelper.getStringAndSubstituteToken(IDENT, "pred", "$exactPredictionHL", exactPredictionHL),
                            opad, hlColor, exactPredictionHL);
                    break;
                default:
                    tooltip.addPara(IndEvo_StringHelper.getStringAndSubstituteToken(IDENT, "noPred", "$exactPredictionHL", exactPredictionHL),
                            opad, hlColor, exactPredictionHL);
                    break;
            }
        } else tooltip.addPara("%s",
                opad, hlColor, IndEvo_StringHelper.getString(IDENT, "expand"));

        if (market.hasIndustry(REPAIRDOCKS)) {
            if (market.getIndustry(REPAIRDOCKS).getAICoreId() != null) {
                IndEvo_dryDock dd = (IndEvo_dryDock) market.getIndustry(REPAIRDOCKS);
                dd.addInstalledItemsSection(Industry.IndustryTooltipMode.NORMAL, tooltip, expanded);
            } else {
                FactionAPI faction = market.getFaction();
                Color color = faction.getBaseUIColor();
                Color dark = faction.getDarkUIColor();

                tooltip.addSectionHeading(IndEvo_StringHelper.getString("IndEvo_items", "items"), color, dark, Alignment.MID, opad);
                tooltip.addPara(IndEvo_StringHelper.getString(IDENT, "noCore"), opad);
            }
        }
    }
}

