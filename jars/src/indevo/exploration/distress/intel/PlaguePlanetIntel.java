package indevo.exploration.distress.intel;

import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;

public class PlaguePlanetIntel extends GhostShipIntelBase {

    private final int crewLosses;

    public PlaguePlanetIntel(FleetMemberAPI member, boolean known) {
        super("plague_planet", member, known);
        this.crewLosses = calculateCrewLosses();
    }

    private int calculateCrewLosses() {
        return (int) Math.min(member.getMinCrew() * member.getCrewFraction(), Global.getSector().getPlayerFleet().getCargo().getCrew());
    }

    @Override
    public String getName() {
        return "Plague on Planet";
    }

    @Override
    public String getIcon() {
        return "graphics/icons/markets/death.png";
    }

    @Override
    protected String getDescriptionText() {
        String shipName = member.getShipName();
        if (known) {
            return "The " + shipName + " became infected while on the planet's surface. The plague spread rapidly, leaving no survivors. The ship is now a ghostly derelict, drifting in space.";
        } else {
            return "A plague broke out on the " + shipName + " after an expedition to the planet's surface. The infection spread uncontrollably, killing all aboard. The ship now floats in space, a grim reminder of the dangers of exploration.";
        }
    }

    @Override
    protected String getDescriptionImage() {
        return "graphics/illustrations/plague_planet.jpg";
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
