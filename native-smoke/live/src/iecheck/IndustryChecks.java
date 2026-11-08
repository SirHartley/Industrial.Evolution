package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Personalities;
import com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.ids.Ids;
import indevo.ids.ItemIds;
import indevo.industries.RestorationDocks;
import indevo.industries.academy.industry.Academy;
import indevo.items.VPCItemPlugin;
import indevo.items.installable.SpecialItemEffectsRepo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class IndustryChecks {
    private IndustryChecks() {
    }

    private static boolean hasCoreUpkeep(Industry industry) {
        return industry.getUpkeep().getMultMods().containsKey("ind_core");
    }

    private static PersonAPI officer() {
        PersonAPI person = Global.getSector().getPlayerFaction().createRandomPerson();
        person.setPersonality(Personalities.STEADY);
        Global.getSector().getPlayerFleet().getFleetData().addOfficer(person);
        return person;
    }

    private static void toggle(Ctx c, Setting setting, String id, MarketAPI market, boolean expectBuildable, String name) throws Exception {
        Industry industry = market.instantiateIndustry(id);
        c.expect(industry.isAvailableToBuild() == expectBuildable, name + " buildable " + expectBuildable);
        c.expect(industry.showWhenUnavailable() == expectBuildable, name + " shown " + expectBuildable);
    }

    private static Set<String> restock(MarketAPI market, int times) {
        Set<String> seen = new HashSet<>();
        BaseSubmarketPlugin plugin = (BaseSubmarketPlugin) market.getSubmarket(Ids.PETMARKET).getPlugin();
        for (int i = 0; i < times; i++) {
            plugin.setSinceSWUpdate(1000f);
            plugin.updateCargoPrePlayerInteraction();
            for (CargoStackAPI stack : market.getSubmarket(Ids.PETMARKET).getCargo().getStacksCopy()) {
                if (stack.isSpecialStack()) seen.add(stack.getSpecialDataIfSpecial().getData());
            }
        }
        return seen;
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            Colony.pair(c, 6);
            MarketAPI a = c.market("a");
            MarketAPI b = c.market("b");

            Colony.setting(Setting.ENGHUB, false);
            toggle(c, Setting.ENGHUB, Ids.ENGHUB, a, false, "801 hub toggle off:");
            Colony.setting(Setting.ENGHUB, true);
            toggle(c, Setting.ENGHUB, Ids.ENGHUB, a, true, "801 hub toggle on:");

            Colony.setting(Setting.MUSEUM_ENABLED, false);
            Colony.setting(Setting.METEOR_ENABLED, true);
            toggle(c, Setting.MUSEUM_ENABLED, Ids.MUSEUM, a, false, "901 museum off, meteors on:");
            Colony.setting(Setting.MUSEUM_ENABLED, true);
            Colony.setting(Setting.METEOR_ENABLED, false);
            toggle(c, Setting.MUSEUM_ENABLED, Ids.MUSEUM, a, true, "901 museum on, meteors off:");
            Colony.setting(Setting.METEOR_ENABLED, true);

            a.addIndustry(Ids.ACADEMY);
            a.reapplyIndustries();
            Academy academy = (Academy) a.getIndustry(Ids.ACADEMY);
            int base = academy.getOfficerTrainingDays();
            academy.setAICoreId(Commodities.BETA_CORE);
            a.reapplyIndustries();
            int beta = academy.getOfficerTrainingDays();
            c.expect(base - beta == 31, "beta core shortens training 31 days (" + base + " -> " + beta + ")");
            academy.setAICoreId(Commodities.GAMMA_CORE);
            a.reapplyIndustries();
            c.expect(academy.getOfficerTrainingDays() == base, "106 gamma core drops the beta reduction");
            c.expect(hasCoreUpkeep(academy), "gamma core cuts upkeep");
            academy.setAICoreId(Commodities.BETA_CORE);
            a.reapplyIndustries();
            c.expect(!hasCoreUpkeep(academy), "106 beta core drops the gamma upkeep change");
            academy.setAICoreId(null);
            a.reapplyIndustries();

            CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
            fleet.getCargo().getCredits().add(100000f);
            float credits = fleet.getCargo().getCredits().get();
            PersonAPI person = officer();

            academy.setOfficerForTraining(person, academy.getReachablePersonalities(person).get(0));
            c.expect(academy.getOfficerInTraining() == person, "officer went into training");
            c.near(credits - fleet.getCargo().getCredits().get(), 0f, 1f, "own academy training is free");
            MarketAPI galatia = Global.getSector().getEconomy().getMarket(Ids.ACADEMY_MARKET_ID);
            Academy npc = galatia == null ? null : (Academy) galatia.getIndustry(Ids.ACADEMY);
            c.expect(npc != null && npc.getOfficerTrainingFee(1) == Setting.PERSONALITY_TRAINING_COST.intValue(), "102 npc academy charges the training fee");

            int max = fleet.getCommander().getStats().getOfficerNumber().getModifiedInt();
            while (fleet.getFleetData().getOfficersCopy().size() < max) officer();
            c.expect(!Academy.playerHasOfficerCapacity(), "104 no capacity at the limit (" + max + ")");
            fleet.getFleetData().removeOfficer(fleet.getFleetData().getOfficersCopy().get(0).getPerson());
            c.expect(Academy.playerHasOfficerCapacity(), "104 capacity one below the limit");

            b.addIndustry(Ids.COMFORGE);
            b.reapplyIndustries();
            Industry forge = b.getIndustry(Ids.COMFORGE);
            forge.setAICoreId(Commodities.BETA_CORE);
            b.reapplyIndustries();
            c.expect(hasCoreUpkeep(forge), "beta core forge without VPC has the upkeep cut");
            forge.setSpecialItem(VPCItemPlugin.createItem(VPCItemPlugin.Tier.COMMON, List.of(Commodities.SUPPLIES)));
            b.reapplyIndustries();
            c.expect(!hasCoreUpkeep(forge), "302 installing a VPC ends the upkeep cut");
        });
        steps.add(c -> {
            MarketAPI a = c.market("a");
            MarketAPI b = c.market("b");
            a.addIndustry(Industries.MILITARYBASE);
            b.addIndustry(Industries.MILITARYBASE);
            a.getIndustry(Industries.MILITARYBASE).setSpecialItem(new SpecialItemData(ItemIds.TRANSMITTER, null));
            b.getIndustry(Industries.MILITARYBASE).setSpecialItem(new SpecialItemData(ItemIds.TRANSMITTER, null));
            a.reapplyIndustries();
            b.reapplyIndustries();
            MemoryAPI memory = Global.getSector().getMemoryWithoutUpdate();
            c.expect(memory.getBoolean(SpecialItemEffectsRepo.TRANSMITTER_UNLOCK_KEY), "transmitters unlock the relay");
            a.getIndustry(Industries.MILITARYBASE).setSpecialItem(null);
            a.reapplyIndustries();
            c.expect(memory.getBoolean(SpecialItemEffectsRepo.TRANSMITTER_UNLOCK_KEY), "3002 relay stays on with one transmitter left");
            b.getIndustry(Industries.MILITARYBASE).setSpecialItem(null);
            b.reapplyIndustries();
            c.expect(!memory.getBoolean(SpecialItemEffectsRepo.TRANSMITTER_UNLOCK_KEY), "relay off with no transmitter");
        });
        steps.add(c -> {
            MarketAPI b = c.market("b");
            b.addIndustry(Ids.MUSEUM);
            b.getIndustry(Ids.MUSEUM).startBuilding();
            b.reapplyIndustries();
            b.removeIndustry(Ids.MUSEUM, null, false);
            b.reapplyIndustries();
            c.expect(!b.hasIndustry(Ids.MUSEUM), "903 cancelling museum construction does not crash");

            b.addIndustry(Ids.MUSEUM);
            b.reapplyIndustries();
            c.days(1f);
        });
        steps.add(c -> {
            MarketAPI b = c.market("b");
            c.expect(b.hasSubmarket(Ids.MUSEUM_SUBMARKET), "museum adds its storage");
            CargoAPI cargo = b.getSubmarket(Ids.MUSEUM_SUBMARKET).getCargo();
            cargo.initMothballedShips(Global.getSector().getPlayerFaction().getId());
            FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "wolf_Assault");
            cargo.getMothballedShips().addFleetMember(member);
            c.put("ship", member.getId());
            b.removeIndustry(Ids.MUSEUM, null, false);
            b.reapplyIndustries();
            c.days(2f);
        });
        steps.add(c -> {
            MarketAPI b = c.market("b");
            c.expect(!b.hasSubmarket(Ids.MUSEUM_SUBMARKET), "903 exhibition storage gone with the museum");
            CargoAPI storage = Misc.getStorageCargo(b);
            storage.initMothballedShips(Global.getSector().getPlayerFaction().getId());
            boolean found = false;
            for (FleetMemberAPI member : storage.getMothballedShips().getMembersListCopy()) found |= member.getId().equals(c.get("ship"));
            c.expect(found, "903 museum ships went to storage");

            b.addIndustry(Ids.PET_STORE);
            b.reapplyIndustries();
            c.days(2f);
        });
        steps.add(c -> {
            MarketAPI b = c.market("b");
            c.expect(b.hasSubmarket(Ids.PETMARKET), "animal center opens the animal market");
            Set<String> seen = restock(b, 800);
            c.note("pets seen in stock: " + seen.size());
            for (String pet : new String[]{"tammy", "bugdog", "schafunschaf", "jungleshark", "slaghound", "goopuppies", "voidsquid", "kaysaar", "paranautic_gebb"}) {
                c.expect(seen.contains(pet), "1005 rare pet appears in stock: " + pet);
            }

            com.fs.starfarer.api.campaign.econ.SubmarketAPI market = b.getSubmarket(Ids.PETMARKET);
            float normal = market.getPlugin().getTariff();
            b.getIndustry(Ids.PET_STORE).setAICoreId(Commodities.BETA_CORE);
            c.expect(market.getPlugin().getTariff() < normal, "1004 beta core lowers the animal market tariff");
            b.getIndustry(Ids.PET_STORE).setAICoreId(null);

            b.removeIndustry(Ids.PET_STORE, null, false);
            b.reapplyIndustries();
            c.days(2f);
        });
        steps.add(c -> {
            c.expect(!c.market("b").hasSubmarket(Ids.PETMARKET), "1001 animal market closes with the animal center");

            MarketAPI a = c.market("a");
            a.addIndustry(Ids.REPAIRDOCKS);
            a.reapplyIndustries();
            RestorationDocks docks = (RestorationDocks) a.getIndustry(Ids.REPAIRDOCKS);
            float base = docks.getBaseRepairPrice();
            docks.setAICoreId(Commodities.BETA_CORE);
            a.reapplyIndustries();
            c.near(docks.getBaseRepairPrice(), base * 0.85f, 0.001f, "1605 beta core tooltip price is the lower one");
            docks.setAICoreId(null);
            a.reapplyIndustries();
            c.days(1f);
        });
        steps.add(c -> {
            MarketAPI a = c.market("a");
            RestorationDocks docks = (RestorationDocks) a.getIndustry(Ids.REPAIRDOCKS);
            FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "onslaught_Elite");
            String[] dmods = {"comp_armor", "comp_hull", "degraded_engines", "faulty_grid", "comp_structure", "glitched_sensors", "fragile_subsystems", "erratic_injector", "degraded_life_support", "degraded_shields"};
            member.setVariant(member.getVariant().clone(), false, false);
            for (String dmod : dmods) member.getVariant().addPermaMod(dmod);
            CargoAPI storage = a.getSubmarket(Ids.REPSTORAGE).getCargo();
            storage.initMothballedShips(Global.getSector().getPlayerFaction().getId());
            storage.getMothballedShips().addFleetMember(member);
            c.put("dmods", docks.getNumNonBuiltInDMods(member.getVariant()));
            c.put("docksShip", member.getId());
            c.note("repair budget " + docks.getMaxDModRepairAmt() + ", d-mods " + c.get("dmods"));
            Global.getSector().getPlayerFleet().getCargo().getCredits().add(5000000f);
            c.days(33f);
        });
        steps.add(c -> {
            MarketAPI a = c.market("a");
            RestorationDocks docks = (RestorationDocks) a.getIndustry(Ids.REPAIRDOCKS);
            CargoAPI storage = a.getSubmarket(Ids.REPSTORAGE).getCargo();
            for (FleetMemberAPI member : storage.getMothballedShips().getMembersListCopy()) {
                if (!member.getId().equals(c.get("docksShip"))) continue;
                int left = docks.getNumNonBuiltInDMods(member.getVariant());
                int removed = (Integer) c.get("dmods") - left;
                c.expect(removed == docks.getMaxDModRepairAmt(), "1604 monthly repairs match the tooltip count (removed " + removed + ", tooltip " + docks.getMaxDModRepairAmt() + ")");
            }
        });
        return steps;
    }
}
