package indevo.exploration.crucible;

import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;

public class CrucibleMoteEntityPlugin extends BaseCustomEntityPlugin {

    SectorEntityToken target;

    @Override
    public void advance(float amount) {
        super.advance(amount);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        super.render(layer, viewport);
    }
}
