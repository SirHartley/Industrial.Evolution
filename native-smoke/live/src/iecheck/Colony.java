package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.submarkets.StoragePlugin;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.List;

public final class Colony {
    private Colony() {
    }

    public static List<PlanetAPI> planets(int count) {
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (system.hasTag(Tags.THEME_HIDDEN) || !Misc.getMarketsInLocation(system).isEmpty()) continue;
            if (system.getStar() == null) continue;

            List<PlanetAPI> planets = new ArrayList<>();
            for (PlanetAPI planet : system.getPlanets()) {
                if (planet.isStar() || planet.getMarket() == null) continue;
                planets.add(planet);
            }
            if (planets.size() >= count) return planets.subList(0, count);
        }
        throw new IllegalStateException("No free system with " + count + " planets");
    }

    public static MarketAPI make(PlanetAPI planet, int size) {
        MarketAPI market = planet.getMarket();
        market.removeCondition(com.fs.starfarer.api.impl.campaign.ids.Conditions.DECIVILIZED);
        market.setSize(size);
        market.addCondition("population_" + size);
        market.setFactionId(Factions.PLAYER);
        market.setPlayerOwned(true);
        market.setPlanetConditionMarketOnly(false);
        market.setDaysInExistence(0);
        market.setHidden(false);
        market.addIndustry(Industries.POPULATION);
        market.addIndustry(Industries.SPACEPORT);
        market.addSubmarket(Submarkets.SUBMARKET_STORAGE);
        ((StoragePlugin) market.getSubmarket(Submarkets.SUBMARKET_STORAGE).getPlugin()).setPlayerPaidToUnlock(true);
        market.addSubmarket(Submarkets.LOCAL_RESOURCES);
        market.getTariff().modifyFlat("default_tariff", market.getFaction().getTariffFraction());
        Misc.setFullySurveyed(market, null, false);
        planet.setFaction(Factions.PLAYER);
        Global.getSector().getEconomy().addMarket(market, true);
        Global.getSector().getEconomy().tripleStep();
        market.advance(0f);
        return market;
    }

    public static void jump(LocationAPI location, float x, float y) {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        fleet.getContainingLocation().removeEntity(fleet);
        location.addEntity(fleet);
        Global.getSector().setCurrentLocation(location);
        fleet.setLocation(x, y);
    }

    // checks that sit in meteor swarms wear the fleet down
    public static void ships() {
        for (int i = 0; i < 4; i++) {
            Global.getSector().getPlayerFleet().getFleetData().addFleetMember(Global.getFactory().createFleetMember(com.fs.starfarer.api.fleet.FleetMemberType.SHIP, "onslaught_Elite"));
        }
    }

    public static void setting(indevo.config.Setting setting, Object value) throws Exception {
        lunalib.backend.ui.settings.LunaSettingsLoader.getSettings().get(indevo.config.PlayerSettings.MOD_ID).put(setting.id, value);
        indevo.config.PlayerSettings.reload();
    }

    public static void pair(Ctx c, int size) {
        List<PlanetAPI> planets = planets(2);
        MarketAPI a = make(planets.get(0), size);
        MarketAPI b = make(planets.get(1), size);
        c.put("a", a.getId());
        c.put("b", b.getId());
        c.note("colonies " + a.getName() + " and " + b.getName() + " in " + a.getStarSystem().getName());
    }
}
