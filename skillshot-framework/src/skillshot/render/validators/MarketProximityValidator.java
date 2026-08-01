package skillshot.render.validators;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;
import skillshot.render.PositionValidator;

/**
 * Rejects aim points that sit inside the safe bubble around an inhabited world - the "don't shell
 * civilians" rule. Radius is measured from the market's primary entity and includes that entity's
 * own radius.
 */
public class MarketProximityValidator implements PositionValidator {

    protected float safeRadius;

    public MarketProximityValidator(float safeRadius) {
        this.safeRadius = safeRadius;
    }

    @Override
    public boolean isValid(Vector2f worldPos) {
        for (MarketAPI market : Misc.getMarketsInLocation(Global.getSector().getCurrentLocation())) {
            if (market.getPrimaryEntity() == null) continue;

            float radius = market.getPrimaryEntity().getRadius() + safeRadius;
            if (Misc.getDistance(market.getPrimaryEntity().getLocation(), worldPos) < radius) return false;
        }

        return true;
    }
}
