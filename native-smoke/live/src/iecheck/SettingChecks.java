package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin;
import indevo.config.Setting;
import indevo.exploration.meteor.MeteorSwarmManager;
import indevo.exploration.meteor.scripts.TutorialEncounterScript;
import indevo.exploration.minefields.MineBeltTerrainPlugin;
import indevo.ids.Ids;
import indevo.items.consumables.listeners.ConsumableItemMarketAdder;
import indevo.industries.museum.data.ParadeFleetProfile;
import indevo.industries.museum.industry.Museum;

import java.util.ArrayList;
import java.util.List;

// settings that apply on next use, and what a new game takes from the switches
public final class SettingChecks {
    private static final String ACADEMY_MARKET_ID = indevo.industries.academy.rules.IndEvo_AcademyVariables.ACADEMY_MARKET_ID;

    private SettingChecks() {
    }

    private static boolean paradeOut(Museum museum) {
        for (ParadeFleetProfile profile : museum.getParadeFleetProfiles()) if (profile.hasActiveFleet()) return true;
        return false;
    }

    private static int mines(StarSystemAPI system, SectorEntityToken belt) {
        int count = 0;
        for (SectorEntityToken entity : system.getCustomEntities()) {
            if ("IndEvo_mine".equals(entity.getCustomEntityType()) && entity.getOrbitFocus() == belt) count++;
        }
        return count;
    }

    public static List<Step> nextUse() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            Colony.pair(c, 6);
            c.market("a").addIndustry(Ids.MUSEUM);
            c.market("a").reapplyIndustries();
            c.days(1f);
        });
        steps.add(c -> {
            MarketAPI a = c.market("a");
            Museum museum = (Museum) a.getIndustry(Ids.MUSEUM);
            CargoAPI cargo = a.getSubmarket(Ids.MUSEUM_SUBMARKET).getCargo();
            cargo.initMothballedShips(Global.getSector().getPlayerFaction().getId());
            for (int i = 0; i < 10; i++) cargo.getMothballedShips().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.SHIP, "wolf_Assault"));
            museum.setFlyParades(true);

            Colony.setting(Setting.MUSEUM_MIN_SHIPS_FOR_PARADE, 50);
            museum.reportEconomyTick(5);
            c.expect(!paradeOut(museum), "Museum minimum ships setting applies on next use");
            Colony.setting(Setting.MUSEUM_MIN_SHIPS_FOR_PARADE, 3);
            museum.reportEconomyTick(5);
            c.expect(paradeOut(museum), "Museum parade goes out once the setting allows it");
            museum.setFlyParades(false);
            for (ParadeFleetProfile profile : museum.getParadeFleetProfiles()) profile.despawnFleet();

            PlanetAPI planet = a.getPrimaryEntity() instanceof PlanetAPI ? (PlanetAPI) a.getPrimaryEntity() : null;
            StarSystemAPI system = a.getStarSystem();
            Colony.setting(Setting.ENABLE_MINEFIELDS, false);
            SectorEntityToken belt = MineBeltTerrainPlugin.addMineBelt(planet, 500f, 100f, 30f, 40f, "Check Minefield");
            MineBeltTerrainPlugin plugin = (MineBeltTerrainPlugin) ((com.fs.starfarer.api.campaign.CampaignTerrainAPI) belt).getPlugin();
            plugin.regenerateAsteroids();
            c.expect(mines(system, belt) == 0, "minefield switch applies on next use");
            Colony.setting(Setting.ENABLE_MINEFIELDS, true);
            plugin.regenerateAsteroids();
            c.expect(mines(system, belt) > 0, "mines back with the switch on");

            StarSystemAPI valid = null;
            for (StarSystemAPI one : Global.getSector().getStarSystems()) if (MeteorSwarmManager.isValidMeteorLoc(one)) valid = one;
            c.expect(valid != null, "system for meteors found");
            Colony.jump(valid, 6000f, 0f);
            Global.getSector().getMemoryWithoutUpdate().unset(MeteorSwarmManager.MEM_METEOR_TUTORIAL);
            Colony.setting(Setting.METEOR_ENABLED, false);
            MeteorSwarmManager.getInstance().reportEconomyTick(1);
            c.expect(!Global.getSector().getMemoryWithoutUpdate().getBoolean(MeteorSwarmManager.MEM_METEOR_TUTORIAL), "meteor switch applies on next use");
            Colony.setting(Setting.METEOR_ENABLED, true);
            MeteorSwarmManager.getInstance().reportEconomyTick(1);
            c.expect(Global.getSector().getMemoryWithoutUpdate().getBoolean(MeteorSwarmManager.MEM_METEOR_TUTORIAL), "meteors back with the switch on");
            Global.getSector().removeScriptsOfClass(TutorialEncounterScript.class);
        });
        return steps;
    }

    // run on the base save for the switches on, on a new game with SWITCHES=off for the switches off
    public static List<Step> newGame() {
        boolean on = !"off".equals(System.getProperty("ie.switches"));
        List<Step> steps = new ArrayList<>();
        steps.add(c -> c.days(2f));
        steps.add(c -> {
            int industries = 0;
            int consumables = 0;
            int minefields = 0;
            int stations = 0;
            int ruins = 0;
            for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
                for (Industry industry : market.getIndustries()) {
                    // the Galatia station is the Academy in any game, the switch only stops new ones
                    if (!industry.getId().startsWith("IndEvo_") || ACADEMY_MARKET_ID.equals(market.getId())) continue;
                    industries++;
                    if (!on) c.note("left on " + market.getName() + ": " + industry.getId());
                }
                if (market.hasCondition(Ids.COND_MINERING)) minefields++;
            }
            ConsumableItemMarketAdder adder = new ConsumableItemMarketAdder(false);
            for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
                // the adder only touches stock the game just refreshed
                for (SubmarketAPI submarket : market.getSubmarketsCopy()) {
                    if (submarket.getPlugin() instanceof BaseSubmarketPlugin) ((BaseSubmarketPlugin) submarket.getPlugin()).setSinceSWUpdate(0f);
                }
                adder.reportPlayerOpenedMarketAndCargoUpdated(market);
            }
            for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
                for (SubmarketAPI submarket : market.getSubmarketsCopy()) {
                    for (CargoStackAPI stack : submarket.getCargo().getStacksCopy()) {
                        if (stack.isSpecialStack() && stack.getSpecialDataIfSpecial().getId().startsWith("IndEvo_consumable")) consumables++;
                    }
                }
            }

            int gacha = 0;
            for (StarSystemAPI system : Global.getSector().getStarSystems()) {
                for (SectorEntityToken entity : system.getAllEntities()) {
                    String id = entity.getCustomEntityType();
                    if (Ids.LAB_ENTITY.equals(id) || Ids.ARSENAL_ENTITY.equals(id) || Ids.ABANDONED_PETSHOP_ENTITY.equals(id)) stations++;
                    if (Ids.GACHA_STATION.equals(id)) gacha++;
                }
                for (PlanetAPI planet : system.getPlanets()) {
                    if (planet.getMarket() != null && planet.getMarket().hasCondition(Ids.COND_RUINS)) ruins++;
                }
            }
            c.note("switches " + (on ? "on" : "off") + ": industries " + industries + ", consumables " + consumables + ", minefields " + minefields
                    + ", stations " + stations + ", ruins " + ruins + ", automated shipyards " + gacha);
            if (on) {
                c.expect(industries > 0, "new game places IE industries");
                c.expect(stations > 0, "new game places derelict stations");
                c.expect(minefields > 0, "new game places minefields");
                c.expect(consumables > 0, "markets stock consumables");
            } else {
                c.expect(industries == 0, "new game without industries (" + industries + ")");
                c.expect(consumables == 0, "new game without consumables (" + consumables + ")");
                c.expect(stations == 0, "new game without derelict stations (" + stations + ")");
                c.expect(ruins == 0, "new game without derelict ruins (" + ruins + ")");
                c.expect(minefields == 0, "new game without minefields (" + minefields + ")");
            }
        });
        return steps;
    }

    public static void switchesOff() throws Exception {
        Colony.setting(Setting.BLOCK_INDUSTRIES, false);
        Colony.setting(Setting.BLOCK_CONSUMABLES, false);
        Colony.setting(Setting.ENABLE_DERELICTS, false);
        Colony.setting(Setting.ENABLE_MINEFIELDS, false);
        Colony.setting(Setting.ENABLE_ARTILLERY, false);
        Colony.setting(Setting.PETS, false);
    }
}
