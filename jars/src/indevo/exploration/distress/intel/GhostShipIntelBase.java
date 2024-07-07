package indevo.exploration.distress.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.intel.misc.FleetLogIntel;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;

import java.awt.*;

public abstract class GhostShipIntelBase extends FleetLogIntel {

    public static Logger log = Global.getLogger(GhostShipIntelBase.class);

    protected final FleetMemberAPI member;
    protected final boolean known;

    protected GhostShipIntelBase(FleetMemberAPI member, boolean known) {
        this.member = member;
        this.known = known;
    }

    @Override
    public String getSortString() {
        return "Ghost Ship - " + getName();
    }

    @Override
    public FactionAPI getFactionForUIColors() {
        return Global.getSector().getPlayerFaction();
    }

    @Override
    public String getSmallDescriptionTitle() {
        return getName();
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        Color h = Misc.getHighlightColor();
        Color g = Misc.getGrayColor();
        Color tc = Misc.getTextColor();
        float pad = 3f;
        float opad = 10f;

        String text = getDescriptionText();
        String image = getDescriptionImage();

        if (text != null) {
            info.addPara(text, opad, tc);
        }
        if (image != null) {
            info.addImage(image, width, height, opad);
        }
    }

    protected abstract String getDescriptionText();

    protected abstract String getDescriptionImage();

    protected abstract void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode);

    @Override
    public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
        Color c = getTitleColor(mode);
        float pad = 3f;
        float opad = 10f;
        Color h = Misc.getHighlightColor();

        info.addPara(getName(), c, 0f);

        addBulletPoints(info, mode);
    }
}
