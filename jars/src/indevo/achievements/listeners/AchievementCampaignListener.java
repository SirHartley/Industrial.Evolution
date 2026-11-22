package indevo.achievements.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction;
import com.fs.starfarer.api.impl.campaign.DModManager;
import indevo.achievements.Achievements;
import indevo.exploration.fog.fleet.FogLurkerAssignmentAI;
import indevo.ids.Ids;

public class AchievementCampaignListener extends BaseCampaignEventListener {

    private AchievementCampaignListener() {
        super(false);
    }

    public static void register() {
        Global.getSector().addTransientListener(new AchievementCampaignListener());
    }

    @Override
    public void reportBattleOccurred(CampaignFleetAPI primaryWinner, BattleAPI battle) {
        if (!battle.isPlayerInvolved()) return;

        for (CampaignFleetAPI fleet : battle.getSnapshotBothSides()) {
            if (fleet.getMemoryWithoutUpdate().getBoolean(FogLurkerAssignmentAI.MEM_LURKER)) {
                Achievements.complete(Achievements.VISIBILITY_ZERO);
                return;
            }
        }
    }

    @Override
    public void reportPlayerMarketTransaction(PlayerMarketTransaction transaction) {
        if (transaction.getSubmarket() == null || !Ids.MUSEUM_SUBMARKET.equals(transaction.getSubmarket().getSpecId())) return;

        for (PlayerMarketTransaction.ShipSaleInfo info : transaction.getShipsSold()) {
            if (DModManager.getNumDMods(info.getMember().getVariant()) >= Achievements.MUSEUM_DMODS) {
                Achievements.complete(Achievements.MUSEUM);
                return;
            }
        }
    }
}
