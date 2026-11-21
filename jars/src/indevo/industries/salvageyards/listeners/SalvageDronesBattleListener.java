package indevo.industries.salvageyards.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.industries.salvageyards.industry.SalvageYards;
import org.lwjgl.util.vector.Vector2f;

import static indevo.items.installable.SpecialItemEffectsRepo.SALVAGE_DRONES_RANGE_LY;

public class SalvageDronesBattleListener extends BaseCampaignEventListener {

    public SalvageDronesBattleListener() {
        super(false);
    }

    public static void register() {
        Global.getSector().addTransientListener(new SalvageDronesBattleListener());
    }

    @Override
    public void reportBattleOccurred(CampaignFleetAPI primaryWinner, BattleAPI battle) {
        if (primaryWinner == null || battle == null) return;

        LocationAPI location = primaryWinner.getContainingLocation();
        Vector2f locInHyper = primaryWinner.getLocationInHyperspace();

        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!market.hasIndustry(Ids.SCRAPYARD)) continue;

            SalvageYards yards = (SalvageYards) market.getIndustry(Ids.SCRAPYARD);
            if (!yards.hasSalvageDrones()) continue;

            boolean sameSystem = market.getContainingLocation() == location;
            if (!sameSystem && Misc.getDistanceLY(locInHyper, market.getLocationInHyperspace()) > SALVAGE_DRONES_RANGE_LY) continue;

            yards.reportBattleInDroneRange(battle, sameSystem);
        }
    }
}
