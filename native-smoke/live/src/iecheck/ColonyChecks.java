package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathBaseManager;
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.industries.Supercomputer;
import indevo.industries.changeling.industry.SubIndustryData;
import indevo.industries.changeling.conditions.GovernmentCondition;
import indevo.industries.changeling.industry.Specialization;
import indevo.industries.changeling.industry.mining.MiningSpecializations;
import indevo.industries.changeling.industry.population.Governments;
import indevo.industries.changeling.industry.population.UnderworldSubIndustry;
import indevo.industries.changeling.plugins.ChangelingPopulationOptionProvider;
import indevo.industries.embassy.AmbassadorItemHelper;
import indevo.industries.embassy.listeners.AmbassadorItemTrackerPlugin;
import indevo.industries.embassy.rules.IndEvo_playerHasFreeEmbassy;
import indevo.industries.embassy.scripts.HAAmbassadorEventFactor;
import indevo.industries.relay.plugins.NetworkEntry;
import indevo.utils.helper.ReflectionUtils;

import java.util.ArrayList;
import java.util.List;

public final class ColonyChecks {
    private ColonyChecks() {
    }

    static void government(MarketAPI market, String id) {
        SubIndustryData data = Governments.get(id);
        if (data == null) throw new IllegalArgumentException("No government " + id);
        GovernmentCondition.set(market, data);
    }

    private static float flatMod(MutableStat stat, String key) {
        MutableStat.StatMod mod = stat.getFlatMods().get(key);
        return mod == null ? 0f : mod.getValue();
    }

    static void size(MarketAPI market, int from, int to) {
        market.removeCondition("population_" + from);
        market.addCondition("population_" + to);
        market.setSize(to);
        market.reapplyConditions();
        market.reapplyIndustries();
    }

    private static float supply(Industry industry, String commodity) {
        return industry.getSupply(commodity).getQuantity().getModifiedValue();
    }

    private static boolean hasSupercomputerBonus(MarketAPI market) {
        for (MutableStat.StatMod mod : market.getIncomeMult().getMultMods().values()) {
            if (mod.getDesc() != null && mod.getDesc().contains("Supercomputer")) return true;
        }
        return false;
    }

    private static float flat(MarketAPI market, String stat) {
        return market.getStats().getDynamic().getMod(stat).computeEffective(0f);
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            Colony.pair(c, 3);
            government(c.market("a"), "underworld");
            c.days(8f);
        });
        steps.add(c -> {
            MarketAPI a = c.market("a");
            MarketAPI b = c.market("b");
            Industry population = a.getIndustry(Industries.POPULATION);
            ChangelingPopulationOptionProvider provider = new ChangelingPopulationOptionProvider();
            c.expect(GovernmentCondition.isLocked(a), "government locks after 7 days");
            c.expect(!GovernmentCondition.canChange(a), "402 locked government can not change at size 3");
            c.expect(!provider.isSuitable(population), "402 no change government option on a locked government");
            c.expect(!population.getClass().getName().startsWith("indevo."), "population plugin is not replaced (" + population.getClass().getName() + ")");
            c.expect(a.hasCondition(GovernmentCondition.ID), "underworld sits on the market as the government condition");
            c.expect("Underworld Governance".equals(population.getCurrentName()), "population shows the government name (" + population.getCurrentName() + ")");
            c.expect(!a.getCondition(GovernmentCondition.ID).getPlugin().showIcon(), "government condition shows no icon");
            String govImage = GovernmentCondition.getGovernment(a).getImageName(a);
            c.expect(govImage.equals(population.getCurrentImage()), "size 3 population shows the government image through the patch (" + population.getCurrentImage() + ")");
            c.expect(GovernmentCondition.getDescriptionOverride(population) != null, "population tooltip takes the government description");
            c.near(flatMod(a.getStability(), "_ind_population_3_ms"), UnderworldSubIndustry.BASE_STABILITY_AMT, 0.01f, "underworld base stability replaces the vanilla base value");
            c.expect(!a.getStability().getFlatMods().containsKey("ind_population_0"), "underworld drops the domestic goods stability bonus");
            c.expect(provider.isSuitable(b.getIndustry(Industries.POPULATION)), "base government still offers the option at size 3");

            size(b, 3, 5);
            int maxBefore = Misc.getMaxMarketSize(b);
            float light = flat(b, Stats.PATROL_NUM_LIGHT_MOD);
            float medium = flat(b, Stats.PATROL_NUM_MEDIUM_MOD);
            float heavy = flat(b, Stats.PATROL_NUM_HEAVY_MOD);

            government(b, "helldivers");
            c.expect(Misc.getMaxMarketSize(b) == 5, "managed democracy caps size at 5 (max " + Misc.getMaxMarketSize(b) + ", before " + maxBefore + ")");
            c.near(flat(b, Stats.PATROL_NUM_LIGHT_MOD) - light, 2f, 0.01f, "403 size 5 managed democracy has 2 small patrols");
            c.near(flat(b, Stats.PATROL_NUM_MEDIUM_MOD) - medium, 1f, 0.01f, "403 and 1 medium patrol");
            c.near(flat(b, Stats.PATROL_NUM_HEAVY_MOD) - heavy, 1f, 0.01f, "403 and 1 large patrol");

            government(b, "rural");
            c.expect(Misc.getMaxMarketSize(b) == maxBefore, "404 leaving managed democracy removes the size cap (max " + Misc.getMaxMarketSize(b) + ")");
            c.near(flat(b, Stats.PATROL_NUM_LIGHT_MOD), light, 0.01f, "patrol bonus gone after leaving managed democracy");
        });
        steps.add(c -> {
            MarketAPI b = c.market("b");
            b.addIndustry(Industries.FARMING);
            b.getIndustry(Industries.FARMING).supply("ie_check", Commodities.FOOD, 6, "check");
            b.addIndustry(Industries.LIGHTINDUSTRY);
            b.setFreePort(true);
            b.reapplyConditions();
            b.reapplyIndustries();
            for (int i = 0; i < 5; i++) Global.getSector().getEconomy().tripleStep();
            b.reapplyIndustries();
            Industry farming = b.getIndustry(Industries.FARMING);
            Industry light = b.getIndustry(Industries.LIGHTINDUSTRY);
            c.expect(supply(farming, Commodities.ORGANICS) > 0f, "rural polity farming makes organics (" + supply(farming, Commodities.ORGANICS) + ")");
            c.expect(supply(farming, Commodities.DRUGS) > 0f, "rural polity farming makes drugs on a free port (" + supply(farming, Commodities.DRUGS) + ")");
            c.expect(supply(light, Commodities.SUPPLIES) > 0f, "rural polity light industry makes supplies (" + supply(light, Commodities.SUPPLIES) + ")");

            b.setFreePort(false);
            b.reapplyConditions();
            b.reapplyIndustries();
            c.near(supply(farming, Commodities.DRUGS), 0f, 0.01f, "408 free port off takes the drugs away");

            government(b, "base_population_and_infrastructure");
            c.near(supply(b.getIndustry(Industries.FARMING), Commodities.ORGANICS), 0f, 0.01f, "408 leaving rural polity takes organics away");
            c.near(supply(b.getIndustry(Industries.LIGHTINDUSTRY), Commodities.SUPPLIES), 0f, 0.01f, "408 leaving rural polity takes supplies away");
            c.near(supply(b.getIndustry(Industries.FARMING), Commodities.DRUGS), 0f, 0.01f, "408 no drugs after leaving rural polity");
        });
        steps.add(c -> {
            MarketAPI b = c.market("b");
            c.expect(!b.hasCondition(GovernmentCondition.ID), "base government removes the government condition");
            c.expect(b.getIndustry(Industries.POPULATION).getCurrentName().equals(b.getIndustry(Industries.POPULATION).getSpec().getName()), "base government clears the name override (" + b.getIndustry(Industries.POPULATION).getCurrentName() + ")");

            float baseInterest = LuddicPathBaseManager.getLuddicPathMarketInterest(b);
            government(b, "rural");
            float ruralInterest = LuddicPathBaseManager.getLuddicPathMarketInterest(b);
            c.expect(ruralInterest < baseInterest - 5f, "rural polity lowers pather interest through the patch (" + baseInterest + " -> " + ruralInterest + ")");
            government(b, "base_population_and_infrastructure");

            b.addIndustry(Industries.MINING);
            b.reapplyIndustries();
            Industry mining = b.getIndustry(Industries.MINING);
            String key = Specialization.getModId(mining);
            Specialization.MINING.setCurrent(b, Specialization.MINING.get("ore_mining"));
            c.near(flatMod(mining.getSupply(Commodities.ORE).getQuantity(), key), 2f, 0.01f, "ore mining adds 2 ore on the vanilla mine");
            c.near(flatMod(mining.getSupply(Commodities.VOLATILES).getQuantity(), key), -2f, 0.01f, "ore mining takes 2 volatiles");
            c.expect("Ore Mining".equals(mining.getCurrentName()), "specialized mine shows the variant name (" + mining.getCurrentName() + ")");
            c.expect(Specialization.MINING.get("ore_mining").imageName.equals(mining.getCurrentImage()), "specialized mine shows the variant image (" + mining.getCurrentImage() + ")");
            c.expect(Specialization.getDescriptionOverride(mining) != null, "specialized mine tooltip takes the variant description");
            c.expect(mining.getClass().getName().startsWith("com.fs.starfarer.api"), "mining plugin is not replaced (" + mining.getClass().getName() + ")");

            Specialization.MINING.setCurrent(b, Specialization.MINING.get(MiningSpecializations.BASE_ID));
            c.expect(!mining.getSupply(Commodities.ORE).getQuantity().getFlatMods().containsKey(key), "base mining removes the ore delta");
            boolean leftover = false;
            for (String id : Specialization.MINING.commodities) {
                leftover |= mining.getSupply(id).getQuantity().getFlatMods().containsKey(key) || mining.getDemand(id).getQuantity().getFlatMods().containsKey(key);
            }
            c.expect(!leftover, "base mining removes the malus on lines the mine does not produce");
            c.expect(mining.getSpec().getName().equals(mining.getCurrentName()), "base mining clears the name override (" + mining.getCurrentName() + ")");
            b.removeIndustry(Industries.MINING, null, false);
        });
        steps.add(c -> {
            MarketAPI a = c.market("a");
            MarketAPI b = c.market("b");
            a.addIndustry(Ids.SUPCOM);
            Supercomputer computer = (Supercomputer) a.getIndustry(Ids.SUPCOM);
            ReflectionUtils.set("lastApplied", computer, Commodities.GAMMA_CORE);
            a.reapplyIndustries();
            c.expect(hasSupercomputerBonus(b), "supercomputer income bonus reaches the other colony");
            a.removeIndustry(Ids.SUPCOM, null, false);
            a.reapplyIndustries();
            b.reapplyIndustries();
            c.expect(!hasSupercomputerBonus(a) && !hasSupercomputerBonus(b), "1602 bonus gone after removing the supercomputer");
        });
        steps.add(c -> {
            MarketAPI a = c.market("a");
            StatBonus size = a.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT);
            float before = new NetworkEntry(a).baseFleetSize;
            size.modifyPercent("ie_check", 25f);
            float after = new NetworkEntry(a).baseFleetSize;
            size.unmodifyPercent("ie_check");
            c.near(after, before, 0.001f, "1201 percent fleet size mod does not move the relay comparison");
        });
        steps.add(c -> {
            MarketAPI a = c.market("a");
            a.addIndustry(Ids.EMBASSY);
            a.reapplyIndustries();
            IndEvo_playerHasFreeEmbassy command = new IndEvo_playerHasFreeEmbassy();
            c.expect(command.execute("check", null, null, null), "free embassy with no ambassador in cargo");

            PersonAPI person = Global.getFactory().createPerson();
            person.setFaction(Factions.HEGEMONY);
            Global.getSector().getImportantPeople().addPerson(person);
            SpecialItemData item = AmbassadorItemHelper.createItem(person);
            Global.getSector().getPlayerFleet().getCargo().addSpecial(item, 1);
            AmbassadorItemTrackerPlugin tracker = new AmbassadorItemTrackerPlugin(person, item);
            Global.getSector().getListenerManager().addListener(tracker);
            c.expect(!command.execute("check", null, null, null), "705 second hire refused while the first ambassador is in cargo");
            Global.getSector().getListenerManager().removeListener(tracker);
            Global.getSector().getPlayerFleet().getCargo().removeItems(com.fs.starfarer.api.campaign.CargoAPI.CargoItemType.SPECIAL, item, 1);
            Global.getSector().getImportantPeople().removePerson(person);
        });
        steps.add(c -> {
            c.note("hostile activity intel " + HostileActivityEventIntel.get());
            if (HostileActivityEventIntel.get() == null) {
                c.days(3f);
            }
        });
        steps.add(c -> {
            HostileActivityEventIntel intel = HostileActivityEventIntel.get();
            c.expect(intel != null, "hostile activity event exists");
            Global.getSector().getFaction(Factions.LUDDIC_CHURCH).setRelationship(Factions.PLAYER, 0.4f);
            Global.getSector().getFaction(Factions.HEGEMONY).setRelationship(Factions.PLAYER, 0.4f);
            float church = HAAmbassadorEventFactor.getReductionAmtForFaction(Global.getSector().getFaction(Factions.LUDDIC_CHURCH));
            float hegemony = HAAmbassadorEventFactor.getReductionAmtForFaction(Global.getSector().getFaction(Factions.HEGEMONY));
            for (com.fs.starfarer.api.impl.campaign.intel.events.EventFactor factor : intel.getFactors()) {
                if (factor instanceof com.fs.starfarer.api.impl.campaign.intel.events.BaseHostileActivityFactor) {
                    c.note("factor " + factor.getClass().getSimpleName() + " threat name " + ((com.fs.starfarer.api.impl.campaign.intel.events.BaseHostileActivityFactor) factor).getNameForThreatList(false) + " progress " + factor.getProgress(intel));
                }
            }
            c.note("ambassador reduction church " + church + ", hegemony " + hegemony);
            c.market("a").addCondition(Conditions.LUDDIC_MAJORITY);
            c.market("a").reapplyConditions();
            c.days(1f);
        });
        steps.add(c -> {
            float church = HAAmbassadorEventFactor.getReductionAmtForFaction(Global.getSector().getFaction(Factions.LUDDIC_CHURCH));
            c.expect(church > 0f, "703 luddic church ambassador lowers hostile activity (" + church + " points)");
        });
        return steps;
    }
}
