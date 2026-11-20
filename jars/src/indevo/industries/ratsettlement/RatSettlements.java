package indevo.industries.ratsettlement;

import assortment_of_things.frontiers.FrontiersUtils;
import assortment_of_things.frontiers.SettlementData;
import assortment_of_things.frontiers.data.SettlementFacilitySlot;
import assortment_of_things.frontiers.plugins.facilities.BaseSettlementFacility;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

// the only ie side entry to rat settlements; every rat call sits behind isEnabled
public class RatSettlements {

    public static final String MOD_ID = "assortment_of_things";
    public static final String MARKET_ID = "rat_station_commander_market";

    public static boolean isEnabled() {
        return Global.getSettings().getModManager().isModEnabled(MOD_ID);
    }

    public static boolean isSettlementMarket(MarketAPI market) {
        return market != null && MARKET_ID.equals(market.getId());
    }

    public static MarketAPI getSettlementMarket() {
        SettlementData data = getSettlement();
        return data != null ? data.getSettlementEntity().getMarket() : null;
    }

    public static boolean hasFunctionalFacility(String id) {
        return getFacility(id) != null;
    }

    public static BaseSettlementFacility getFacility(String id) {
        SettlementData data = getSettlement();
        if (data == null) return null;

        for (SettlementFacilitySlot slot : data.getFacilitySlots()) {
            if (slot.isFunctional() && id.equals(slot.getFacilityID())) return slot.getPlugin();
        }

        return null;
    }

    // hands the dialog back to rat's settlement plugin, init redraws its options
    public static void returnTo(InteractionDialogAPI dialog, InteractionDialogPlugin ratPlugin) {
        dialog.setPlugin(ratPlugin);
        dialog.showTextPanel();
        dialog.showVisualPanel();
        dialog.getTextPanel().clear();
        dialog.setPromptText("You decide to...");
        ratPlugin.init(dialog);
    }

    private static SettlementData getSettlement() {
        if (!isEnabled()) return null;
        return FrontiersUtils.INSTANCE.getFrontiersData().getActiveSettlement();
    }
}
