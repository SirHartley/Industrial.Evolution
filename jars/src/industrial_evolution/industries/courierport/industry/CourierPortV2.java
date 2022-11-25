package industrial_evolution.industries.courierport.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import industrial_evolution.industries.courierport.ShippingContract;
import industrial_evolution.industries.courierport.ShippingContractMemory;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;
import java.util.List;
import java.util.*;

import static industrial_evolution.industries.courierport.ShippingCostCalculator.*;

public class CourierPortV2 extends BaseIndustry {

    public void apply() {
        super.apply(true);
    }

    @Override
    public void unapply() {
        super.unapply();
    }

    @Override
    public boolean isAvailableToBuild() {
        return Global.getSettings().getBoolean("PrivatePort");
    }

    @Override
    public boolean showWhenUnavailable() {
        return Global.getSettings().getBoolean("PrivatePort");
    }

    @Override
    protected void addRightAfterDescriptionSection(TooltipMakerAPI tooltip, IndustryTooltipMode mode) {
        super.addRightAfterDescriptionSection(tooltip, mode);
        float opad = 10.0F;

        tooltip.addPara("Only the AI core %s in any port is valid for contracts.",
                opad, Misc.getHighlightColor(), new String[]{"last installed"});
    }

    @Override
    protected void addPostUpkeepSection(TooltipMakerAPI tooltip, IndustryTooltipMode mode) {
        if (!mode.equals(IndustryTooltipMode.NORMAL)) return;
        addUpcomingShipmentList(tooltip, mode, false);
    }

    public void addUpcomingShipmentList(TooltipMakerAPI tooltip, IndustryTooltipMode mode, boolean expanded) {

        FactionAPI marketFaction = market.getFaction();
        Color color = marketFaction.getBaseUIColor();
        Color dark = marketFaction.getDarkUIColor();

        float opad = 5.0F;

        tooltip.addSectionHeading("Upcoming Shipments", color, dark, Alignment.MID, 10f);

        tooltip.beginTable(marketFaction, 20f, "From", 150f, "To", 150f, "In", 90f);

        int i = 0;
        Map<ShippingContract, Integer> dayList = getShipmentWithDayList();

        for (Map.Entry<ShippingContract, Integer> entry : dayList.entrySet()) {
            if(!entry.getKey().isValid() || (!entry.getKey().isActive && entry.getKey().elapsedDays > 0)) continue;
            i++;
            String origin = entry.getKey().getFromMarket().getName();
            String destination = entry.getKey().getToMarket().getName();
            String days = entry.getValue() + " " + BaseIntelPlugin.getDaysString(entry.getValue());

            tooltip.addRow(origin, destination, days);

            if (i > 4 && dayList.size() > 5) break;
        }

        tooltip.addTable("No planned shipments.", 0, opad);

        if (i > 4 && dayList.size() > 5)
            tooltip.addPara("... and " + (dayList.size() - 5) + " more upcoming shipments.",
                    opad, Misc.getHighlightColor(), new String[]{(dayList.size() - 5) + ""});
    }

    private Map<ShippingContract, Integer> getShipmentWithDayList() {
        List<ShippingContract> containerList = ShippingContractMemory.getContractList();
        Map<ShippingContract, Integer> daymap = new HashMap<>();

        for (ShippingContract contract : containerList) {
            daymap.put(contract, contract.getRecurrentDays() - contract.elapsedDays);
        }

        return sort(daymap);
    }

    // TODO: 22.09.2020 this should be a method in industryTools with no set obj, really
    // TODO: 18/04/2022 status update: I do not care
    private static Map<ShippingContract, Integer> sort(Map<ShippingContract, Integer> hm) {
        // Create a list from elements of HashMap
        List<Map.Entry<ShippingContract, Integer>> list =
                new LinkedList<Map.Entry<ShippingContract, Integer>>(hm.entrySet());

        // Sort the list
        Collections.sort(list, new Comparator<Map.Entry<ShippingContract, Integer>>() {
            public int compare(Map.Entry<ShippingContract, Integer> o1,
                               Map.Entry<ShippingContract, Integer> o2) {
                return (o1.getValue()).compareTo(o2.getValue());
            }
        });

        // put data from sorted list into a hashmap
        Map<ShippingContract, Integer> temp = new LinkedHashMap<>();
        for (Map.Entry<ShippingContract, Integer> aa : list) {
            temp.put(aa.getKey(), aa.getValue());
        }
        return temp;
    }

    @Override
    protected void applyAICoreModifiers() {
        super.applyAICoreModifiers();
        //removeAICores();

        setAIcoreMemoryKey();
    }

    public static String getMemoryAICoreId() {
        String id = Global.getSector().getMemoryWithoutUpdate().getString(AI_CORE_ID_STRING);

        return id != null ? id : "none";
    }

    private void setAIcoreMemoryKey() {
        String id = getAICoreId();
        id = id != null ? id : "none";

        Global.getSector().getMemoryWithoutUpdate().set(AI_CORE_ID_STRING, id);
    }

    @Override
    public void addInstalledItemsSection(IndustryTooltipMode mode, TooltipMakerAPI tooltip, boolean expanded) {
        float opad = 10f;

        FactionAPI faction = market.getFaction();
        Color color = faction.getBaseUIColor();
        Color dark = faction.getDarkUIColor();

        LabelAPI heading = tooltip.addSectionHeading("Items", color, dark, Alignment.MID, opad);

        boolean addedSomething = false;
        if (!getMemoryAICoreId().equals("none")) {
            AICoreDescriptionMode aiCoreDescMode = AICoreDescriptionMode.INDUSTRY_TOOLTIP;
            addAICoreSection(tooltip, getMemoryAICoreId(), aiCoreDescMode);
            addedSomething = true;
        }

        addedSomething |= addNonAICoreInstalledItems(mode, tooltip, expanded);

        if (!addedSomething) {
            heading.setText("No items installed");
            //tooltip.addPara("None.", opad);
        }
    }

    //AI-Core tooltips

    //gamma allows accessing the menu from any comms relay
    //beta reduces distance mult
    //alpha reduces shipping costs

    @Override
    protected void addAlphaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = Misc.getHighlightColor();
        Color bad = Misc.getNegativeHighlightColor();
        String pre = "Alpha-level AI core currently assigned. ";
        if (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Alpha-level AI core. ";
        }

        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(this.aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + "Decreases the %s by %s", 0f, Misc.getPositiveHighlightColor(), new String[]{"total shipping costs", (int) Math.round((1 - TOTAL_FEE_REDUCTION) * 100) + "%"});
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + pre + "Decreases the %s by %s", opad, Misc.getPositiveHighlightColor(), new String[]{"total shipping costs", (int) Math.round((1 - TOTAL_FEE_REDUCTION) * 100) + "%"});
        }
    }

    @Override
    protected void addBetaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = Misc.getHighlightColor();
        Color bad = Misc.getNegativeHighlightColor();
        String pre = "Beta-level AI core currently assigned. ";
        if (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Beta-level AI core. ";
        }

        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(this.aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + "Decreases the %s by %s", 0f, Misc.getPositiveHighlightColor(), new String[]{"distance multiplier", (int) Math.round((1 - DISTANCE_MULT_REDUCTION) * 100) + "%"});
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + "Decreases the %s by %s", opad, Misc.getPositiveHighlightColor(), new String[]{"distance multiplier", (int) Math.round((1 - DISTANCE_MULT_REDUCTION) * 100) + "%"});
        }
    }

    @Override
    protected void addGammaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = Misc.getHighlightColor();
        Color bad = Misc.getNegativeHighlightColor();
        String pre = "Gamma-level AI core currently assigned. ";
        if (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Gamma-level AI core. ";
        }

        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(this.aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + "Allows accessing the shipping menu at %s.", 0f, Misc.getPositiveHighlightColor(), new String[]{"comm relays"});
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + "Allows accessing the shipping menu at %s.", 0f, Misc.getPositiveHighlightColor(), new String[]{"comm relays"});
        }
    }
}
