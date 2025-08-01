package indevo.exploration.meteor.intel;

import com.fs.graphics.M;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.CargoPodsEntityPlugin;
import com.fs.starfarer.api.impl.campaign.intel.misc.FleetLogIntel;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.exploration.meteor.MeteorSwarmManager;

import java.awt.*;
import java.util.Set;

public class MeteorShowerLocationIntel extends FleetLogIntel {
    protected LocationAPI loc;
    protected float intensity;
    protected MeteorSwarmManager.MeteroidShowerType type;
    protected float days;
    private String dateString;

    public boolean spawnedSwarm = false;

    public MeteorShowerLocationIntel(LocationAPI loc, float intensity, MeteorSwarmManager.MeteroidShowerType type, int days) {
        this.loc = loc;
        this.intensity = intensity;
        this.type = type;
        this.days = days;

        Global.getSector().addScript(this);
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        days -= Global.getSector().getClock().convertToDays(amount);

        if (days <= 0 && !spawnedSwarm) {
            MeteorSwarmManager.getInstance().spawnShower(loc, intensity, type);
            dateString = Global.getSector().getClock().getDateString();
            spawnedSwarm = true;

            endAfterDelay(20);
        }

        if (days <= 0) days = 0;
    }

    @Override
    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, Color tc, float initPad) {
        float pad = 3f;

        bullet(info);

        String sizeHint = getSizeHintFromIntensity(intensity);
        info.addPara("Intensity: " + sizeHint + ".", initPad);

        if (type != MeteorSwarmManager.MeteroidShowerType.ASTEROID) {
            info.addPara("Unusual composition.", pad);
        }

        unindent(info);
    }

    private String getSizeHintFromIntensity(float intensity) {
        if (intensity < 1.5f) return "modest";
        if (intensity < 2.5f) return "significant";
        return "extreme";
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        Color tc = Misc.getTextColor();
        float opad = 10f;
        float spad = 10f;

        info.addImage("graphics/illustrations/free_orbit.jpg", width, opad);

        if (days > 0) info.addPara("A meteor storm of %s is scheduled to pass through " + loc.getName() + " in %s",
                opad, Misc.getHighlightColor(),
                getSizeHintFromIntensity(intensity) + " size",
                getDays(days) + " " + getDaysString(Math.round(days)));

        else info.addPara("A meteor storm of %s entered " + loc.getName()+ " on %s",
                opad, Misc.getHighlightColor() ,
                getDays(days) + " " + getDaysString(Math.round(days)),
                dateString);

        info.addPara("Meteor storms are known to catch salvage and transport it over long distances.", spad);
        if (type != MeteorSwarmManager.MeteroidShowerType.ASTEROID) info.addPara("This one emits an unusual signature.", spad);

        addLogTimestamp(info, tc, opad);
        addDeleteButton(info, width);
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName("IndEvo", "meteor_storm");
    }

    @Override
    public String getSortString() {
        return super.getSortString();
    }

    @Override
    public String getName() {
        return "Meteor Storm";
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        return ((StarSystemAPI) loc).getCenter();
    }
}
