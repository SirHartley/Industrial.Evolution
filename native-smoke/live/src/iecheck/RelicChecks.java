package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.Personalities;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.util.Misc;
import indevo.dialogue.research.listeners.CorruptedNanoforgeChecker;
import indevo.economy.listeners.RelicBuildTimeReductionButtonAdder;
import indevo.economy.listeners.TechMiningRelicOptionProvider;
import indevo.economy.plugins.RelicSpeedUpDialog;
import indevo.ids.Ids;
import indevo.ids.ItemIds;
import indevo.industries.academy.industry.Academy;
import indevo.industries.engineeringhub.memory.RecoveredHullmods;
import indevo.industries.orbitalworks.scripts.OrbitalWorksSlotManager;
import indevo.industries.senate.conditions.EdictAPI;
import indevo.utils.helper.MiscIE;
import indevo.utils.helper.RelicHelper;

import java.util.ArrayList;
import java.util.List;

public final class RelicChecks {
    private static final String LATTICE = "IndEvo_relicLattice";

    private RelicChecks() {
    }

    private static float storage(MarketAPI market) {
        return MiscIE.getStorageCargo(market).getCommodityQuantity(ItemIds.RARE_PARTS);
    }

    private static float fleet() {
        return Global.getSector().getPlayerFleet().getCargo().getCommodityQuantity(ItemIds.RARE_PARTS);
    }

    private static int unrestAfterRelicRemoval(MarketAPI market, String edict) {
        market.addCondition(edict);
        market.reapplyConditions();
        int before = RecentUnrest.getPenalty(market);
        ((EdictAPI) market.getCondition(edict).getPlugin()).removeEarlyWithRelics();
        market.reapplyConditions();
        return RecentUnrest.getPenalty(market) - before;
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            Colony.pair(c, 6);
            MarketAPI a = c.market("a");
            CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
            cargo.removeCommodity(ItemIds.RARE_PARTS, fleet());
            cargo.addCommodity(ItemIds.RARE_PARTS, 100f);
            MiscIE.getStorageCargo(a).addCommodity(ItemIds.RARE_PARTS, 50f);
            RelicHelper.take(a, 80);
            c.near(storage(a), 0f, 0.01f, "relic payment drains storage first");
            c.near(fleet(), 70f, 0.01f, "relic payment takes the rest from the fleet");
            boolean threw = false;
            try {
                RelicHelper.take(a, 1000);
            } catch (IllegalStateException e) {
                threw = true;
            }
            c.expect(threw && fleet() == 70f, "relic payment refuses when short and takes nothing");

            cargo.addCommodity(ItemIds.RARE_PARTS, 2000f);
            a.addIndustry(Industries.LIGHTINDUSTRY);
            a.addIndustry(Industries.MINING);
            a.getIndustry(Industries.LIGHTINDUSTRY).startBuilding();
            a.getIndustry(Industries.MINING).startBuilding();
            MarketAPI b = c.market("b");
            b.addIndustry(Industries.LIGHTINDUSTRY);
            b.getIndustry(Industries.LIGHTINDUSTRY).startBuilding();

            RelicBuildTimeReductionButtonAdder.ConstructionTarget light = new RelicBuildTimeReductionButtonAdder.ConstructionTarget((BaseIndustry) a.getIndustry(Industries.LIGHTINDUSTRY));
            int left = light.getSpeedUpDaysLeft();
            float relics = fleet();
            c.expect(RelicSpeedUpDialog.apply(light), "construction speed up applies");
            c.expect(Math.abs(light.getSpeedUpDaysLeft() - (left - left / 2)) <= 1, "construction speed up halves days (" + left + " -> " + light.getSpeedUpDaysLeft() + ")");
            c.near(relics - fleet(), left, 0.01f, "construction speed up costs 1 relic per day left");
            c.expect(!RelicSpeedUpDialog.isAvailable(new RelicBuildTimeReductionButtonAdder.ConstructionTarget((BaseIndustry) a.getIndustry(Industries.MINING))), "construction cooldown blocks a second build on the colony");
            c.expect(RelicSpeedUpDialog.isAvailable(new RelicBuildTimeReductionButtonAdder.ConstructionTarget((BaseIndustry) b.getIndustry(Industries.LIGHTINDUSTRY))), "construction cooldown leaves the other colony alone");
        });
        steps.add(c -> {
            MarketAPI a = c.market("a");
            MarketAPI b = c.market("b");
            b.addIndustry(Ids.SENATE);
            b.reapplyIndustries();
            c.expect(unrestAfterRelicRemoval(a, "IndEvo_edict_lockdown") == 1, "lockdown relic removal adds only the finish unrest");
            c.expect(!a.hasCondition("IndEvo_edict_lockdown"), "lockdown gone after relic removal");
            c.expect(unrestAfterRelicRemoval(a, "IndEvo_edict_conscription") == 0, "forced labour relic removal adds no early unrest");

            a.addIndustry(Industries.ORBITALWORKS);
            a.reapplyIndustries();
            c.expect(OrbitalWorksSlotManager.isWorking(a), "orbital works functional");
            FleetMemberAPI eagle = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "eagle_Assault");
            int cap = Misc.getMaxPermanentMods(eagle, Global.getSector().getPlayerStats());
            c.put("eagle", eagle.getId());
            c.put("cap", cap);
            OrbitalWorksSlotManager manager = OrbitalWorksSlotManager.getInstanceOrRegister();
            manager.start(a, eagle);
            manager.getJob(a).daysDone = OrbitalWorksSlotManager.DAYS_REQUIRED - 1;

            a.addCondition("ruins_widespread");
            a.addIndustry(Industries.TECHMINING);
            a.reapplyIndustries();
            a.getStats().getDynamic().getStat(Stats.TECH_MINING_MULT).modifyMult(TechMiningRelicOptionProvider.MOD_ID, TechMiningRelicOptionProvider.FIND_MULT);
            a.getMemoryWithoutUpdate().set(TechMiningRelicOptionProvider.MEM_BOOST, true);

            Global.getSector().getPlayerFaction().removeKnownHullMod("heavyarmor");
            RecoveredHullmods.record(a, Global.getSettings().getVariant("phaeton_Standard"));
            c.expect(RecoveredHullmods.getLearnable(a).containsKey("heavyarmor"), "engineering hub records an unknown hullmod");
            boolean dmod = false;
            for (String id : RecoveredHullmods.getLearnable(a).keySet()) dmod |= Global.getSettings().getHullModSpec(id).hasTag("dmod");
            c.expect(!dmod, "recorded list has no d-mods");

            Global.getSector().getPlayerFleet().getCargo().addSpecial(new SpecialItemData(Items.CORRUPTED_NANOFORGE, null), 1);

            a.addIndustry(Ids.ACADEMY);
            a.reapplyIndustries();
            Academy academy = (Academy) a.getIndustry(Ids.ACADEMY);
            academy.setAICoreId(Commodities.ALPHA_CORE);
            a.reapplyIndustries();
            PersonAPI person = Global.getSector().getPlayerFaction().createRandomPerson();
            person.setPersonality(Personalities.CAUTIOUS);
            Global.getSector().getPlayerFleet().getFleetData().addOfficer(person);
            c.expect(academy.getReachablePersonalities(person).contains(Personalities.AGGRESSIVE), "alpha core makes two steps reachable");
            academy.setOfficerForTraining(person, Personalities.AGGRESSIVE);
            c.expect(academy.getCurrentOfficerTrainingSteps() == 2, "two step training started");
            c.expect(academy.getRemainingOfficerTrainingDays() == 2 * academy.getOfficerTrainingDays(), "two step training takes twice the days (" + academy.getRemainingOfficerTrainingDays() + ")");
            c.days(3f);
        });
        steps.add(c -> {
            MarketAPI a = c.market("a");
            FleetMemberAPI eagle = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "eagle_Assault");
            FleetMemberAPI done = null;
            for (FleetMemberAPI m : MiscIE.getStorageCargo(a).getMothballedShips().getMembersListCopy()) {
                if (m.getId().equals(c.get("eagle"))) done = m;
            }
            c.expect(OrbitalWorksSlotManager.getInstanceOrRegister().getJob(a) == null, "lattice job ends at 93 days");
            c.expect(done != null && done.getVariant().hasHullMod(LATTICE), "latticed ship in storage with the hullmod");
            if (done != null) c.expect(Misc.getMaxPermanentMods(done, Global.getSector().getPlayerStats()) == (Integer) c.get("cap") + 1, "lattice adds one s-mod slot");
            c.expect(CorruptedNanoforgeChecker.isDone(), "corrupted nanoforge unlocks project reforge");

            OrbitalWorksSlotManager.getInstanceOrRegister().start(a, eagle);
            c.put("eagle2", eagle.getId());
            c.reload();
        });
        steps.add(c -> {
            MarketAPI a = c.market("a");
            c.expect(OrbitalWorksSlotManager.getInstanceOrRegister().getJob(a) != null, "lattice job survives reload");
            c.expect(TechMiningRelicOptionProvider.isBoosted(a), "tech mining boost survives reload");
            Academy academy = (Academy) a.getIndustry(Ids.ACADEMY);
            c.expect(academy.getCurrentOfficerTrainingSteps() == 2, "two step training survives reload");
            c.expect(RecoveredHullmods.getLearnable(a).containsKey("heavyarmor"), "recorded hullmods survive reload");
            a.removeIndustry(Industries.ORBITALWORKS, null, false);
            c.days(2f);
        });
        steps.add(c -> {
            MarketAPI a = c.market("a");
            c.expect(OrbitalWorksSlotManager.getInstanceOrRegister().getJob(a) == null, "orbital works removal ends the lattice job");
            boolean inStorage = false;
            for (FleetMemberAPI m : MiscIE.getStorageCargo(a).getMothballedShips().getMembersListCopy()) {
                if (m.getId().equals(c.get("eagle2"))) inStorage = true;
            }
            c.expect(inStorage, "orbital works removal puts the ship in storage");
            c.until(() -> !TechMiningRelicOptionProvider.isBoosted(c.market("a")), 35f, "tech mining boost removed at month end");
        });
        steps.add(c -> {
            MarketAPI a = c.market("a");
            c.expect(a.getStats().getDynamic().getStat(Stats.TECH_MINING_MULT).getMultStatMod(TechMiningRelicOptionProvider.MOD_ID) == null, "tech mining boost modifier gone at month end");
        });
        return steps;
    }
}
