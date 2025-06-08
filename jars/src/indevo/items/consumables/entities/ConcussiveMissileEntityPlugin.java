package indevo.items.consumables.entities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.ExplosionEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.terrain.ShoveFleetScript;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.artillery.entities.VariableExplosionEntityPlugin;
import indevo.items.consumables.fleet.MissileMemFlags;
import indevo.utils.helper.MiscIE;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.EnumSet;

public class ConcussiveMissileEntityPlugin extends BaseMissileEntityPlugin {

    public static final float DISTANCE_TO_EXPLODE = 25f;
    public static final float SHOVE_RANGE = 500f;
    public static final float SHOVE_STRENGTH = 2f;
    public static final String EXPLOSION_SOUND_ID = "kinetic_blaster_fire";
    public static final float EXPLOSION_VFX_RADIUS = 100f;

    @Override
    public boolean shouldExplode() {
        for (CampaignFleetAPI fleet : entity.getContainingLocation().getFleets()) if (Misc.getDistance(fleet, entity) < DISTANCE_TO_EXPLODE && fleet != source) return true;
        return false;
    }

    @Override
    public void onExplosion() {
        LocationAPI cl = entity.getContainingLocation();

        for (CampaignFleetAPI fleet : Misc.getNearbyFleets(entity, SHOVE_RANGE)){
            float shoveDir = Misc.getAngleInDegrees(entity.getLocation(), fleet.getLocation());
            float intensity = MathUtils.clamp((1 - Misc.getDistance(entity, fleet) / SHOVE_RANGE), 0,1) * SHOVE_STRENGTH;
            if (fleet.isPlayerFleet()) intensity /= 2f; //player gets to cheat

            fleet.addScript(new ShoveFleetScript(fleet, shoveDir, intensity));
        }

        for (SectorEntityToken t : cl.getAllEntities()){
            if (Misc.getDistance(entity.getLocation(), t.getLocation()) < SHOVE_RANGE) t.getMemoryWithoutUpdate().set(MissileMemFlags.MEM_CAUGHT_BY_MISSILE, new Vector2f(entity.getLocation()), 1f);
        }

        LunaCampaignRenderer.addRenderer(new LunaCampaignRenderingPlugin() {
            public float amt = 0f;
            public static final float ANIM_DUR = 0.3f;
            public transient SpriteAPI sprite;
            public Vector2f loc = new Vector2f(entity.getLocation());

            @Override
            public boolean isExpired() {
                return amt > ANIM_DUR;
            }

            @Override
            public void advance(float amount) {
                amt += amount;
            }

            @Override
            public EnumSet<CampaignEngineLayers> getActiveLayers() {
                return EnumSet.of(CampaignEngineLayers.ABOVE);
            }

            @Override
            public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
                if (sprite == null) sprite = Global.getSettings().getSprite("fx", "IndEvo_concussionExplosionFX");

                float fract = amt / ANIM_DUR;
                float radius = (SHOVE_RANGE + 200f) * fract;
                float alpha = MiscIE.smootherstep(1,0,fract);

                sprite.setAlphaMult(alpha);
                sprite.setWidth(radius);
                sprite.setHeight(radius);
                sprite.setColor(Color.lightGray);
                sprite.renderAtCenter(loc.x, loc.y);
            }
        });

        VariableExplosionEntityPlugin.VariableExplosionParams params =
                new VariableExplosionEntityPlugin.VariableExplosionParams(
                        EXPLOSION_SOUND_ID,
                        true,
                        1f,
                        getTrailColour(),
                        cl,
                        entity.getLocation(),
                        EXPLOSION_VFX_RADIUS,
                        0.65f);
        params.damage = ExplosionEntityPlugin.ExplosionFleetDamage.NONE;
        SectorEntityToken explosion = cl.addCustomEntity(Misc.genUID(), "Explosion", "IndEvo_VariableExplosion", Factions.NEUTRAL, params);
        explosion.setLocation(entity.getLocation().x, entity.getLocation().y);
    }

    @Override
    public SpriteAPI getMissileSprite() {
        return Global.getSettings().getSprite("IndEvo", "IndEvo_consumable_missile_concussive");
    }

    @Override
    public Color getTrailColour() {
        return new Color(20, 240,240);
    }
}
