package indevo.utils.update;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.ids.IndEvo_Items;
import indevo.ids.IndEvo_ids;

import java.util.*;

public class IndEvo_NewGameIndustryPlacer {

    public static void run() {
        placeDryDocks();
        placeAcademy();
        placeReqCenter();
        placeSalvageYards();
        placeManufactories();
        //placeCourierPorts();
    }

    public static void placeManufactories() {
        if (!Global.getSettings().getBoolean("Manufactory")) {
            return;
        }

        HashMap<String, String> h = new HashMap<>();
        h.put("eochu_bres", IndEvo_Items.VPC_PARTS);

        placeIndustries(h, IndEvo_ids.ADMANUF);
    }

    public static void placeSalvageYards() {
        if (!Global.getSettings().getBoolean("ScrapYard")) {
            return;
        }

        HashMap<String, String> h = new HashMap<>();
        h.put("donn", null);
        h.put("agreus", null);
        h.put("suddene", null);

        placeIndustries(h, IndEvo_ids.SCRAPYARD);
    }

    public static void placeDryDocks() {
        if (!Global.getSettings().getBoolean("dryDock")) {
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

        placeIndustries(h, IndEvo_ids.REPAIRDOCKS);
    }

    public static void placeCourierPorts(){
        if (!Global.getSettings().getBoolean("PrivatePort")) {
            return;
        }

        Map<String, String> h = new HashMap<>();
        List<StarSystemAPI> blockedSystems = new ArrayList<>();

        for (FactionAPI f : Global.getSector().getAllFactions()){
            List<MarketAPI> marketList = Misc.getFactionMarkets(f.getId());

            if (marketList.isEmpty() || f.getId().equals(Factions.NEUTRAL)) continue;

            WeightedRandomPicker<MarketAPI> marketPicker = new WeightedRandomPicker<MarketAPI>();
            marketPicker.addAll(marketList);

            while (!marketPicker.isEmpty()){
                MarketAPI m = marketPicker.pickAndRemove();
                if(blockedSystems.contains(m.getStarSystem()) || m.getTags().contains(Tags.MARKET_NO_INDUSTRIES_ALLOWED)) continue;

                h.put(m.getId(), null);
                blockedSystems.add(m.getStarSystem());
            }
        }

        placeIndustries(h, IndEvo_ids.PORT);
    }

    public static void placeAcademy() {
        if (!Global.getSettings().getBoolean("Academy")) {
            return;
        }

        HashMap<String, String> h = new HashMap<>();
        h.put("eochu_bres", Commodities.BETA_CORE);
        h.put("fikenhild", null);

        placeIndustries(h, IndEvo_ids.ACADEMY);
    }

    public static void placeReqCenter() {
        if (!Global.getSettings().getBoolean("ReqCenter")) {
            return;
        }

        HashMap<String, String> h = new HashMap<>();
        h.put("baetis", null);

        placeIndustries(h, IndEvo_ids.REQCENTER);
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
