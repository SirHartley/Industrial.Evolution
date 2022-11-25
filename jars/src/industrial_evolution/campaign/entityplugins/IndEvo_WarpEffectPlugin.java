package industrial_evolution.campaign.entityplugins;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SoundAPI;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import industrial_evolution.plugins.addOrRemovePlugins.IndEvo_PlanetMovingScript;
import org.lwjgl.util.vector.Vector2f;

public class IndEvo_WarpEffectPlugin extends BaseCustomEntityPlugin {

    transient private SpriteAPI sprite; // needs to be transient - can't save sprites
    transient private SoundAPI boomSound = null;

    private float passed = 0;
    private float total = 0;
    private int phase = 0;
    private int count = 0;
    private Vector2f loc;
    private Vector2f vel;

    private boolean isDone = false;

    private SectorEntityToken target;

    public void init(SectorEntityToken entity, Object pluginParams) {
        super.init(entity, pluginParams);
        this.target = (SectorEntityToken) pluginParams;
        this.loc = target.getLocation();
        this.vel = target.getVelocity();

        readResolve();
    }

    Object readResolve() {
        sprite = Global.getSettings().getSprite("IndEvo", "rift_flare");
        return this;
    }

    public void advance(float amount) {
        if (isDone) return;

        passed += 0.0083f;
        total += 0.0083f;

        //waiting period
        if (total > 0.29f) {
            passed = 0;
            total = 0;
            phase++;
        }

        if (!Global.getSector().isPaused() && boomSound == null) {
            boomSound = Global.getSoundPlayer().playSound("IndEvo_warp_end", 1f, 1f, loc, vel);
        } else if (Global.getSector().isPaused() && boomSound != null) boomSound.stop();

        //Global.getSector().getCampaignUI().addMessage("loc " + entity.getLocation().x + " " + entity.getLocation().y + " vel " + entity.getVelocity());

        if (!entity.isInCurrentLocation()) {
            if (Global.getSettings().isDevMode())
                Global.getSector().getCampaignUI().addMessage("animation abort - moving planet, player left system");
            movePlanet(target);
            phase = 4;
        }

        if (phase > 3) {
            isDone = true;
            entity.getStarSystem().removeEntity(entity);
        }
    }

    public float getRenderRange() {
        return target.getRadius() + 10000f;
    }

    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (isDone) return;

        float alphaMult = viewport.getAlphaMult();
        float radius = 130f;
        float size = 0;
        float flareMult = 10;

        switch (phase) {
            case 1:
                size = (float) (radius + Math.pow(passed * 100, 3.3));
                if (size >= radius * flareMult) {
                    size = radius * flareMult;

                    phase++;
                    passed = 0;
                }
                break;
            case 2:
                size = radius * flareMult;
                count++;

                if (count > 5) {
                    phase++;
                    passed = 0;
                }
                break;

            case 3:
                //boom goes the planet
                movePlanet(target);

                //and shrink the flare it again
                size = (float) Math.max((radius * flareMult - Math.pow(passed * 100, 2.5)), 0);
                alphaMult -= passed * 5;

                if (size == 0) {
                    phase++;
                    if (Global.getSettings().isDevMode()) Global.getSector().getCampaignUI().addMessage(total + "");
                }
                break;
        }

        sprite.setSize(size, size);
        sprite.setAlphaMult(alphaMult);
        sprite.setAdditiveBlend();
        sprite.renderAtCenter(loc.x, loc.y);
    }

    public static void movePlanet(SectorEntityToken target) {
        for (EveryFrameScript script : Global.getSector().getTransientScripts()) {
            if (script instanceof IndEvo_PlanetMovingScript) {
                IndEvo_PlanetMovingScript pm = (IndEvo_PlanetMovingScript) script;

                if (pm.getTarget().getId().equals(target.getId())) {
                    pm.advancePhase();
                }
            }
        }
    }
}



