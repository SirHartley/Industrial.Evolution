package indevo.exploration.meteor;

import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.utils.helper.TrigHelper;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

import static com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator.addSalvageEntity;
import static com.fs.starfarer.api.util.Misc.*;

public class TreasuroidEntity extends MeteorEntity {

    public static void spawn(LocationAPI loc, MeteorData data){
        loc.addCustomEntity(Misc.genUID(), null, "IndEvo_meteor_treasure_1", "engHubStorageColour", data.size * 1.2f, data.size, data.size, data);
    }

    public boolean hasDroppedTreasure = false;
    public IntervalUtil sparkleInterval = new IntervalUtil(0.02f, 0.08f);

    @Override
    public void advance(float amount) {
        super.advance(amount);
        sparkleInterval.advance(amount);

        if (sparkleInterval.intervalElapsed() && !hasDroppedTreasure) {
            Vector2f loc = MathUtils.getRandomPointInCircle(entity.getLocation(), entity.getRadius());
            float distToCenter = Misc.getDistance( entity.getLocation(), loc);
            float fract = distToCenter / entity.getRadius();
            float dur = 1 + 3 * TrigHelper.getNormalDistributionCurve(fract, 0.4f, 0f);

            addHitGlow(entity.getContainingLocation(), loc, new Vector2f(0,0), 20f, dur, Color.ORANGE);
        }

        if (colliding && !hasDroppedTreasure) {
            WeightedRandomPicker<String> caches = createStringPicker(
                    Entities.SUPPLY_CACHE, 20f,
                    Entities.EQUIPMENT_CACHE, 20f,
                    Entities.EQUIPMENT_CACHE_SMALL, 20f,
                    Entities.WEAPONS_CACHE, 10f,
                    Entities.WEAPONS_CACHE_LOW, 10f,
                    Entities.WEAPONS_CACHE_HIGH, 10f,
                    Entities.WEAPONS_CACHE_SMALL_HIGH, 10f);

            SectorEntityToken loot = addSalvageEntity(random, entity.getStarSystem(), caches.pick(), null);
            loot.setDiscoverable(true);
            loot.setDiscoveryXP(100f);
            loot.setLocation(entity.getLocation().x, entity.getLocation().y);

            hasDroppedTreasure = true;
        }
    }
}

