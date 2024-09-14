package indevo.submarkets;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CoreUIAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Highlights;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.RestorationDocks;
import indevo.utils.helper.MiscIE;
import indevo.utils.helper.StringHelper;

import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static indevo.ids.Ids.REPAIRDOCKS;

public class RestorationDocksSubmarketPlugin extends BaseSubmarketPlugin implements DynamicSubmarket {

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
        return action != TransferAction.PLAYER_BUY
                && (RestorationDocks.getListNonBuiltInDMods(member.getVariant()).isEmpty()
                || member.getVariant().getTags().contains(Tags.VARIANT_UNRESTORABLE)
                || member.getHullSpec().getTags().contains(Tags.HULL_UNRESTORABLE));
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
        return StringHelper.getString("take");
    }

    @Override
    public String getSellVerb() {
        return StringHelper.getString("leave");
    }

    @Override
    public String getIllegalTransferText(FleetMemberAPI member, TransferAction action) {
        if (member.getVariant().getTags().contains(Tags.VARIANT_UNRESTORABLE) || member.getHullSpec().getTags().contains(Tags.HULL_UNRESTORABLE)) return "Can not be restored";

        if (DModManager.getNumNonBuiltInDMods(member.getVariant()) == 0 && DModManager.getNumDMods(member.getVariant()) > 0)
            return StringHelper.getString(IDENT, "cannotRepair");
        else return StringHelper.getString(IDENT, "cannotTransfer");
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

        RestorationDocks restorationDocks = (RestorationDocks) market.getIndustry(REPAIRDOCKS);
        restorationDocks.publicAddRightAfterDescriptionSection(tooltip, Industry.IndustryTooltipMode.NORMAL);

        if (expanded) {
            String aiCoreId = MiscIE.getAiCoreIdNotNull(restorationDocks);
            HashMap<FleetMemberAPI, Integer> maxDModMemory = (HashMap<FleetMemberAPI, Integer>) Global.getSector().getMemoryWithoutUpdate().get("$IndEvo_maxDModMemory");

            List<FleetMemberAPI> fleet = restorationDocks.getEligibleShips(submarket);

            if (fleet.isEmpty()) {
                tooltip.addPara("%s", opad, Misc.getNegativeHighlightColor(), StringHelper.getString(IDENT, "costPredictionShips"));
                return;
            }

            String subst = StringHelper.getString(IDENT, "shipPredictionHL");
            tooltip.addPara(StringHelper.getStringAndSubstituteToken(IDENT, "shipPrediction", "$shipPredictionHL", subst), opad, hlColor, subst);

            String hullType = StringHelper.getString("hulltype");
            String dMod = StringHelper.getString("dmod");
            String perDMod = StringHelper.getString("perDMod");
            String totalMax = StringHelper.getString("totalMax");

            tooltip.beginTable(market.getFaction(), 20f, hullType, 140f, dMod, 50f, perDMod, 100f, totalMax, 100f);
            float total = 0;
            int totalDMods = 0;

            for (FleetMemberAPI ship : fleet) {
                String shipName = ship.getHullSpec().getHullName();
                int currentDModAmount = RestorationDocks.getListNonBuiltInDMods(ship.getVariant()).size();
                float costPerDMod = restorationDocks.getSingleDModRepairCost(ship, maxDModMemory);
                float totalMaximumCost = costPerDMod * currentDModAmount;
                totalDMods += RestorationDocks.getListNonBuiltInDMods(ship.getVariant()).size();

                if (aiCoreId.equals(Commodities.GAMMA_CORE)) {
                    total += costPerDMod;
                }

                tooltip.addRow(shipName, Integer.toString(currentDModAmount), Misc.getDGSCredits(costPerDMod), Misc.getDGSCredits(totalMaximumCost));
            }

            tooltip.addTable(StringHelper.getString(IDENT, "noShips"), 0, opad);
            if (aiCoreId.equals(Commodities.BETA_CORE)) {
                int dModCounter = 0;

                HashMap<FleetMemberAPI, Integer> eligibleShipsDNum = new HashMap<>();
                for (FleetMemberAPI s : fleet) {
                    eligibleShipsDNum.put(s, restorationDocks.getNumNonBuiltInDMods(s.getVariant()));
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
                        tooltip.addPara("%s", 10f, Misc.getNegativeHighlightColor(), StringHelper.getString(IDENT, "shitBroke"));
                        return;
                    }

                    //calculate cost
                    total += restorationDocks.getSingleDModRepairCost(min.getKey(), maxDModMemory);
                    dModCounter++;
                    //remove counted d-mod from list - delete the entry or put the incremented entry back on the list
                    if (min.getValue() - 1 < 1) {
                        eligibleShipsDNum.remove(min.getKey());
                    } else {
                        eligibleShipsDNum.put(min.getKey(), min.getValue() - 1);
                    }
                }
            }

            String exactPredictionHL = StringHelper.getString(IDENT, "exactPredictionHL");
            int cap = restorationDocks.getMaxDModRepairAmt();

            switch (aiCoreId) {
                case Commodities.GAMMA_CORE:
                    if (totalDMods > cap) {
                        tooltip.addPara(StringHelper.getStringAndSubstituteToken(IDENT, "gCoreNoPred", "$exactPredictionHL", exactPredictionHL),
                                opad, hlColor, exactPredictionHL);
                    } else {
                        tooltip.addPara(StringHelper.getStringAndSubstituteToken(IDENT, "pred", "$exactPredictionHL", exactPredictionHL),
                                opad, hlColor, exactPredictionHL);
                    }
                    break;
                case Commodities.BETA_CORE:
                    tooltip.addPara(StringHelper.getStringAndSubstituteToken(IDENT, "pred", "$exactPredictionHL", exactPredictionHL),
                            opad, hlColor, exactPredictionHL);
                    break;
                default:
                    tooltip.addPara(StringHelper.getStringAndSubstituteToken(IDENT, "noPred", "$exactPredictionHL", exactPredictionHL),
                            opad, hlColor, exactPredictionHL);
                    break;
            }
        } else tooltip.addPara("%s",
                opad, hlColor, StringHelper.getString(IDENT, "expand"));

        if (market.hasIndustry(REPAIRDOCKS)) {
            if (market.getIndustry(REPAIRDOCKS).getAICoreId() != null) {
                RestorationDocks dd = (RestorationDocks) market.getIndustry(REPAIRDOCKS);
                dd.addInstalledItemsSection(Industry.IndustryTooltipMode.NORMAL, tooltip, expanded);
            } else {
                FactionAPI faction = market.getFaction();
                Color color = faction.getBaseUIColor();
                Color dark = faction.getDarkUIColor();

                tooltip.addSectionHeading(StringHelper.getString("IndEvo_items", "items"), color, dark, Alignment.MID, opad);
                tooltip.addPara(StringHelper.getString(IDENT, "noCore"), opad);
            }
        }
    }
}

