package indevo.industries.changeling.listener;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_MarketCMD;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.InvasionRound;
import exerelin.utilities.InvasionListener;
import indevo.industries.changeling.industry.population.HelldiversSubIndustry;
import indevo.industries.changeling.conditions.GovernmentCondition;
import indevo.industries.changeling.industry.SubIndustryAPI;

import java.util.List;

public class ManagedDemocracyNexerelinListenerPlugin implements InvasionListener {

    public static void register(){
        Global.getSector().getListenerManager().addListener(new ManagedDemocracyNexerelinListenerPlugin(), true);
    }

    @Override
    public void reportInvadeLoot(InteractionDialogAPI dialog, MarketAPI market, Nex_MarketCMD.TempDataInvasion actionData, CargoAPI cargo) {

    }

    @Override
    public void reportInvasionRound(InvasionRound.InvasionRoundResult result, CampaignFleetAPI fleet, MarketAPI defender, float atkStr, float defStr) {

    }

    @Override
    public void reportInvasionFinished(CampaignFleetAPI fleet, FactionAPI attackerFaction, MarketAPI market, float numRounds, boolean success) {

    }

    @Override
    public void reportMarketTransfered(MarketAPI market, FactionAPI newOwner, FactionAPI oldOwner, boolean playerInvolved, boolean isCapture, List<String> factionsToNotify, float repChangeStrength) {
        if (playerInvolved && isCapture && newOwner == Global.getSector().getPlayerFaction()){
            for (MarketAPI pMarket : Misc.getPlayerMarkets(true)){
                SubIndustryAPI gov = GovernmentCondition.getGovernment(pMarket);
                if (gov instanceof HelldiversSubIndustry) ((HelldiversSubIndustry) gov).reportNexerelinInvasionSuccess(market);
            }
        }
    }
}
