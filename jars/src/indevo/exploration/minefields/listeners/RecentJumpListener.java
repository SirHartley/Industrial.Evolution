package indevo.exploration.minefields.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.campaign.CampaignClock;

import static indevo.exploration.minefields.MineBeltTerrainPlugin.RECENT_JUMP_KEY;
import static indevo.exploration.minefields.MineBeltTerrainPlugin.RECENT_JUMP_TIMEOUT_SECONDS;

public class RecentJumpListener extends BaseCampaignEventListener {

    public static void register() {
        Global.getSector().addTransientListener(new RecentJumpListener(false));
    }

    public RecentJumpListener(boolean permaRegister) {
        super(permaRegister);
    }

    @Override
    public void reportFleetJumped(CampaignFleetAPI fleet, SectorEntityToken from, JumpPointAPI.JumpDestination to) {
        super.reportFleetJumped(fleet, from, to);

        fleet.getMemoryWithoutUpdate().set(RECENT_JUMP_KEY, true, (1 / CampaignClock.SECONDS_PER_GAME_DAY) * RECENT_JUMP_TIMEOUT_SECONDS);
    }
}
