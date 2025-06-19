package indevo.exploration.meteor.entities;

import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.exploration.meteor.movement.MeteorMovementModuleAPI;
import indevo.exploration.meteor.spawners.IceSwarmSpawner;
import indevo.utils.helper.MiscIE;
import indevo.utils.helper.TrigHelper;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.List;
import java.util.Random;

import static com.fs.starfarer.api.util.Misc.addHitGlow;

public class IceTreasureoidEntity extends IcyRockEntity {

    public static class IceMeteorData extends MeteorData {
        public Random random;

        public IceMeteorData(float size, MeteorMovementModuleAPI movement, Random random) {
            super(size, movement);
            this.random = random;
        }
    }

    public IntervalUtil sparkleInterval = new IntervalUtil(0.02f, 0.08f);
    public SectorEntityToken relatedWreck;
    public Random random;

    public static SectorEntityToken spawn(LocationAPI loc, MeteorData data, Random random){
        return loc.addCustomEntity(Misc.genUID(), null, "IndEvo_meteor_treasure_2", "engHubStorageColour", data.size, data.size, data.size,
                new IceMeteorData(data.size, data.movement, random));
    }

    @Override
    public void init(SectorEntityToken entity, Object pluginParams) {
        super.init(entity, pluginParams);

        IceMeteorData data = (IceMeteorData) pluginParams;
        this.random = data.random;
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        if (relatedWreck == null) spawnWreck();

        relatedWreck.setLocation(entity.getLocation().x, entity.getLocation().y);

        sparkleInterval.advance(amount);

        if (sparkleInterval.intervalElapsed()) {
            Vector2f loc = MathUtils.getRandomPointInCircle(entity.getLocation(), entity.getRadius() * 0.5f);
            float distToCenter = Misc.getDistance( entity.getLocation(), loc);
            float fract = distToCenter / entity.getRadius();
            float dur = 1 + 3 * TrigHelper.getNormalDistributionCurve(fract, 0.4f, 0f);

            addHitGlow(entity.getContainingLocation(), loc, new Vector2f(0,0), 20f, dur, Color.CYAN);
        }

        if (!colliding && entity.hasTag(Tags.FADING_OUT_AND_EXPIRING)) Misc.fadeAndExpire(relatedWreck, 0f);
    }

    public void spawnWreck(){
        WeightedRandomPicker<ShipRecoverySpecial.ShipCondition> conditionWeightedRandomPicker = new WeightedRandomPicker<>();
        conditionWeightedRandomPicker.addAll(List.of(ShipRecoverySpecial.ShipCondition.values()));

        WeightedRandomPicker<ShipHullSpecAPI> specAPIWeightedRandomPicker = new WeightedRandomPicker<>(random);

        for (ShipHullSpecAPI spec : MiscIE.getAllLearnableShipHulls()){
            if (spec.getBaseValue() > IceSwarmSpawner.MIN_TREASURE_SHIP_VALUE) specAPIWeightedRandomPicker.add(spec);
        }

        DerelictShipEntityPlugin.DerelictShipData params = DerelictShipEntityPlugin.createHull(specAPIWeightedRandomPicker.pick().getHullId(), random, 0.15f);
        relatedWreck = BaseThemeGenerator.addSalvageEntity(entity.getContainingLocation(), Entities.WRECK, Factions.NEUTRAL, params);

        //this is required or it'll always be a SP recovery for some reason
        ShipRecoverySpecial.ShipRecoverySpecialData data = new ShipRecoverySpecial.ShipRecoverySpecialData("caught in the ice, now freed");
        data.addShip(params.ship);
        data.storyPointRecovery = false;

        Misc.setSalvageSpecial(relatedWreck, data);
    }
}
