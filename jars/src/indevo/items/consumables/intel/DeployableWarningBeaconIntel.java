package indevo.items.consumables.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.intel.misc.WarningBeaconIntel;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;

public class DeployableWarningBeaconIntel extends WarningBeaconIntel {

    public String message;

    public DeployableWarningBeaconIntel(SectorEntityToken beacon, String message) {
        super(beacon);

        this.message = message;
    }

    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {

        Color p = Misc.getDarkPlayerColor();
        Color h = Misc.getHighlightColor();
        Color g = Misc.getGrayColor();
        float pad = 3f;
        float opad = 10f;

        float initPad = pad;
        if (mode == ListInfoMode.IN_DESC) initPad = opad;

        Color tc = getBulletColorForMode(mode);

        bullet(info);
        info.addPara("Danger level: %s", initPad, tc, p, "custom");
        unindent(info);
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName("IndEvo", "beacon_custom");
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        super.createSmallDescription(info, width, height);

        float opad = 10f;

        info.addPara("It is %s the following message:", opad, Misc.getHighlightColor(), "broadcasting");
        info.addPara((message.isEmpty() ? "Indecipherable static noise. Whatever data it was supposed to transmit has degraded beyond saving.": message), opad);
    }
}
