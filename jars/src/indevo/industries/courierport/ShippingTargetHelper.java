package indevo.industries.courierport;

import assortment_of_things.frontiers.FrontiersUtils;
import assortment_of_things.frontiers.SettlementData;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import indevo.ids.Ids;
import indevo.utils.helper.MiscIE;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static indevo.industries.courierport.ShippingCostCalculator.AI_CORE_ID_STRING;

public class ShippingTargetHelper {

    public static List<SectorEntityToken> getValidOriginPlanets() {
        List<SectorEntityToken> marketList = new ArrayList<>();
        Set<String> whitelist = MiscIE.getCSVSetFromMemory(Ids.SHIPPING_LIST);

        if (Global.getSettings().getModManager().isModEnabled("assortment_of_things")){
            SettlementData data = FrontiersUtils.INSTANCE.getFrontiersData().getActiveSettlement();
            if (data != null) marketList.add(data.getSettlementEntity());
        }

        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (m.getFaction().isHostileTo("player") || MiscIE.getStorageCargo(m) == null) continue;

            for (SubmarketAPI s : m.getSubmarketsCopy()) {
                if (whitelist.contains(s.getSpecId())) {
                    marketList.add(m.getPrimaryEntity());
                    break;
                }
            }
        }

        return marketList;
    }

    public static List<SectorEntityToken> getValidTargetPlanets(ShippingContract contract) {
        List<SectorEntityToken> marketList = new ArrayList<>();

        if (Global.getSettings().getModManager().isModEnabled("assortment_of_things")){
            SettlementData data = FrontiersUtils.INSTANCE.getFrontiersData().getActiveSettlement();
            if (data != null) marketList.add(data.getSettlementEntity());
        }

        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (m.getFaction().isHostileTo("player") || MiscIE.getStorageCargo(m) == null) continue;

            if (contract.fromSubmarketId == null && m.hasSubmarket(Submarkets.SUBMARKET_STORAGE))
                marketList.add(m.getPrimaryEntity());
            else {
                MarketAPI market = contract.getFromMarket();
                SubmarketAPI sub =
                        contract.fromSubmarketId != null
                                && market != null
                                && market.hasSubmarket(contract.fromSubmarketId) ? market.getSubmarket(contract.fromSubmarketId) : null;

                if (market != null && sub != null && !getValidTargetSubmarkets(m, sub).isEmpty()) {
                    marketList.add(m.getPrimaryEntity());
                }
            }
            ;
        }

        return marketList;
    }

    public static String getMemoryAICoreId() {
        String id = Global.getSector().getMemoryWithoutUpdate().getString(AI_CORE_ID_STRING);
        return id != null ? id : "none";
    }

    public static Set<SubmarketAPI> getValidOriginSubmarkets(MarketAPI market) {
        Set<SubmarketAPI> finalSet = new LinkedHashSet<>();
        Set<String> whitelist = MiscIE.getCSVSetFromMemory(Ids.SHIPPING_LIST);

        if (market != null) {
            for (SubmarketAPI sub : market.getSubmarketsCopy()) {
                if (sub.getSpecId().equals(Submarkets.LOCAL_RESOURCES)) continue;

                if (sub.getPlugin().isFreeTransfer()
                        && whitelist.contains(sub.getSpecId())) finalSet.add(sub);
            }
        }

        return finalSet;
    }

    public static Set<SubmarketAPI> getValidTargetSubmarkets(MarketAPI onMarket, SubmarketAPI fromSubmarket) {
        Set<SubmarketAPI> submarketSet = new LinkedHashSet<>();

        for (SubmarketAPI sub : onMarket.getSubmarketsCopy()) {
            if (sub.getSpecId().equals(Submarkets.LOCAL_RESOURCES)) continue;

            Set<String> whitelist = MiscIE.getCSVSetFromMemory(Ids.SHIPPING_LIST);
            boolean matchingCargoScreen = sub.getPlugin().showInCargoScreen() && fromSubmarket.getPlugin().showInCargoScreen();
            boolean matchingFleetScreen = sub.getPlugin().showInFleetScreen() && fromSubmarket.getPlugin().showInFleetScreen();
            boolean intersect = matchingCargoScreen || matchingFleetScreen;

            if (sub.getPlugin().isFreeTransfer()
                    && whitelist.contains(sub.getSpecId())
                    && intersect) {

                submarketSet.add(sub);
            }
        }

        return submarketSet;
    }
}
