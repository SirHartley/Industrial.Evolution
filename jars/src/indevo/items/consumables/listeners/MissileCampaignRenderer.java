package indevo.items.consumables.listeners;

import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;

public interface MissileCampaignRenderer extends LunaCampaignRenderingPlugin {
    void setDone();
    boolean isValidPosition();
}
