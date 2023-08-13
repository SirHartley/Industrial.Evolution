package indevo.utils.update;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.ids.Ids;
import indevo.ids.ItemIds;
import indevo.utils.helper.Settings;

import java.util.*;

public class NewGameIndustryPlacer {

    public static void run() {
        placeDryDocks();
        placeAcademy();
        placeReqCenter();
        placeSalvageYards();
        placeManufactories();
        placePetShops();
        //placeCourierPorts();
    }

    public static void placePetShops() {
        if (!Settings.PETS) {
            return;
        }

        HashMap<String, String> h = new HashMap<>();
        h.put("jangala", null);
        h.put("gilead", null);
        h.put("fikenhild", null);

        placeIndustries(h, Ids.PET_STORE);
    }

    public static void placeManufactories() {
        if (!Settings.ADMANUF
) {
            return;
        }

        HashMap<String, String> h = new HashMap<>();
        h.put("eochu_bres", ItemIds.VPC_PARTS);

        placeIndustries(h, Ids.ADMANUF);
    }

    public static void placeSalvageYards() {
        if (!Settings.SCRAPYARD) {
            return;
        }

        HashMap<String, String> h = new HashMap<>();
        h.put("donn", null);
        h.put("agreus", null);
        h.put("suddene", null);

        placeIndustries(h, Ids.SCRAPYARD);
    }

    public static void placeDryDocks() {
        if (!Settings.DRYDOCK) {
            return;
        }

        HashMap<String, String> h = new HashMap<>();
        h.put("agreus", null);
        h.put("cruor", null);
        h.put("chicomoztoc", null);
        h.put("asher", null);
        h.put("culann", Commodities.BETA_CORE);
        h.put("station_kapteyn", null);
        h.put("new_maxios", Commodities.GAMMA_CORE);
        h.put("mairaath_abandoned_station2", null);
        h.put("yama", null);
        h.put("kazeron", Commodities.GAMMA_CORE);
        h.put("ragnar_complex", null);
        h.put("suddene", null);
        h.put("ilm", null);

        placeIndustries(h, Ids.REPAIRDOCKS);
    }

    public static void placeCourierPorts() {
        if (!Settings.PRIVATEPORT) {
            return;
        }

        Map<String, String> h = new HashMap<>();
        List<StarSystemAPI> blockedSystems = new ArrayList<>();

        for (FactionAPI f : Global.getSector().getAllFactions()) {
            List<MarketAPI> marketList = Misc.getFactionMarkets(f.getId());

            if (marketList.isEmpty() || f.getId().equals(Factions.NEUTRAL)) continue;

            WeightedRandomPicker<MarketAPI> marketPicker = new WeightedRandomPicker<MarketAPI>();
            marketPicker.addAll(marketList);

            while (!marketPicker.isEmpty()) {
                MarketAPI m = marketPicker.pickAndRemove();
                if (blockedSystems.contains(m.getStarSystem()) || m.getTags().contains(Tags.MARKET_NO_INDUSTRIES_ALLOWED))
                    continue;

                h.put(m.getId(), null);
                blockedSystems.add(m.getStarSystem());
            }
        }

        placeIndustries(h, Ids.PORT);
    }

    public static void placeAcademy() {
        if (!Settings.ACADEMY) {
            return;
        }

        HashMap<String, String> h = new HashMap<>();
        h.put("eochu_bres", Commodities.BETA_CORE);
        h.put("fikenhild", null);

        placeIndustries(h, Ids.ACADEMY);
    }

    public static void placeReqCenter() {
        if (!Settings.REQCENTER) {
            return;
        }

        HashMap<String, String> h = new HashMap<>();
        h.put("baetis", null);

        placeIndustries(h, Ids.REQCENTER);
    }

    private static void placeIndustries(Map<String, String> planetIdMap, String industryId) {
        for (Map.Entry<String, String> entry : planetIdMap.entrySet()) {
            MarketAPI m;

            if (Global.getSector().getEconomy().getMarket(entry.getKey()) != null) {
                m = Global.getSector().getEconomy().getMarket(entry.getKey());

                if (!m.hasIndustry(industryId)
                        && !m.isPlayerOwned()
                        && !m.getFaction().getId().equals(Global.getSector().getPlayerFaction().getId())) {

                    m.addIndustry(industryId);

                    if (entry.getValue() == null) continue;

                    if (isAICoreId(entry.getValue())) m.getIndustry(industryId).setAICoreId(entry.getValue());
                    else m.getIndustry(industryId).setSpecialItem(new SpecialItemData(entry.getValue(), null));
                }
            }
        }
    }

    protected static boolean isAICoreId(String str) {
        Set<String> cores = new HashSet<String>();
        cores.add(Commodities.ALPHA_CORE);
        cores.add(Commodities.BETA_CORE);
        cores.add(Commodities.GAMMA_CORE);
        return cores.contains(str);
    }
}
