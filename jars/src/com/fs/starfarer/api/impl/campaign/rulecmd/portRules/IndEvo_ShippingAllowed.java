package com.fs.starfarer.api.impl.campaign.rulecmd.portRules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.IndEvo_PrivatePort;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
@Deprecated
public class IndEvo_ShippingAllowed extends BaseCommandPlugin {

    public static final Logger log = Global.getLogger(IndEvo_ShippingAllowed.class);
    boolean debug = false;

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        debug = Global.getSettings().isDevMode();
        //check if there are two different markets anywhere that meet requirements

        List<MarketAPI> eligibleFromMarkets = new ArrayList<>();
        List<MarketAPI> eligibleToMarkets = new ArrayList<>();

        IndEvo_PrivatePort.ShippingContainer tempCon = new IndEvo_PrivatePort.ShippingContainer();

        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (m.isHidden() || !m.isInEconomy() || m.getStarSystem() == null) continue;

            for (SubmarketAPI sub : m.getSubmarketsCopy()) {
                CargoAPI cargo = sub.getCargoNullOk();

                if (sub.getPlugin().isFreeTransfer()
                        && cargo != null
                        && (!cargo.isEmpty() || !cargo.getMothballedShips().getMembersListCopy().isEmpty())
                        && !sub.getSpecId().equals(Submarkets.LOCAL_RESOURCES)) {

                    eligibleFromMarkets.add(m);
                    break;
                }
            }

            for (SubmarketAPI sub : m.getSubmarketsCopy()) {
                //set the temporary container to the submarkets the origin one had, and check if there are any target markets
                tempCon.setOriginMarket(m);
                tempCon.setOriginSubmarketId(sub.getSpecId());

                List<MarketAPI> mList = IndEvo_CreateShippingSelectionList.getEligibleMarketList(tempCon, IndEvo_ShippingVariables.actionTypes.TARGET_MARKET);

                if (mList.size() > 0) {
                    eligibleToMarkets.addAll(mList);
                    break;
                }
            }
        }

        if (debug)
            log.info("Eligible shipping markets : TO " + eligibleToMarkets.size() + " | FROM:" + eligibleToMarkets.size());

        for (MarketAPI fm : eligibleFromMarkets) {
            for (MarketAPI tm : eligibleToMarkets) {
                if (fm != tm) return true;
            }
        }

        return false;
    }
}
