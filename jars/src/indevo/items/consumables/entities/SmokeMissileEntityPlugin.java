package indevo.items.consumables.entities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.ExplosionEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.artillery.entities.VariableExplosionEntityPlugin;

import java.awt.*;
import java.util.Random;

public class SmokeMissileEntityPlugin extends BaseMissileEntityPlugin {

    public static final float DISTANCE_TO_EXPLODE = 25f;

    @Override
    public boolean shouldExplode() {
        for (CampaignFleetAPI fleet : entity.getContainingLocation().getFleets()) if (Misc.getDistance(fleet, entity) < DISTANCE_TO_EXPLODE && fleet != source) return true;
        return false;
    }

    @Override
    public void onExplosion() {
        LocationAPI cl = entity.getContainingLocation();
        SmokeCloudEntityPlugin.SmokeCloudParams params = new SmokeCloudEntityPlugin.SmokeCloudParams(cl, entity.getLocation(), SmokeCloudEntityPlugin.DURATION_IN_DAYS * Global.getSector().getClock().getSecondsPerDay(), (float) (SmokeCloudEntityPlugin.BASE_RADIUS * 0.9 + 0.1 * (new Random().nextFloat())));
        SectorEntityToken t = cl.addCustomEntity(Misc.genUID(), null, "IndEvo_SmokeCloud", null, params);
        t.setLocation(entity.getLocation().x, entity.getLocation().y);
    }

    @Override
    public SpriteAPI getMissileSprite() {
        return Global.getSettings().getSprite("IndEvo", "IndEvo_consumable_missile_smoke");
    }

    @Override
    public Color getTrailColour() {
        return new Color(240, 20,20);
    }
}
