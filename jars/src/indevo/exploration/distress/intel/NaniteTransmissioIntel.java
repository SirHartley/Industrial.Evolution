package indevo.exploration.distress.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;

public class NaniteTransmissioIntel extends GhostShipIntelBase {

    private final int crewLosses;

    public NaniteTransmissioIntel(FleetMemberAPI member, boolean known) {
        super(member, known);
        this.crewLosses = calculateCrewLosses();
    }

    private int calculateCrewLosses() {
        return (int) Math.min(member.getMinCrew() * member.getCrewFraction(), Global.getSector().getPlayerFleet().getCargo().getCrew());
    }

    @Override
    public String getName() {
        return "Cannibal Attack";
    }

    @Override
    public String getIcon() {
        return "graphics/icons/markets/abandoned.png";
    }

    @Override
    protected String getDescriptionText() {
        String shipName = member.getShipName();
        if (known) {
            return "The " + shipName + " was attacked by a group of cannibals. The crew was brutally slaughtered, leaving the ship adrift.";
        } else {
            return "A group of cannibals attacked the " + shipName + ", killing the crew in a horrific massacre. The ship now floats lifeless in space.";
        }
    }

    @Override
    protected String getDescriptionImage() {
        return "graphics/illustrations/cannibal_attack.jpg";
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
