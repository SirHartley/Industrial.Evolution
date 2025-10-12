package indevo.exploration.meteor.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.misc.FleetLogIntel;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.exploration.meteor.MeteorSwarmManager;

import java.awt.*;
import java.util.Set;

public class MeteorShowerSpawningLocationIntel extends MeteorShowerLocationIntel {

    public boolean spawnedSwarm = false;

    public MeteorShowerSpawningLocationIntel(LocationAPI loc, float intensity, MeteorSwarmManager.MeteroidShowerType type, int days) {
        super(loc, intensity, type, days);
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        if (days <= 0 && !spawnedSwarm) {
            MeteorSwarmManager.getInstance().spawnShower(loc, intensity, type);
            spawnedSwarm = true;
        }
    }
}
