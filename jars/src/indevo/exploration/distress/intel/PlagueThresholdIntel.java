package indevo.exploration.distress.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;

public class PlagueThresholdIntel extends GhostShipIntelBase {

    private final int crewLosses;

    public PlagueThresholdIntel(FleetMemberAPI member, boolean known) {
        super(member, known);
        this.crewLosses = calculateCrewLosses();
    }

    private int calculateCrewLosses() {
        return (int) Math.min(member.getMinCrew() * member.getCrewFraction(), Global.getSector().getPlayerFleet().getCargo().getCrew());
    }

    @Override
    public String getName() {
        return "Plague Threshold";
    }

    @Override
    public String getIcon() {
        return "graphics/icons/markets/inimical_biosphere.png";
    }

    @Override
    protected String getDescriptionText() {
        String shipName = member.getShipName();
        if (known) {
            return "The " + shipName + " was already teetering on the edge of becoming a vessel of plague, the quarantine protocols were barely containing the virulent infections aboard. Despite your precautions, a massive outbreak occurred, and the vessel became a floating coffin, losing all hands.";
        } else {
            return "Despite your precautions, the infections aboard the " + shipName + " broke out uncontrollably, turning the ship into a vector of death. All hands were lost as the vessel became a floating coffin.";
        }
    }

    @Override
    protected String getDescriptionImage() {
        return "graphics/illustrations/plague_ship.jpg";
    }

    @Override
    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
        Color h = Misc.getHighlightColor();
        Color g = Misc.getGrayColor();
        float pad = 3f;
        float opad = 10f;
        float initPad = pad;

        if (mode == ListInfoMode.IN_DESC) {
            initPad = opad;
        }

        Color tc = getBulletColorForMode(mode);

        bullet(info);

        String shipName = member.getShipName() + ", " + member.getHullSpec().getNameWithDesignationWithDashClass();

        info.addPara(shipName + " lost", initPad, tc, h, shipName);
        info.addPara(crewLosses + " crew lost", initPad, tc, h, "" + crewLosses);

        unindent(info);
    }
}
