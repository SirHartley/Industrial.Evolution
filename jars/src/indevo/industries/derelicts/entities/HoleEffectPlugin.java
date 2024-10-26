package indevo.industries.derelicts.entities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SoundAPI;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import org.lwjgl.util.vector.Vector2f;

/**
 * replaced by WarpEffectAnimationScript
 */

@Deprecated
public class HoleEffectPlugin extends BaseCustomEntityPlugin {

    @Deprecated
    transient private SpriteAPI ringSprite1;
    @Deprecated
    transient private SpriteAPI ringSprite2;

    transient private SpriteAPI holeSprite;

    transient private SoundAPI initialSound;

    private boolean isDone = false;

    private float passed = 0f;
    private float passedStamp = 0f;
    private int phase = 0;

    private SectorEntityToken target;
    private float oldRadius;

    private float holeSize = 0f;
    private float holeAlpha = 0f;
    private float rotation = 0f;

    private Vector2f loc;
    private Vector2f vel;

    public void init(SectorEntityToken entity, Object pluginParams) {
        super.init(entity, pluginParams);

        this.target = (SectorEntityToken) pluginParams;
        this.oldRadius = target.getRadius();
        this.loc = target.getLocation();
        this.vel = target.getVelocity();

        readResolve();
    }

    Object readResolve() {
        holeSprite = Global.getSettings().getSprite("IndEvo", "rift_hole");
        return this;
    }

    public void advance(float amount) {
        if (isDone) return;

        passed += 0.0083f;

        if (!Global.getSector().isPaused() && initialSound == null) {
            initialSound = Global.getSoundPlayer().playSound("IndEvo_warp_start", 0.7f, 2f, loc, vel);
        } else if (Global.getSector().isPaused() && initialSound != null) initialSound.stop();

        if (phase < 5) Global.getSoundPlayer().playLoop("IndEvo_warp_loop", entity, 1f, 1.5f, loc, vel);

        if (Global.getSector().getPlayerFleet().getContainingLocation() != entity.getContainingLocation()) {
            if (Global.getSettings().isDevMode())
                Global.getSector().getCampaignUI().addMessage("animation abort - moving planet, player left system");
            WarpEffectPlugin.movePlanet(target);
            phase = 6;
        }

        switch (phase) {
            case 1:
                //shrink the planet
                PlanetAPI planet = (PlanetAPI) target;
                planet.setRadius(Math.max(0.01f, planet.getRadius() - (oldRadius * 0.025f)));
                break;
            case 6:
                isDone = true;
                entity.getStarSystem().removeEntity(entity);
                break;
        }
    }

    public float getRenderRange() {
        return target.getRadius() + 10000f;
    }

    private boolean spawnedFlash = false;

    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (isDone) return;

        float targetRadius = oldRadius + 100;
        PlanetAPI planet = (PlanetAPI) target;

        switch (phase) {
            case 0:
                //spawn the hole, fade in, rotate and enlarge it until radius x4?
                holeSize = (float) (targetRadius + Math.pow(passed * 100, 1.3f));
                holeAlpha = Math.min(1, passed * 10);
                rotation = -passed * 50;

                if (holeSize >= targetRadius * 5) {
                    holeSize = targetRadius * 5;

                    phase++;
                }

                renderHoleSprite(loc.x, loc.y);
                break;
            case 1:
                //wait until planet is very small
                rotation = -passed * 50;
                renderHoleSprite(loc.x, loc.y);

                if (planet.getRadius() < 0.011f) phase++;
                break;
            case 2:
                rotation = -passed * 50;
                renderHoleSprite(loc.x, loc.y);

                passedStamp = passed;
                phase++;
                break;
            case 3:
                //shrink the hole again
                holeSize = (float) ((targetRadius * 5) - Math.pow((passed - passedStamp) * 100, 2.2f));
                rotation = -passed * 50;

                renderHoleSprite(loc.x, loc.y);

                if (!spawnedFlash && holeSize < targetRadius * 2.5f) {
                    target.getStarSystem().addCustomEntity("IndEvo_flash", null, "IndEvo_WarpEffect", null, target);
                    spawnedFlash = true;
                }

                if (holeSize < targetRadius) phase++;
                break;
            case 4:
                passedStamp = passed;
                phase++;
                break;
            case 5:
                float tp = passed - passedStamp;
                Global.getSoundPlayer().playLoop("IndEvo_warp_loop", entity, 0.5f, (1f - (tp * 2f)), loc, vel);

                if (tp >= 0.50f) phase++;
                break;
        }
    }

    /*public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        Vector2f loc = target.getLocation();
        float targetRadius = 255f;
        float magicBullshitNumber = (passed*40);

        switch (phase) {
            case 0:
                //Spawn the first ring, slowly increment it towards the center with fade in
                ringSize1 = (float) ((targetRadius * 6f) - Math.pow(1.3, magicBullshitNumber));
                ringAlpha1 += 0.02;

                renderRingSprite1(loc.x, loc.y);

                if (passed > 0.6f) phase++;
                break;
            case 1:
                ringSize1 = (float) Math.max(0, (targetRadius * 6f) - Math.pow(1.3, magicBullshitNumber));
                ringAlpha1 += 0.02;

                //spawn the additional second ring, same handling as 1
                ringSize2 = (float) Math.max(0, (targetRadius * 8f) - Math.pow(1.3, (magicBullshitNumber - 0.6)));
                ringAlpha2 += 0.3;

                renderRingSprite1(loc.x, loc.y);
                renderRingSprite2(loc.x, loc.y);
                if (ringSize2 < target.getRadius()) phase++;
                break;
            case 2:
                target.getStarSystem().addCustomEntity("IndEvo_flash", null, "IndEvo_WarpEffect", null, target);
                phase++;
                break;
        }
    }*/

    private void renderHoleSprite(float x, float y) {
        holeSprite.setSize(holeSize, holeSize);
        holeSprite.setAlphaMult(Math.min(holeAlpha, 1));
        holeSprite.setAngle(rotation);
        holeSprite.setNormalBlend();
        holeSprite.renderAtCenter(x, y);

    }
}



