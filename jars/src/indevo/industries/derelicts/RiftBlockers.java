package indevo.industries.derelicts;

import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import indevo.ids.Ids;
import indevo.industries.compatibility.AotdManager;
import indevo.utils.helper.MiscIE;

public class RiftBlockers {

    public static final String BLOCKED_KEY = "$IndEvo_riftBlocked";

    public record Reason(String text, String... highlights) {
    }

    private RiftBlockers() {
    }

    public static Reason getReason(MarketAPI market) {
        PlanetAPI planet = market.getPlanetEntity();
        if (planet == null) return new Reason("The Generator can only move a planet.");
        if (AotdManager.hasMegastructure(planet)) return new Reason("A megastructure ties this planet to its star system.");
        if (isBlocked(planet)) return new Reason("Something on this planet disrupts the targeting process.");

        for (SectorEntityToken e : RiftRelocator.getMovers(market)) {
            if (AotdManager.hasMegastructure(e) || isBlocked(e))
                return new Reason("The " + e.getName() + " in orbit disrupts the targeting process.", e.getName());
        }

        return null;
    }

    private static boolean isBlocked(SectorEntityToken entity) {
        if (entity.getMemoryWithoutUpdate().getBoolean(BLOCKED_KEY)) return true;
        return entity.getCustomEntityType() != null && MiscIE.getCSVSetFromMemory(Ids.RIFT_BLOCKING_ENTITIES_LIST).contains(entity.getCustomEntityType());
    }
}
