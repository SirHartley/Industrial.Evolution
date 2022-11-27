package indevo.converters.scripts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_IsFactionRuler;
import com.fs.starfarer.api.impl.campaign.shared.PlayerTradeDataForSubmarket;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.impl.campaign.submarkets.StoragePlugin;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.SectorManager;
import exerelin.campaign.intel.MarketTransferIntel;
import exerelin.campaign.submarkets.Nex_LocalResourcesSubmarketPlugin;
import exerelin.utilities.NexUtils;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.NexUtilsMarket;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static indevo.converters.conditions.IndEvo_LogCoreCond.*;

public class IndEvo_TakeoverScriptWithNex {

    public static void finalizeColonyExchange(MarketAPI market, String newOwnerId, String oldOwnerId) {
        FactionAPI newOwner = Global.getSector().getFaction(newOwnerId);
        FactionAPI oldOwner = Global.getSector().getFaction(oldOwnerId);

        // transfer market and associated entities
        Set<SectorEntityToken> linkedEntities = market.getConnectedEntities();
        if (market.getPlanetEntity() != null)
            linkedEntities.addAll(getCapturableEntitiesAroundPlanet(market.getPlanetEntity()));
        for (SectorEntityToken entity : linkedEntities) entity.setFaction(newOwnerId);

        // transfer defense station
        if (Misc.getStationFleet(market) != null) Misc.getStationFleet(market).setFaction(newOwnerId, true);
        if (Misc.getStationBaseFleet(market) != null) Misc.getStationBaseFleet(market).setFaction(newOwnerId, true);

        // don't lock player out of freshly captured market
        if (!newOwner.isHostileTo(Factions.PLAYER))
            market.getMemoryWithoutUpdate().unset(MemFlags.MEMORY_KEY_PLAYER_HOSTILE_ACTIVITY_NEAR_MARKET);

        // player: free storage unlock
        if (Nex_IsFactionRuler.isRuler(newOwnerId)) {
            SubmarketAPI storage = market.getSubmarket(Submarkets.SUBMARKET_STORAGE);
            if (storage != null) {
                StoragePlugin plugin = (StoragePlugin) market.getSubmarket(Submarkets.SUBMARKET_STORAGE).getPlugin();
                if (plugin != null)
                    plugin.setPlayerPaidToUnlock(true);
            }
        }

        // handle local resources stockpiles taken
        if (market.hasSubmarket(Submarkets.LOCAL_RESOURCES)) {
            Nex_LocalResourcesSubmarketPlugin plugin = (Nex_LocalResourcesSubmarketPlugin)
                    market.getSubmarket(Submarkets.LOCAL_RESOURCES).getPlugin();
            plugin.billCargo();
        }


        updateSubmarkets(market, newOwnerId);

        // set submarket factions
        List<SubmarketAPI> submarkets = market.getSubmarketsCopy();
        for (SubmarketAPI submarket : submarkets) {
            String submarketId = submarket.getSpecId();
            if (!ALWAYS_CAPTURE_SUBMARKET.contains(submarketId)) {
                if (submarket.getPlugin().isFreeTransfer()) continue;
                if (!submarket.getPlugin().isParticipatesInEconomy()) continue;
            }

            // reset smuggling suspicion
            if (submarketId.equals(Submarkets.SUBMARKET_BLACK)) {
                PlayerTradeDataForSubmarket tradeData = SharedData.getData().getPlayerActivityTracker().getPlayerTradeData(submarket);
                tradeData.setTotalPlayerTradeValue(0);
                continue;
            }
            if (submarketId.equals("ssp_cabalmarket")) continue;
            if (submarketId.equals("uw_cabalmarket")) continue;

            submarket.setFaction(newOwner);
        }

        market.reapplyConditions();
        market.reapplyIndustries();

        // prompt player to name faction if needed
        if (newOwnerId.equals(Factions.PLAYER) && !Misc.isPlayerFactionSetUp()) {
            //Global.getSector().getCampaignUI().showPlayerFactionConfigDialog();
            Global.getSector().addTransientScript(new PlayerFactionSetupNag());
        }

        // intel report
        List<String> factionsToNotify = new ArrayList<>();

        MarketTransferIntel intel = new MarketTransferIntel(market, oldOwnerId, newOwnerId, true, false,
                factionsToNotify, 0, 0);
        NexUtils.addExpiringIntel(intel);

        DiplomacyManager.notifyMarketCaptured(market, oldOwner, newOwner);

        int marketsRemaining = NexUtilsFaction.getFactionMarkets(oldOwner.getId(), true).size();
        if (marketsRemaining == 0) SectorManager.factionEliminated(newOwner, oldOwner, market);

        NexUtilsMarket.reportMarketTransferred(market, newOwner, oldOwner,
                false, true, factionsToNotify, 0);


    }
}
