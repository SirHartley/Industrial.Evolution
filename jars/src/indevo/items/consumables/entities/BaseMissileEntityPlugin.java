package indevo.items.consumables.entities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.util.Misc;
import indevo.utils.trails.MagicCampaignTrailPlugin;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public abstract class BaseMissileEntityPlugin extends BaseCustomEntityPlugin {
    //travels in a wiggly line from spawn point for a certain duration
    //checks surroundings for enemy, if found, home in with limited turn rate
    //splode into slow field and interdict if close enough

    public static class ConsumableMissileParams {
        SectorEntityToken source;
        public float angle;
        public float duration;
        public float speed;
        public Vector2f optionalTarget;

        public ConsumableMissileParams(SectorEntityToken source, float angle, float duration, float speed, Vector2f optionalTarget) {
            this.source = source;
            this.angle = angle;
            this.duration = duration;
            this.speed = speed;
            this.optionalTarget = optionalTarget;
        }
    }

    public abstract boolean shouldExplode();
    public abstract void onExplosion();
    public abstract SpriteAPI getMissileSprite();
    public abstract Color getTrailColour();

    //trail
    public static final float TRAIL_TIME = 0.4f;

    //base
    transient private SpriteAPI missileSprite;

    private float timePassedSeconds = 0f;
    public Vector2f optionalTarget;
    public float duration;
    public float angle;
    public float velocity;

    public float trailID;
    public boolean finishing = false;
    public boolean optionalTargetReached = false;
    public SectorEntityToken source;

  /*  public static void spawn(ConsumableMissileParams params, LocationAPI loc) {
        SectorEntityToken t = params.loc.addCustomEntity(Misc.genUID(), null, "IndEvo_missileSubmunition", params.faction.getId(), params);
        t.setLocation(params.origin.x, params.origin.y);
    }*/

    public void init(SectorEntityToken entity, Object pluginParams) {
        super.init(entity, pluginParams);
        entity.setDetectionRangeDetailsOverrideMult(0.75f);

        if (pluginParams instanceof ConsumableMissileParams) {
            this.source = ((ConsumableMissileParams) pluginParams).source;
            this.angle = ((ConsumableMissileParams) pluginParams).angle;
            this.duration = ((ConsumableMissileParams) pluginParams).duration;
            this.velocity = ((ConsumableMissileParams) pluginParams).speed;
            this.optionalTarget = ((ConsumableMissileParams) pluginParams).optionalTarget;

            trailID = MagicCampaignTrailPlugin.getUniqueID();
        }

        readResolve();
    }

    public Object readResolve() {
        missileSprite = getMissileSprite();
        return this;
    }

    public void advance(float amount) {
        timePassedSeconds += amount;

        if (!finishing) {
            advanceProjectile(amount);

            if (shouldExplode() || timePassedSeconds > duration || optionalTargetReached){
                onExplosion();
                Misc.fadeAndExpire(entity, 0.1f);
                finishing = true;
            }

            addTrailToProj();
        }
    }

    public void advanceProjectile(float amount) {
        float dist = velocity * amount;

        float remainingDistance = optionalTarget != null ? Misc.getDistance(entity.getLocation(), optionalTarget) : Float.MAX_VALUE;
        if (dist >= remainingDistance) optionalTargetReached = true;

        Vector2f nextPos = MathUtils.getPointOnCircumference(entity.getLocation(), dist, angle);
        entity.setLocation(nextPos.x, nextPos.y);
    }

    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        renderProjectile(viewport);
    }

    public void renderProjectile(ViewportAPI viewport) {
        if (missileSprite == null) missileSprite = getMissileSprite();

        float alphaMult = viewport.getAlphaMult();
        alphaMult *= entity.getSensorFaderBrightness();
        alphaMult *= entity.getSensorContactFaderBrightness();
        if (alphaMult <= 0) return;

        missileSprite.setNormalBlend();
        missileSprite.setAngle(entity.getFacing() - 90f);
        missileSprite.setSize(12, 26);
        missileSprite.setAlphaMult(alphaMult);
        missileSprite.renderAtCenter(entity.getLocation().x, entity.getLocation().y);
    }

    //trail

    private void addTrailToProj() {
        MagicCampaignTrailPlugin.AddTrailMemberSimple(
                entity,
                trailID,
                Global.getSettings().getSprite("fx", "IndEvo_stream_core"),
                entity.getLocation(),
                0f,
                entity.getFacing(),
                12f,
                1f,
                getTrailColour(),
                0.9f,
                TRAIL_TIME,
                true,
                new Vector2f(0, 0));
    }

    public float getRenderRange() {
        return 9999999999999f;
    }
}
