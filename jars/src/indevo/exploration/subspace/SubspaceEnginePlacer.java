package indevo.exploration.subspace;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.ids.Ids;
import indevo.utils.ModPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static indevo.exploration.gacha.GachaStationDialoguePlugin.RANDOM;

public class SubspaceEnginePlacer {
    public static final String HAS_PLACED_STATIONS = "$IndEvo_hasPlacedSubspaceStations";
    public static final float AMOUNT_MULT = Global.getSettings().getFloat("IndEvo_SubspaceEngineStationNum"); //default 1.5f
    public static final float RANGE = 12;

    public static void place() {
        if (Global.getSector().getPersistentData().containsKey(HAS_PLACED_STATIONS)) return;

        int amt = (int) Math.ceil(Global.getSector().getEntitiesWithTag(Tags.CORONAL_TAP).size() * AMOUNT_MULT);

        Map<String, Float> starTypes = new HashMap<>();
        starTypes.put("star_neutron", 10f);
        starTypes.put("black_hole", 100f);

        WeightedRandomPicker<StarSystemAPI> systemPicker = new WeightedRandomPicker<>();

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (!Misc.getMarketsInLocation(system).isEmpty()) continue;
            if (!system.getEntitiesWithTag(Tags.CORONAL_TAP).isEmpty()) continue;
            if (!system.getEntitiesWithTag(Ids.GACHA_STATION).isEmpty()) continue;

            PlanetAPI p = system.getStar();

            if (p == null) continue;

            String type = p.getTypeId();

            if (type != null && starTypes.containsKey(type)) {
                systemPicker.add(system, starTypes.get(type));
            }
        }

        for (int i = 0; i < amt; i++) {
            StarSystemAPI system = systemPicker.pickAndRemove();
            placeStation(system);

            //remove all entries within the range of the station so it doesn't double spawn in range
            for (StarSystemAPI s : new ArrayList<>(systemPicker.getItems())) {
                if (Misc.getDistance(s.getHyperspaceAnchor(), system.getHyperspaceAnchor()) < RANGE)
                    systemPicker.remove(s);
            }
        }

        Global.getSector().getPersistentData().put(HAS_PLACED_STATIONS, true);
    }

    public static void placeStation(StarSystemAPI system) {
        SectorEntityToken t = system.addCustomEntity(Misc.genUID(), null, "IndEvo_SubspaceStation", null);
        t.setCircularOrbitPointingDown(system.getStar(), 0f, system.getStar().getRadius() + 200f, 31);
        t.getMemoryWithoutUpdate().set(RANDOM, new Random(t.hashCode()));

        ModPlugin.log("placed Subspace Engine at " + system.getName());
    }
}
