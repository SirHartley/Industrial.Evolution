package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.impl.campaign.terrain.BaseRingTerrain;
import com.fs.starfarer.api.util.Misc;
import indevo.abilities.pets.PetManagementAbilityPlugin;
import indevo.ids.Ids;
import indevo.industries.changeling.hullmods.HandBuiltEffectMemoryRepo;
import indevo.industries.changeling.hullmods.HandBuiltHullmod;
import indevo.industries.courierport.ShippingContract;
import indevo.industries.courierport.ShippingContractMemory;
import indevo.industries.courierport.listeners.ShippingManager;
import indevo.industries.derelicts.industry.AncientLaboratory;
import indevo.industries.derelicts.industry.Ruins;
import indevo.industries.derelicts.scripts.PlanetMovingScript;
import indevo.industries.derelicts.utils.RuinsManager;
import indevo.utils.helper.MiscIE;

import java.util.ArrayList;
import java.util.List;

public final class ContentChecks {
    private ContentChecks() {
    }

    private static String stabilityDesc(MarketAPI market) {
        for (MutableStat.StatMod mod : market.getStability().getFlatMods().values()) {
            if (mod.getDesc() != null && mod.getDesc().startsWith("Construction material")) return mod.getDesc() + " " + mod.getValue();
        }
        return "none";
    }

    private static boolean boosted(MarketAPI market, Industry lab, String commodity) {
        for (Industry industry : market.getIndustries()) {
            if (industry.getSupply(commodity).getQuantity().getFlatMods().containsKey("ind_" + lab.getId())) return true;
        }
        return false;
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            Colony.pair(c, 6);
            MarketAPI b = c.market("b");

            new HandBuiltHullmod().createHullmodEffect("ie_check");
            int effects = HandBuiltEffectMemoryRepo.getInstance().get("ie_check").getPluginsWithEffectAmounts().size();
            c.expect(effects == 4, "411 hand built ships roll 4 effects (" + effects + ")");

            ColonyChecks.government(b, "warhammer");
            b.reapplyIndustries();
            c.expect(stabilityDesc(b).contains("shortage"), "406 forge world short of metals: " + stabilityDesc(b));
            b.getCommodityData(Commodities.METALS).getAvailableStat().modifyFlat("ie_check", 20f);
            b.getCommodityData(Commodities.RARE_METALS).getAvailableStat().modifyFlat("ie_check", 20f);
            b.reapplyIndustries();
            c.expect(stabilityDesc(b).contains("met"), "406 forge world with metals and transplutonics: " + stabilityDesc(b));
            b.getCommodityData(Commodities.METALS).getAvailableStat().unmodifyFlat("ie_check");
            b.getCommodityData(Commodities.RARE_METALS).getAvailableStat().unmodifyFlat("ie_check");
        });
        steps.add(c -> {
            MarketAPI a = c.market("a");
            MemoryAPI memory = a.getMemoryWithoutUpdate();
            memory.set(AncientLaboratory.COMMODITY_KEY, true);
            memory.set(AncientLaboratory.COMMODITY_KEY + "_1", Commodities.SUPPLIES);
            memory.set(AncientLaboratory.COMMODITY_KEY + "_2", Commodities.DOMESTIC_GOODS);
            memory.set(AncientLaboratory.COMMODITY_KEY + "_3", Commodities.LUXURY_GOODS);
            a.addIndustry(Industries.HEAVYINDUSTRY);
            a.addIndustry(Industries.LIGHTINDUSTRY);
            a.addIndustry(Ids.LAB);
            a.reapplyIndustries();
            Industry lab = a.getIndustry(Ids.LAB);
            lab.setAICoreId(null);
            a.reapplyIndustries();
            c.expect(boosted(a, lab, Commodities.SUPPLIES), "lab boosts its first commodity");
            c.expect(!boosted(a, lab, Commodities.LUXURY_GOODS), "no core: third commodity not boosted");
            lab.setAICoreId(Commodities.ALPHA_CORE);
            a.reapplyIndustries();
            c.expect(boosted(a, lab, Commodities.LUXURY_GOODS), "602 alpha core boosts the third commodity");
        });
        steps.add(c -> {
            int systems = 0;
            int same = 0;
            for (StarSystemAPI system : Global.getSector().getStarSystems()) {
                List<PlanetAPI> planets = new ArrayList<>();
                for (PlanetAPI planet : system.getPlanets()) if (!planet.isStar() && planet.getMarket() != null) planets.add(planet);
                if (planets.size() < 2) continue;

                MemoryAPI first = planets.get(0).getMarket().getMemoryWithoutUpdate();
                MemoryAPI second = planets.get(1).getMarket().getMemoryWithoutUpdate();
                if (first.contains(Ruins.INDUSTRY_ID_MEMORY_KEY) || second.contains(Ruins.INDUSTRY_ID_MEMORY_KEY)) continue;

                RuinsManager.setUpgradeSpec(planets.get(0));
                RuinsManager.setUpgradeSpec(planets.get(1));
                systems++;
                if (first.getString(Ruins.INDUSTRY_ID_MEMORY_KEY).equals(second.getString(Ruins.INDUSTRY_ID_MEMORY_KEY))) same++;
                first.unset(Ruins.INDUSTRY_ID_MEMORY_KEY);
                second.unset(Ruins.INDUSTRY_ID_MEMORY_KEY);
            }
            c.expect(systems > 3 && same == 0, "605 ruins in one system roll different industries (" + same + " same of " + systems + " systems)");
        });
        steps.add(c -> {
            MarketAPI a = c.market("a");
            MarketAPI b = c.market("b");
            Global.getSector().getPlayerFaction().getProduction().setGatheringPoint(b);
            FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "onslaught_Elite");
            int before = Misc.getStorageCargo(b).getWeapons().size();
            MiscIE.stripShipToCargoAndReturnVariant(member, a);
            c.expect(Misc.getStorageCargo(b).getWeapons().size() > before, "3907 stripped weapons reach the gathering point");
            c.expect(Misc.getStorageCargo(a).getWeapons().isEmpty(), "3907 nothing left in local storage");
            Global.getSector().getPlayerFaction().getProduction().setGatheringPoint(null);

            PetManagementAbilityPlugin ability = (PetManagementAbilityPlugin) Global.getSector().getPlayerFleet().getAbility(Ids.ABILITY_PET_MANAGEMENT);
            c.expect(!ability.showActiveIndicator(), "1009 pet ability never shows active");

            a.addIndustry(Ids.PORT);
            a.reapplyIndustries();
            ShippingContract contract = new ShippingContract();
            contract.isActive = false;
            contract.elapsedDays = 999;
            ShippingContractMemory.getContractList().add(contract);
            Global.getSector().getListenerManager().getListeners(ShippingManager.class).get(0).onNewDay();
            c.expect(contract.elapsedDays == 999, "507 ship now on a broken inactive contract does nothing and does not crash");
            ShippingContractMemory.getContractList().remove(contract);
        });
        steps.add(c -> {
            MarketAPI b = c.market("b");
            PlanetAPI planet = b.getPlanetEntity();
            SectorEntityToken ring = planet.getContainingLocation().addTerrain(Terrain.RING, new BaseRingTerrain.RingParams(200f, planet.getRadius() + 300f, planet, "Test ring"));
            c.put("ring", ring.getId());
            c.note("ring terrain " + ring.getId());

            StarSystemAPI target = null;
            for (StarSystemAPI system : Global.getSector().getStarSystems()) {
                if (system != planet.getContainingLocation() && system.getStar() != null && !system.hasTag("theme_hidden") && Misc.getMarketsInLocation(system).isEmpty()) {
                    target = system;
                    break;
                }
            }
            c.put("target", target.getId());
            c.put("from", planet.getContainingLocation().getId());
            b.addIndustry(Ids.RIFTGEN);
            Global.getSector().addScript(new PlanetMovingScript(b, target));
            c.days(3f);
        });
        steps.add(c -> {
            MarketAPI b = c.market("b");
            StarSystemAPI target = Global.getSector().getStarSystem((String) c.get("target"));
            c.expect(b.getStarSystem() == target, "planet moved to " + target.getName() + " (is in " + b.getStarSystem().getName() + ")");
            boolean moved = false;
            for (CampaignTerrainAPI terrain : target.getTerrainCopy()) {
                if (terrain.getPlugin() instanceof BaseRingTerrain && ((BaseRingTerrain) terrain.getPlugin()).getRelatedEntity() == b.getPrimaryEntity()) moved = true;
            }
            c.expect(moved, "607 ring terrain moved with the planet");
        });
        return steps;
    }
}
