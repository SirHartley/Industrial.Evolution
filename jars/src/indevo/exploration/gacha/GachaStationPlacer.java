package indevo.exploration.gacha;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.MusicPlayerPluginImpl;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.utils.ModPlugin;
import indevo.utils.helper.Settings;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static indevo.exploration.gacha.GachaStationDialoguePlugin.RANDOM;

public class GachaStationPlacer {

    public static final String HAS_PLACED_STATIONS = "$IndEvo_hasPlacedStations";
    public static final float AMOUNT_MULT = Settings.getFloat(Settings.AUTOMATEDSHIPYARD_NUM); //default 1.5f

    public static void place() {
        if (Global.getSector().getPersistentData().containsKey(HAS_PLACED_STATIONS)) return;

        int amt = (int) Math.ceil(Global.getSector().getEntitiesWithTag(Tags.CORONAL_TAP).size() * AMOUNT_MULT);

        Map<String, Float> starTypes = new HashMap<>();
        starTypes.put("star_red_supergiant", 100f);
        starTypes.put("star_red_giant", 50f);
        starTypes.put("star_red_dwarf", 10f);

        WeightedRandomPicker<StarSystemAPI> systemPicker = new WeightedRandomPicker<>();

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (!Misc.getMarketsInLocation(system).isEmpty()) continue;
            if (!system.getEntitiesWithTag(Tags.CORONAL_TAP).isEmpty()) continue;

            PlanetAPI p = system.getStar();

            if (p == null) continue;

            String type = p.getTypeId();

            if (type != null && starTypes.containsKey(type)) {
                systemPicker.add(system, starTypes.get(type));
            } else if (type != null) {
                systemPicker.add(system, 0.01f);
            }
        }

        for (int i = 0; i < amt; i++) {
            placeStation(systemPicker.pickAndRemove());
        }

        Global.getSector().getPersistentData().put(HAS_PLACED_STATIONS, true);
    }

    public static void placeStation(StarSystemAPI system) {
        SectorEntityToken t = system.addCustomEntity(Misc.genUID(), null, "IndEvo_GachaStation", null);
        t.setCircularOrbitPointingDown(system.getStar(), 0f, system.getStar().getRadius() + 200f, 31);
        t.getMemoryWithoutUpdate().set(RANDOM, new Random(t.hashCode()));
        t.getMemoryWithoutUpdate().set(MusicPlayerPluginImpl.MUSIC_SET_MEM_KEY, "IndEvo_Haplogynae_derelict_theme");
        ModPlugin.log("placed GachaStation (Automated Shipyard) at " + system.getName());
    }
}
