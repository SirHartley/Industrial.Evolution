package indevo.exploration.crucible.scripts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.ExplosionEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import indevo.exploration.crucible.entities.BaseCrucibleEntityPlugin;
import indevo.exploration.crucible.entities.YeetopultEntityPlugin;
import indevo.exploration.crucible.terrain.CrucibleFieldTerrainPlugin;
import indevo.industries.artillery.entities.VariableExplosionEntityPlugin;
import indevo.utils.animation.AnimationStage;
import indevo.utils.animation.BaseStagedAnimationScript;
import indevo.utils.animation.particles.RadialDustCloudEjectionRenderer;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static indevo.exploration.crucible.entities.BaseCrucibleEntityPlugin.MEM_ACTIVITY_LEVEL;
import static indevo.exploration.crucible.plugin.CrucibleSpawner.MAGNETIC_FIELD_WIDTH;

public class CrucibleStartupAnimationScript extends BaseStagedAnimationScript {
    public static final float CATAPULT_ACTIVATION_INTERVAL_SECONDS = 1f;
    public static final float CATAPULTS_ENABLED_ROTATION_START_DELAY = 1f;
    public static final float MAIN_CRUCIBLE_ACTIVATION_DELAY = 6f; //must be smaller than gear ramp up since it runs parallel
    public static final float GEAR_SCAFFOLD_CATAPULT_RAMP_UP_TIME = 8f;
    public static final float MAIN_CRUCIBLE_ACTIVATION_RAMP_UP_TIME = 10f;

    public List<SectorEntityToken> gears;
    public Map<Integer, SectorEntityToken> catapults;
    public SectorEntityToken scaffold;
    public SectorEntityToken crucible;
    public CampaignTerrainAPI magField;

    public IntervalUtil catapultActivationTimer = new IntervalUtil(CATAPULT_ACTIVATION_INTERVAL_SECONDS, CATAPULT_ACTIVATION_INTERVAL_SECONDS);

    public int currentCatapultIndex = 1;

    public CrucibleStartupAnimationScript(final SectorEntityToken crucible) {
        this.crucible = crucible;
        this.scaffold = crucible.getContainingLocation().getEntitiesWithTag("IndEvo_crucible_scaffold").get(0);
        this.gears = new ArrayList<>(crucible.getContainingLocation().getEntitiesWithTag("IndEvo_crucible_gear"));
        this.catapults = new HashMap<>();

        for (SectorEntityToken catapult : crucible.getContainingLocation().getEntitiesWithTag("IndEvo_yeetopult")) {
            if (catapult.hasTag("IndEvo_orbits_crucible")) {
                catapults.put(catapult.getMemoryWithoutUpdate().getInt(BaseCrucibleEntityPlugin.MEM_CATAPULT_NUM), catapult);
            }
        }
    }

    @Override
    public void loadStages() {
        //set activity level of catapults and their counterparts with the interval (play anim at 1f and move to next one), then enable them at once to make them rotate while spawning dust clouds
        //once done, enable & ramp up gears + scaffold, then enable crucible + mag field with a slight delay & ramp them

        float totalDelay = 0f;
        float fistStageRuntime = (catapults.size()) + 1 * CATAPULT_ACTIVATION_INTERVAL_SECONDS + 0.03f;

        addStage(new AnimationStage(fistStageRuntime, 0f) {
            @Override
            public void run(float amt) {
                catapultActivationTimer.advance(amt);
                if (catapultActivationTimer.intervalElapsed()) {
                    if (catapults.containsKey(currentCatapultIndex)) {
                        SectorEntityToken catapult = catapults.get(currentCatapultIndex);
                        YeetopultEntityPlugin plugin = (YeetopultEntityPlugin) catapult.getCustomPlugin();

                        SectorEntityToken pairedCatapult = plugin.getPairedCatapult();
                        YeetopultEntityPlugin pairedPlugin = (YeetopultEntityPlugin) pairedCatapult.getCustomPlugin();

                        plugin.fireAnimation(10f, true);
                        pairedPlugin.fireAnimation(10f, false);

                        setEnabled(catapult);
                        setEnabled(pairedCatapult);

                        currentCatapultIndex++;
                    }
                }
            }

            @Override
            public void runOnce() {

            }
        });

        totalDelay += fistStageRuntime;
        totalDelay += CATAPULTS_ENABLED_ROTATION_START_DELAY;

        addStage(new AnimationStage(0.1f, totalDelay) {
            @Override
            public void run(float amt) {

            }

            @Override
            public void runOnce() {
                //make smoke and asteroids spawn to signal the entire assembly starting to move

                float volumeDistance = 1000f; //nothing at 1000f
                float distance = Misc.getDistance(Global.getSector().getPlayerFleet(), crucible);
                float fract = 1 - MathUtils.clamp(distance / volumeDistance, 0, 1);
                Global.getSoundPlayer().playSound("IndEvo_crucible_start", 1f, fract * 0.5f, crucible.getLocation(), new Vector2f(0f, 0f));

                LunaCampaignRenderer.addRenderer(new RadialDustCloudEjectionRenderer(
                        crucible,
                        0.55f * crucible.getRadius(),
                        0.4f,
                        150f,
                        10f,
                        100f,
                        2f,
                        1f));

            }
        });

        totalDelay += 1f;

        addStage(new AnimationStage(0.1f, totalDelay) {
            @Override
            public void run(float amt) {

            }

            @Override
            public void runOnce() {
                float volumeDistance = 1000f; //nothing at 1000f
                float distance = Misc.getDistance(Global.getSector().getPlayerFleet(), crucible);
                float fract = 1 - MathUtils.clamp(distance / volumeDistance, 0, 1);
                Global.getSoundPlayer().playSound("IndEvo_crucible_start", 0.7f, fract * 0.4f, crucible.getLocation(), new Vector2f(0f, 0f));

                LunaCampaignRenderer.addRenderer(new RadialDustCloudEjectionRenderer(
                        crucible,
                        0.55f * crucible.getRadius(),
                        0.2f,
                        75f,
                        5f,
                        60f,
                        1f,
                        1f));
            }
        });

        addStage(new AnimationStage(GEAR_SCAFFOLD_CATAPULT_RAMP_UP_TIME, totalDelay) {
            @Override
            public void run(float amt) {
                //enable & ramp up gears + scaffold + catapults
                float factor = Math.min(timePassed / runtime, 1f);

                for (SectorEntityToken catapult : catapults.values()) setActivityLevelFor(catapult, factor);
                for (SectorEntityToken gear : gears) setActivityLevelFor(gear, factor);
                setActivityLevelFor(scaffold, factor);
            }

            @Override
            public void runOnce() {
                for (SectorEntityToken catapult : catapults.values()) setEnabled(catapult);
                for (SectorEntityToken gear : gears) setEnabled(gear);
                setEnabled(scaffold);
            }
        });

        totalDelay += MAIN_CRUCIBLE_ACTIVATION_DELAY;

        addStage(new AnimationStage(0.1f, totalDelay) {
            @Override
            public void run(float amt) {
            }

            @Override
            public void runOnce() {
                //main crucible activation sequence
                setEnabled(crucible);
                magField = CrucibleFieldTerrainPlugin.generate(crucible, 1f, MAGNETIC_FIELD_WIDTH);
                ((BaseCrucibleEntityPlugin) crucible.getCustomPlugin()).setMagField(magField);

                Color color = CrucibleFieldTerrainPlugin.baseColors[0];
                VariableExplosionEntityPlugin.VariableExplosionParams params =
                        new VariableExplosionEntityPlugin.VariableExplosionParams(
                                "IndEvo_mortar_hit",
                                true,
                                0f,
                                color,
                                crucible.getContainingLocation(),
                                crucible.getLocation(),
                                500f,
                                2f);

                params.damage = ExplosionEntityPlugin.ExplosionFleetDamage.NONE;
                SectorEntityToken explosion = crucible.getContainingLocation().addCustomEntity(Misc.genUID(), "Explosion", "IndEvo_VariableExplosion", Factions.NEUTRAL, params);
                explosion.setCircularOrbit(crucible, 0f, 0f, 0f);
                //explosion.setLocation(crucible.getLocation().x, crucible.getLocation().y);
            }
        });

        addStage(new AnimationStage(MAIN_CRUCIBLE_ACTIVATION_RAMP_UP_TIME, totalDelay) {
            @Override
            public void run(float amt) {
                //main crucible ramp up

                float factor = Math.min(timePassed / runtime, 1f);
                setActivityLevelFor(crucible, factor);
                ((CrucibleFieldTerrainPlugin) magField.getPlugin()).setAlphaMult(factor);
            }

            @Override
            public void runOnce() {

            }
        });

    }

    public void setEnabled(SectorEntityToken t) {
        t.addTag(BaseCrucibleEntityPlugin.TAG_ENABLED);
    }

    public void setActivityLevelFor(SectorEntityToken t, float level) {
        t.getMemoryWithoutUpdate().set(MEM_ACTIVITY_LEVEL, level);
    }
}
