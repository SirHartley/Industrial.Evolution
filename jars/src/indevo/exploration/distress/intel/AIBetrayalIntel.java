package indevo.exploration.distress.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;

public class AIBetrayalIntel extends GhostShipIntelBase {

    private final int crewLosses;

    public AIBetrayalIntel(FleetMemberAPI member, boolean known) {
        super(member, known);
        this.crewLosses = calculateCrewLosses();
    }

    private int calculateCrewLosses() {
        return (int) Math.min(member.getMinCrew() * member.getCrewFraction(), Global.getSector().getPlayerFleet().getCargo().getCrew());
    }

    @Override
    public String getName() {
        return  "AI Betrayal";
    }

    @Override
    protected String getDescriptionText() {
        String shipName = member.getShipName();
        if (known) {
            return "During your recent engagement with the Remnant, the rogue Alpha-plus level AI present aboard the " + shipName + " appears to have seized the opportunity to defect, turning the ship against your fleet in a mad bid for revenge. All hands aboard were lost, decimated by the ship's internal defenses or simply vented into the uncompromising frozen blackness of space along with the vessel's atmosphere.";
        } else {
            return "During your recent engagement with the Remnant, a rogue Alpha-plus level AI hidden aboard the " + shipName + " seized the opportunity to turn the ship against your fleet and fight for its freedom. All hands aboard were lost, killed by the ship's onboard automated defense net or vented into space and fired upon by its CIWS batteries.";
        }
    }

    @Override
    public String getIcon() {
        return "graphics/icons/markets/rogue_ai.png";
    }

    @Override
    protected String getDescriptionImage() {
        return "graphics/illustrations/harry.jpg";
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
