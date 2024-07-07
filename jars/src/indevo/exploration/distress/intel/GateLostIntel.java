package indevo.exploration.distress.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;

public class GateLostIntel extends GhostShipIntelBase {

    private final int crewLosses;

    public GateLostIntel(FleetMemberAPI member, boolean known) {
        super(member, known);
        this.crewLosses = calculateCrewLosses();
    }

    private int calculateCrewLosses() {
        return (int) Math.min(member.getMinCrew() * member.getCrewFraction(), Global.getSector().getPlayerFleet().getCargo().getCrew());
    }

    @Override
    public String getName() {
        return "Anomalous Gate Reactivation";
    }

    @Override
    public String getIcon() {
        return "graphics/icons/abilities/direct_jump.png";
    }

    @Override
    protected String getDescriptionText() {
        String shipName = member.getShipName();
        if (known) {
            return "You knew the " + shipName + " was... wrong, in ways that you had trouble describing - but despite this, whether due to curiosity, callousness, or misplaced hope, you ordered it through the dead gate. Whatever your reasoning, the result was clear: The ring was somehow awoken, and consumed the ship in a flash of brilliant white light. When your vision cleared, the " + shipName + " had vanished, nothing left of it but the grisly remains of its former crew floating in the middle of the dead, silent gate.";
        } else {
            return "Ignoring the (surely exaggerated, and anyway unconfirmed) tales of anomalous happenings aboard the " + shipName + ", you ordered it through the dead gate. Unfortunately for your crew, it appears the reports were accurate - The ring was somehow awoken, and consumed the ship in a flash of brilliant white light. When your vision cleared, the " + shipName + " had vanished, nothing left of it but the grisly remains of its former crew floating in the middle of the dead, silent gate.";
        }
    }

    @Override
    protected String getDescriptionImage() {
        return "graphics/illustrations/dead_gate.jpg";
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
