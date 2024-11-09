package indevo.exploration.crucible.scripts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.exploration.crucible.entities.BaseCrucibleEntityPlugin;
import indevo.exploration.crucible.entities.YeetopultEntityPlugin;
import indevo.exploration.crucible.plugin.CrucibleSpawner;
import indevo.utils.ModPlugin;
import indevo.utils.animation.AnimationStage;
import indevo.utils.animation.BaseStagedAnimationScript;
import indevo.utils.animation.particles.RadialDustCloudEjectionRenderer;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class CrucibleMoveAnimationScript extends BaseStagedAnimationScript {

    public static final float ANIMATION_DELAY = 4.5f;
    public static final float BELL_DELAY = 3f;

    public static final float MOTE_TRAVEL_RADIUS_OFFSET = 100f;
    public static final float MOTE_TRAVEL_DURATION = 3f;

    public static final float FLARE_DUR = 0.53f;

    public List<SectorEntityToken> catapults;
    public SectorEntityToken crucible;

    public CrucibleMoveAnimationScript(final SectorEntityToken crucible) {
        this.crucible = crucible;
        this.catapults = new ArrayList<>();

        for (SectorEntityToken catapult : crucible.getContainingLocation().getEntitiesWithTag("IndEvo_yeetopult")) {
            if (catapult.hasTag("IndEvo_orbits_crucible")) {
                catapults.add(catapult);
            }
        }
    }

    @Override
    public void loadStages() {

        crucible.addTag(BaseCrucibleEntityPlugin.TAG_ANIMATION_PLAYING);

        addStage(new AnimationStage(0.1f, 0f) {
            @Override
            public void run(float amt) {

            }

            @Override
            public void runOnce() {
                crucible.addTag(BaseCrucibleEntityPlugin.TAG_ANIMATION_PLAYING);

                float volumeDistance = 1000f; //nothing at 1000f
                float distance = Misc.getDistance(Global.getSector().getPlayerFleet(), crucible);
                float fract = 1 - MathUtils.clamp(distance / volumeDistance, 0, 1);
                Global.getSoundPlayer().playSound("IndEvo_crucible_start", 0.7f, fract * 0.4f, crucible.getLocation(), new Vector2f(0f, 0f));

                LunaCampaignRenderer.addRenderer(new RadialDustCloudEjectionRenderer(
                        crucible,
                        0.55f * crucible.getRadius(),
                        0.6f,
                        75f,
                        10f,
                        80f,
                        3f,
                        1f));
            }
        });

        addStage(new AnimationStage(0.1f, BELL_DELAY) {
            @Override
            public void run(float amt) {

            }

            @Override
            public void runOnce() {
                float volumeDistance = 1000f; //nothing at 1000f
                float distance = Misc.getDistance(Global.getSector().getPlayerFleet(), crucible);
                float fract = 1 - MathUtils.clamp(distance / volumeDistance, 0, 1);
                Global.getSoundPlayer().playSound("IndEvo_crucible_alarm", 0.9f, fract, crucible.getLocation(), new Vector2f(0f, 0f));
            }
        });

        float totalDelay = ANIMATION_DELAY;

        addStage(new AnimationStage(MOTE_TRAVEL_DURATION, totalDelay) {
            @Override
            public void run(float amt) {
            }

            @Override
            public void runOnce() {
                for (SectorEntityToken catapult : catapults) {
                    final Vector2f originLocation = catapult.getLocation();
                    final Vector2f target = crucible.getLocation();

                    final CrucibleTrajectoryFollowingMote mote = new CrucibleTrajectoryFollowingMote(originLocation, target, MOTE_TRAVEL_RADIUS_OFFSET, MOTE_TRAVEL_DURATION, ((YeetopultEntityPlugin) catapult.getCustomPlugin()).color);
                    mote.init();

                    YeetopultEntityPlugin p = (YeetopultEntityPlugin) catapult.getCustomPlugin();
                    p.fireAnimation(5f, false);
                    catapult.removeTag(BaseCrucibleEntityPlugin.TAG_ENABLED);

                    float volumeDistance = 1000f; //nothing at 1000f
                    float distance = Misc.getDistance(Global.getSector().getPlayerFleet(), crucible);
                    float fract = 1 - MathUtils.clamp(distance / volumeDistance, 0, 1);
                    Global.getSoundPlayer().playSound("IndEvo_crucible_off", 0.7f, fract, crucible.getLocation(), new Vector2f(0f, 0f));


                    if(Global.getSettings().isDevMode() && false){
                        catapult.addFloatingText("" + catapult.getMemoryWithoutUpdate().getInt(BaseCrucibleEntityPlugin.MEM_CATAPULT_NUM), ((YeetopultEntityPlugin) catapult.getCustomPlugin()).color, MOTE_TRAVEL_DURATION);
                        LunaCampaignRenderingPlugin plugin = new LunaCampaignRenderingPlugin() {
                            float amt = 0;
                            float dur = MOTE_TRAVEL_DURATION;
                            SpriteAPI sprite;

                            @Override
                            public boolean isExpired() {
                                return amt > dur;
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
                                renderCircle(mote.originLocation, 20f);
                                renderCircle(mote.targetLocation, 20f);
                                renderCircle(crucible.getLocation(), 30f);
                            }

                            public void renderCircle(Vector2f loc, float radius){
                                if (sprite == null) sprite = Global.getSettings().getSprite("fx", "IndEvo_target_reticule"); //IndEvo_missile_targetting_arrow

                                sprite.setAlphaMult(1);
                                sprite.setWidth(radius);
                                sprite.setHeight(radius);
                                sprite.setColor(Color.MAGENTA);
                                sprite.renderAtCenter(loc.x, loc.y);

                            }
                        };
                        LunaCampaignRenderer.addRenderer(plugin);
                    }
                }
            }
        });

        totalDelay += MOTE_TRAVEL_DURATION - FLARE_DUR / 2;

        addStage(new AnimationStage(FLARE_DUR, totalDelay) {
            public float globalAlpha = 0;
            public Vector2f loc;

            @Override
            public void run(float amt) {
                globalAlpha = getSineFunctAlpha(timePassed / runtime);

                ModPlugin.log("runtime " +timePassed / runtime + " global alpha " + globalAlpha);
                //flicker1.advance(amt);
            }

            @Override
            public void advance(float amt) {
                //force run at speed for sound after delay time has passed
                if (timePassedTotal >= delayBySeconds && !isDone()){
                    float mult = Global.getSettings().getFloat("campaignSpeedupMult");
                    amt = amt / mult;
                }

                super.advance(amt);
            }

            public float getSineFunctAlpha(float fract){
                //0.5 * sin(1.5pi + x) + 0.5
                return (float) (0.5f * Math.sin(1.5f + Math.PI * fract) + 0.5);
            }

            @Override
            public void runOnce() {
                LunaCampaignRenderer.addRenderer(new RadialDustCloudEjectionRenderer(
                        crucible,
                        0.55f * crucible.getRadius(),
                        0.265f,
                        400f,
                        50f,
                        100f,
                        1f,
                        1f));

                loc = new Vector2f(crucible.getLocation());
                ModPlugin.log("running end stage");
                CrucibleSpawner.removeFromtLoc(crucible.getStarSystem());

                //transit at 0.265s
                float volumeDistance = 1000f; //nothing at 1000f
                float distance = Misc.getDistance(Global.getSector().getPlayerFleet().getLocation(), loc);
                float fract = 1 - MathUtils.clamp(distance / volumeDistance, 0, 1);
                Global.getSoundPlayer().playSound("IndEvo_crucible_transit", 0.7f, fract, loc, new Vector2f(0f, 0f));

                LunaCampaignRenderingPlugin plugin = new LunaCampaignRenderingPlugin() {
                    transient private SpriteAPI glow;

                    @Override
                    public boolean isExpired() {
                        return isDone();
                    }

                    @Override
                    public void advance(float amount) {
                    }

                    @Override
                    public EnumSet<CampaignEngineLayers> getActiveLayers() {
                        return EnumSet.of(CampaignEngineLayers.ABOVE);
                    }

                    @Override
                    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
                        if (glow == null) glow = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");

                        //first
                        glow.setColor(BaseCrucibleEntityPlugin.GLOW_COLOR_1);

                        float factor = 100 * globalAlpha;
                        float w = 40 * factor;
                        float h = 40 * factor;

                        glow.setSize(w, h);
                        glow.setAlphaMult(globalAlpha * 0.4f);
                        glow.setNormalBlend();

                        Vector2f renderLoc = loc; //MathUtils.getPointOnCircumference(entity.getLocation(), 12f, entity.getFacing()-180f); //make glow spawn a bit to the back
                        glow.renderAtCenter(renderLoc.x, renderLoc.y);

                        for (int i = 0; i < 5; i++) {
                            w *= 0.3f;
                            h *= 0.3f;
                            //glow.setSize(w * 0.1f, h * 0.1f);
                            glow.setSize(w, h);
                            glow.setAlphaMult(globalAlpha * 0.6f);
                            glow.renderAtCenter(renderLoc.x, renderLoc.y);
                        }

                        //second
                        glow.setColor(BaseCrucibleEntityPlugin.GLOW_COLOR_2);

                        factor = 60 * globalAlpha;
                        w = 40 * factor;
                        h = 40 * factor;

                        glow.setSize(w, h);
                        glow.setAlphaMult(globalAlpha * 0.4f);
                        glow.setAdditiveBlend();

                        renderLoc = loc; //MathUtils.getPointOnCircumference(entity.getLocation(), 12f, entity.getFacing()-180f); //make glow spawn a bit to the back
                        glow.renderAtCenter(renderLoc.x, renderLoc.y);

                        for (int i = 0; i < 8; i++) {
                            w *= 0.3f;
                            h *= 0.3f;
                            //glow.setSize(w * 0.1f, h * 0.1f);
                            glow.setSize(w, h);
                            glow.setAlphaMult(globalAlpha * 0.4f);
                            glow.renderAtCenter(renderLoc.x, renderLoc.y);
                        }
                    }
                };

                LunaCampaignRenderer.addRenderer(plugin);
            }
        });
    }
}
