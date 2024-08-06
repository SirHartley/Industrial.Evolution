package indevo.dialogue.research.scripts;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;

public interface RefitTabListener {
    void reportRefitOpened(CampaignFleetAPI fleet);

    void reportRefitClosed(CampaignFleetAPI fleet);
}
