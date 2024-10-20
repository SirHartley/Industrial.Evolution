package indevo.industries.derelicts.scripts;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.FlickerUtilV2;
import indevo.exploration.crucible.scripts.AnimationStage;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Vector;

public class WarpEffectAnimationScript implements EveryFrameScript {

              /*"fx_flash":"graphics/fx/IndEvo_flash_new.png",
                      "fx_succy_storm_desat":"graphics/fx/IndEvo_blackhole_desat.png",*/

    public static final float WHIRL_ROTATION_SPEED_ANGLE_PER_SEC = -40f;
    public static final float WHIRL_TWO_ROTATION_SPEED_ANGLE_PER_SEC = -80f;
    public static final float WHIRL_THREE_ROTATION_SPEED_ANGLE_PER_SEC = -160f;
    public static final Color WHIRL_COLOUR_ONE = new Color(180, 80, 255);
    public static final Color WHIRL_COLOUR_TWO = new Color(60, 80, 240);
    public static final Color WHIRL_COLOUR_THREE = new Color(180, 10, 180, 100);
    public static final float TARGET_SIZE_MULTIPLIER = 10f;

    public static final float SCALE_UP_ANIM_DUR = 3f;
    public static final float CONSTANT_SIZE_ANIM_DUR = 1f;
    public static final float SHRINK_ANIM_DUR = 0.5f;
    public static final float FLARE_ANIM_DUR = 1f;

    public List<AnimationStage> stages = new ArrayList<>();
    public boolean done = false;

    public SectorEntityToken target;
    public float originalTargetRadius;
    public float whirlMaxSize;
    public float whirlSize = 0; //this gets changed by the currently active stage
    public float whirlAlpha = 1f;

    public WarpEffectAnimationScript(SectorEntityToken target) {
        this.target = target;
        this.originalTargetRadius = ((PlanetAPI) target).getRadius();
        this.whirlMaxSize = originalTargetRadius * TARGET_SIZE_MULTIPLIER;

        loadStages();
    }

    public void loadStages() {
        //spawn whirl(s) and scale to final size along quad func
        //shrink planet along with whirl
        //spawn hot round shade and ??flare

        float totalDelay = 0f;

        //this stage runs the entire duration and renders the whirls according to the current base data
        stages.add(new AnimationStage(SCALE_UP_ANIM_DUR + CONSTANT_SIZE_ANIM_DUR + SHRINK_ANIM_DUR, totalDelay) {
            @Override
            public void run(float amt) {

            }

            @Override
            public void runOnce() {
                //spawn and rotate the whirl via renderer plugin
                LunaCampaignRenderingPlugin plugin = new LunaCampaignRenderingPlugin() {
                    transient SpriteAPI whirlSprite;
                    public float currentAngle = 0f;
                    public float currentAngleTwo = 0f;
                    public float currentAngleThree = 0f;

                    @Override
                    public boolean isExpired() {
                        return isDone();
                    }

                    @Override
                    public void advance(float amount) {
                        //iterate the rotation angle first whirl
                        float orbitDegreesThisFrame = WHIRL_ROTATION_SPEED_ANGLE_PER_SEC * amount;
                        float nextAngle = currentAngle + orbitDegreesThisFrame;

                        if (nextAngle >= 360f) nextAngle -= 360f;
                        if (nextAngle < 0f) nextAngle += 360f;

                        currentAngle = nextAngle;

                        //second whirl
                        orbitDegreesThisFrame = WHIRL_TWO_ROTATION_SPEED_ANGLE_PER_SEC * amount;
                        nextAngle = currentAngleTwo + orbitDegreesThisFrame;

                        if (nextAngle >= 360f) nextAngle -= 360f;
                        if (nextAngle < 0f) nextAngle += 360f;

                        currentAngleTwo = nextAngle;

                        //third whirl
                        orbitDegreesThisFrame = WHIRL_THREE_ROTATION_SPEED_ANGLE_PER_SEC * amount;
                        nextAngle = currentAngleThree + orbitDegreesThisFrame;

                        if (nextAngle >= 360f) nextAngle -= 360f;
                        if (nextAngle < 0f) nextAngle += 360f;

                        currentAngleThree = nextAngle;
                    }

                    @Override
                    public EnumSet<CampaignEngineLayers> getActiveLayers() {
                        return EnumSet.of(CampaignEngineLayers.TERRAIN_2);
                    }

                    @Override
                    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
                        if (whirlSprite == null) whirlSprite = Global.getSettings().getSprite("IndEvo", "fx_succy_storm_desat");
                        Vector2f loc = target.getLocation();

                        whirlSprite.setNormalBlend();
                        whirlSprite.setAngle(currentAngle);
                        whirlSprite.setSize(whirlSize, whirlSize);
                        whirlSprite.setAlphaMult(whirlAlpha);
                        whirlSprite.setColor(WHIRL_COLOUR_ONE);
                        whirlSprite.renderAtCenter(loc.x, loc.y);

                        whirlSprite.setAdditiveBlend();
                        whirlSprite.setAngle(currentAngleTwo);
                        whirlSprite.setSize(whirlSize * 0.9f, whirlSize * 0.9f);
                        whirlSprite.setAlphaMult(whirlAlpha);
                        whirlSprite.setColor(WHIRL_COLOUR_TWO);
                        whirlSprite.renderAtCenter(loc.x, loc.y);

                        whirlSprite.setAdditiveBlend();
                        whirlSprite.setAngle(currentAngleThree);
                        whirlSprite.setSize(whirlSize * 0.7f, whirlSize * 0.7f);
                        whirlSprite.setAlphaMult(whirlAlpha * 0.5f);
                        whirlSprite.setColor(WHIRL_COLOUR_THREE);
                        whirlSprite.renderAtCenter(loc.x, loc.y);
                    }
                };

                LunaCampaignRenderer.addRenderer(plugin);
            }
        });

        //this stage scales the whirl up quad from -1 to 0
        stages.add(new AnimationStage(SCALE_UP_ANIM_DUR, totalDelay) {
            @Override
            public void run(float amt) {
                float factor = timePassed / runtime;
                float quadFactor = (float) ((-1 * Math.pow(factor, 2)) + 2 * factor); //-x^2 + 2x
                whirlSize = whirlMaxSize * quadFactor;
            }

            @Override
            public void runOnce() {

            }
        });

        totalDelay += SCALE_UP_ANIM_DUR + CONSTANT_SIZE_ANIM_DUR;

        //this stage scales whirl and planet down quickly
        stages.add(new AnimationStage(SHRINK_ANIM_DUR, totalDelay) {
            @Override
            public void run(float amt) {
                float factor = timePassed / runtime;
                float quadFactor = (float) (1 - Math.pow(factor, 2)); //1 - x^2
                whirlSize = whirlMaxSize * quadFactor;
                ((PlanetAPI) target).setRadius(originalTargetRadius * quadFactor);
            }

            @Override
            public void runOnce() {

            }
        });
        
        totalDelay += SHRINK_ANIM_DUR;

        //this stage covers the warp up with a flare and a round light
        stages.add(new AnimationStage(FLARE_ANIM_DUR, totalDelay) {
            @Override
            public void run(float amt) {
                
            }

            @Override
            public void runOnce() {
                LunaCampaignRenderingPlugin plugin = new LunaCampaignRenderingPlugin() {
                    public FlickerUtilV2 flicker = new FlickerUtilV2(0.1f);
                    public transient SpriteAPI glowSprite;
                    public float alphaMult = 1f;
                    
                    @Override
                    public boolean isExpired() {
                        return isDone();
                    }

                    @Override
                    public void advance(float amount) {
                        flicker.advance(amount);

                        float timeToStartFade = runtime * 0.7f;

                        if (timePassed > timeToStartFade) {
                            float correctTimePassed = timePassed - timeToStartFade;
                            float correctTimeLeft = runtime - timeToStartFade;
                            float factor = correctTimePassed / correctTimeLeft;
                            alphaMult = (float) (1 - Math.pow(factor, 2)); //1 - x^2
                        }
                    }

                    @Override
                    public EnumSet<CampaignEngineLayers> getActiveLayers() {
                        return EnumSet.of(CampaignEngineLayers.TERRAIN_5);
                    }

                    @Override
                    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
                        if (glowSprite == null) glowSprite = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");

                        Vector2f loc = target.getLocation();
                        float alpha = flicker.getBrightness() * viewport.getAlphaMult() * alphaMult;

                        glowSprite.setColor(WHIRL_COLOUR_THREE);

                        float w = originalTargetRadius * 8; //spec
                        float h = originalTargetRadius * 8; //spec

                        glowSprite.setSize(w, h);
                        glowSprite.setAlphaMult(0.4f * alphaMult); //spec
                        glowSprite.setAdditiveBlend();

                        glowSprite.renderAtCenter(loc.x, loc.y);

                        for (int i = 0; i < 2; i++) {
                            w *= 0.3f;
                            h *= 0.3f;
                            //glowSprite.setSize(w * 0.1f, h * 0.1f);
                            glowSprite.setSize(w, h);
                            glowSprite.setAlphaMult(alpha); //spec
                            glowSprite.renderAtCenter(loc.x, loc.y);
                        }

                        glowSprite.setColor(WHIRL_COLOUR_TWO);

                        w = originalTargetRadius * 2; //spec
                        h = originalTargetRadius * 2; //spec

                        glowSprite.setSize(w, h);
                        glowSprite.setAlphaMult(0.3f * alphaMult); //spec
                        glowSprite.setAdditiveBlend();

                        glowSprite.renderAtCenter(loc.x, loc.y);

                        for (int i = 0; i < 3; i++) {
                            w *= 0.3f;
                            h *= 0.3f;
                            //glowSprite.setSize(w * 0.1f, h * 0.1f);
                            glowSprite.setSize(w, h);
                            glowSprite.setAlphaMult(alpha); //spec
                            glowSprite.renderAtCenter(loc.x, loc.y);
                        }

                        glowSprite.setColor(WHIRL_COLOUR_ONE);

                        w = originalTargetRadius * TARGET_SIZE_MULTIPLIER * 4;
                        h = originalTargetRadius * TARGET_SIZE_MULTIPLIER * 4;

                        glowSprite.setSize(w, h);
                        glowSprite.setAlphaMult(0.5f * alphaMult);
                        glowSprite.setAdditiveBlend();

                        glowSprite.renderAtCenter(loc.x, loc.y);

                        for (int i = 0; i < 6; i++) {
                            w *= 0.3f;
                            h *= 0.3f;
                            //glowSprite.setSize(w * 0.1f, h * 0.1f);
                            glowSprite.setSize(w, h);
                            glowSprite.setAlphaMult(alpha);
                            glowSprite.renderAtCenter(loc.x, loc.y);
                        }
                    }
                };

                LunaCampaignRenderer.addRenderer(plugin);
            }
        });
        
        totalDelay += 0.1f;
        
        //this stage warps the planet
        stages.add(new AnimationStage(0.1f, totalDelay) {
            @Override
            public void run(float amt) {
            }

            @Override
            public void runOnce() {
                movePlanet(target);
            }
        });
        
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (done) return;

        for (AnimationStage stage : stages) stage.advance(amount);
        for (AnimationStage stage : stages) if (!stage.isDone()) return;
        done = true;
    }
    
    public static void movePlanet(SectorEntityToken target) {
        for (EveryFrameScript script : Global.getSector().getTransientScripts()) {
            if (script instanceof PlanetMovingScript) {
                PlanetMovingScript pm = (PlanetMovingScript) script;

                if (pm.getTarget().getId().equals(target.getId())) {
                    pm.advancePhase();
                }
            }
        }
    }
}
