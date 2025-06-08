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
import indevo.items.consumables.fleet.MissileMemFlags;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class ExplosiveMissileEntityPlugin extends BaseMissileEntityPlugin {

    public static final float DISTANCE_TO_EXPLODE = 10f;
    public static final float EXPLOSION_SIZE = 250f;
    public static final String EXPLOSION_SOUND_ID = "IndEvo_mortar_hit";

    @Override
    public boolean shouldExplode() {
        for (CampaignFleetAPI fleet : entity.getContainingLocation().getFleets())
            if (Misc.getDistance(fleet, entity) < DISTANCE_TO_EXPLODE + fleet.getRadius() && fleet != source) return true;
        return false;
    }

    @Override
    public void onExplosion() {
        LocationAPI cl = entity.getContainingLocation();

        Color color = new Color(200, 110,20);
        VariableExplosionEntityPlugin.VariableExplosionParams params =
                new VariableExplosionEntityPlugin.VariableExplosionParams(
                        EXPLOSION_SOUND_ID,
                        true,
                        1f,
                        color,
                        cl,
                        entity.getLocation(),
                        EXPLOSION_SIZE,
                        0.65f);

        params.damage = ExplosionEntityPlugin.ExplosionFleetDamage.LOW;

        SectorEntityToken explosion = cl.addCustomEntity(Misc.genUID(), "Explosion",
                "IndEvo_VariableExplosion", Factions.NEUTRAL, params);

        explosion.setLocation(entity.getLocation().x, entity.getLocation().y);

        for (SectorEntityToken t : cl.getAllEntities()){
            if (Misc.getDistance(entity.getLocation(), t.getLocation()) < EXPLOSION_SIZE) t.getMemoryWithoutUpdate().set(MissileMemFlags.MEM_CAUGHT_BY_MISSILE, new Vector2f(entity.getLocation()), 1f);
        }
    }

    @Override
    public SpriteAPI getMissileSprite() {
        return Global.getSettings().getSprite("IndEvo", "IndEvo_consumable_missile_explosive");
    }

    @Override
    public Color getTrailColour() {
        return new Color(255, 180,20);
    }
    //IndEvo_consumable_missile_explosive
}
