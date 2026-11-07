package indevo.skillshot.render.validators;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;
import indevo.skillshot.render.PositionValidator;

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
