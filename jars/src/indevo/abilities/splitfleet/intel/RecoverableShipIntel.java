package indevo.abilities.splitfleet.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.misc.FleetLogIntel;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static indevo.abilities.splitfleet.fleetManagement.CombatAndDerelictionScript.ORBIT_FOCUS_ORBITING_TOKEN_LIST;
import static indevo.abilities.splitfleet.fleetManagement.CombatAndDerelictionScript.SHIP_VARIANT_KEY;

public class RecoverableShipIntel extends FleetLogIntel {

    protected SectorEntityToken token;

    public RecoverableShipIntel(SectorEntityToken token) {
        this.token = token;
    }

    private List<SectorEntityToken> getTokenList() {
        return (List<SectorEntityToken>) token.getMemoryWithoutUpdate().get(ORBIT_FOCUS_ORBITING_TOKEN_LIST);
    }

    private ShipVariantAPI getVariant(SectorEntityToken t) {
        return (ShipVariantAPI) t.getMemoryWithoutUpdate().get(SHIP_VARIANT_KEY);
    }

    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, Color tc, float initPad) {
        Color h = Misc.getHighlightColor();
        Color g = Misc.getGrayColor();
        float pad = 3f;
        float opad = 10f;

        bullet(info);
        info.addPara(getTokenList().size() + " Hulls in Orbit", opad);
        unindent(info);
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        Color h = Misc.getHighlightColor();
        Color g = Misc.getGrayColor();
        Color tc = Misc.getTextColor();
        float pad = 3f;
        float small = 3f;
        float opad = 10f;

        info.addPara("A detachment has lost ships to a hostile fleet. They can be recovered at this location.", opad);
        info.addSectionHeading("Recoverable hulls:", Alignment.MID, opad);
        for (SectorEntityToken t : getTokenList()) {
            if (t.isAlive() || !t.isExpired() || Misc.getSalvageSpecial(t) != null)
                info.addPara(BULLET + getVariant(t).getHullSpec().getNameWithDesignationWithDashClass(), opad);
        }

        addBulletPoints(info, ListInfoMode.IN_DESC);
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        List<SectorEntityToken> toRemove = new ArrayList<>();

        for (SectorEntityToken t : getTokenList())
            if (t.isExpired() || !t.isAlive() || Misc.getSalvageSpecial(t) == null) toRemove.add(t);
        if (!toRemove.isEmpty()) getTokenList().removeAll(toRemove);
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = new LinkedHashSet<String>();
        if (isImportant()) {
            tags.add(Tags.INTEL_IMPORTANT);
        }
        if (isNew()) {
            tags.add(Tags.INTEL_NEW);
        }
        if (map != null) {
            SectorEntityToken loc = getMapLocation(map);
            if (loc != null) {
                float max = Global.getSettings().getFloat("maxRelayRangeInHyperspace");
                float dist = Misc.getDistanceLY(loc.getLocationInHyperspace(), Global.getSector().getPlayerFleet().getLocationInHyperspace());
                if (dist <= max) {
                    tags.add(Tags.INTEL_LOCAL);
                }
            }
        }

        tags.add("Detachments");

        return tags;
    }

    public String getSortString() {
        return "Detachments";
    }

    public String getName() {
        return "Derelict Hulls";
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        return token;
    }

    @Override
    public boolean shouldRemoveIntel() {
        return getTokenList().isEmpty();
    }
}
