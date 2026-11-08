package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.industries.salvageyards.industry.SalvageYards;
import indevo.industries.senate.EdictHelper;
import indevo.industries.senate.dialogue.EdictPanelDelegate;
import indevo.industries.senate.conditions.Edict_FastBuild;
import indevo.utils.helper.ReflectionUtils;
import indevo.utils.timers.TimeTracker;

import java.util.ArrayList;
import java.util.List;

public final class SenateChecks {
    private static final String CONSCRIPTION = "IndEvo_edict_conscription";
    private static final String RELOCATION = "IndEvo_edict_forced_reloc";
    private static final String OPEN = "IndEvo_edict_open_market";

    private SenateChecks() {
    }

    private static List<String> penaltyTags(MarketAPI market) {
        List<String> tags = new ArrayList<>();
        for (String tag : market.getTags()) if (tag.startsWith("edict_stab_penalty")) tags.add(tag);
        return tags;
    }

    private static float penalty(MarketAPI market, String id) {
        MutableStat.StatMod mod = market.getStability().getFlatStatMod(market.getCondition(id).getIdForPluginModifications());
        return mod == null ? 0f : mod.getValue();
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            Colony.pair(c, 6);
            MarketAPI a = c.market("a");
            MarketAPI b = c.market("b");
            b.addIndustry(Ids.SENATE);
            a.addIndustry(Industries.LIGHTINDUSTRY);
            a.reapplyIndustries();

            Industry light = a.getIndustry(Industries.LIGHTINDUSTRY);
            float organics = light.getDemand(Commodities.ORGANICS).getQuantity().getModifiedValue();
            a.addCondition(Edict_FastBuild.ID);
            a.reapplyConditions();
            a.reapplyIndustries();
            float halved = light.getDemand(Commodities.ORGANICS).getQuantity().getModifiedValue();
            c.near(halved, organics * 0.5f, 0.01f, "1502 fast build halves demand without the +1");
            a.removeCondition(Edict_FastBuild.ID);
            a.reapplyConditions();
            a.reapplyIndustries();
            c.near(light.getDemand(Commodities.ORGANICS).getQuantity().getModifiedValue(), organics, 0.01f, "demand back after fast build");
        });
        steps.add(c -> {
            MarketAPI a = c.market("a");
            MarketAPI b = c.market("b");
            List<com.fs.starfarer.api.campaign.PlanetAPI> planets = new ArrayList<>(a.getStarSystem().getPlanets());
            MarketAPI third = null;
            for (com.fs.starfarer.api.campaign.PlanetAPI planet : planets) {
                if (!planet.isStar() && planet.getMarket() != null && planet.getMarket().isPlanetConditionMarketOnly()) {
                    third = Colony.make(planet, 3);
                    break;
                }
            }
            c.expect(third != null, "third colony in the system");
            ColonyChecks.size(a, 6, 5);
            a.addCondition(RELOCATION);
            a.reapplyConditions();
            MarketConditionAPI condition = a.getCondition(RELOCATION);
            PopulationComposition incoming = new PopulationComposition();
            ((MarketImmigrationModifier) condition.getPlugin()).modifyIncoming(b, incoming);
            float growth = 0f;
            for (MutableStat.StatMod mod : incoming.getWeight().getFlatMods().values()) growth += mod.getValue();
            c.near(growth, 25f, 0.01f, "1503 size 5 with 2 others moves 25 growth");
            a.removeCondition(RELOCATION);
            a.reapplyConditions();
        });
        steps.add(c -> {
            MarketAPI a = c.market("a");
            a.addIndustry(Industries.MILITARYBASE);
            a.reapplyIndustries();
            a.addCondition(CONSCRIPTION);
            a.reapplyConditions();
            c.note("conscription tags " + penaltyTags(a) + ", penalty " + penalty(a, CONSCRIPTION));
            c.put("penalty", penalty(a, CONSCRIPTION));
            c.expect(penalty(a, CONSCRIPTION) < 0f, "conscription starts with a stability penalty");
            c.days(65f);
        });
        steps.add(c -> {
            MarketAPI a = c.market("a");
            c.note("after 2 months: tags " + penaltyTags(a) + ", penalty " + penalty(a, CONSCRIPTION));
            c.expect(penaltyTags(a).size() == 1, "3901 one penalty tag only");
            c.expect(penalty(a, CONSCRIPTION) < 0f, "3901 penalty has not dropped to 0 after 2 months");
            c.expect(penalty(a, CONSCRIPTION) > (Float) c.get("penalty"), "penalty shrinks by month");
            ((indevo.industries.senate.conditions.BaseEdict) a.getCondition(CONSCRIPTION).getPlugin()).removeWithoutPenalty();
            a.reapplyConditions();
            c.expect(penaltyTags(a).isEmpty(), "3901 ending conscription clears the tag");
            a.addCondition(CONSCRIPTION);
            a.reapplyConditions();
            c.expect(penalty(a, CONSCRIPTION) == (Float) c.get("penalty"), "3901 enacted again it starts with its penalty");
            ((indevo.industries.senate.conditions.BaseEdict) a.getCondition(CONSCRIPTION).getPlugin()).removeWithoutPenalty();
            a.reapplyConditions();
        });
        steps.add(c -> {
            MarketAPI a = c.market("a");
            a.addCondition(OPEN);
            a.reapplyConditions();
            c.days(5f);
        });
        steps.add(c -> {
            MarketAPI a = c.market("a");
            c.put("passed", TimeTracker.getTimeTagPassed(a, a.getCondition(OPEN).getIdForPluginModifications()));
            c.note("edict days before reloads " + c.get("passed"));
            c.reload();
        });
        steps.add(c -> c.reload());
        steps.add(c -> c.reload());
        steps.add(c -> c.days(5f));
        steps.add(c -> {
            MarketAPI a = c.market("a");
            int passed = TimeTracker.getTimeTagPassed(a, a.getCondition(OPEN).getIdForPluginModifications());
            int before = (Integer) c.get("passed");
            c.expect(Math.abs(passed - before - 5) <= 1, "3902 edict days count 1 per day over 3 reloads (" + before + " -> " + passed + " in 5 days)");
        });
        steps.add(c -> {
            MarketAPI a = c.market("a");
            MarketAPI b = c.market("b");
            c.expect(EdictHelper.getColoniesCoveredBy(b).contains(a), "edict panel: the senate covers the other colony in its system");

            boolean refused = false;
            try {
                EdictHelper.enact(a, CONSCRIPTION);
            } catch (IllegalStateException e) {
                refused = true;
            }
            c.expect(refused && !a.hasCondition(CONSCRIPTION), "edict panel: a second edict on one colony is refused");

            java.util.Set<String> tags = new java.util.HashSet<>(a.getTags());
            int conditions = a.getConditions().size();
            new EdictPanelDelegate(a);
            new EdictPanelDelegate(b);
            c.expect(new java.util.HashSet<>(a.getTags()).equals(tags) && a.getConditions().size() == conditions, "edict panel: previews leave the colony untouched");
        });
        steps.add(c -> {
            MarketAPI a = c.market("a");
            StarSystemAPI system = a.getStarSystem();
            int base = Misc.getNumStableLocations(system);
            CustomCampaignEntityAPI relay = system.addCustomEntity(null, null, Entities.COMM_RELAY, Factions.NEUTRAL);
            relay.setCircularOrbit(system.getStar(), 10f, 3000f, 100f);
            relay.addTag(Tags.OBJECTIVE);
            int withRelay = Misc.getNumStableLocations(system);
            c.expect(withRelay == base + 1, "a normal objective counts as a stable location (" + base + " -> " + withRelay + ")");
            CustomCampaignEntityAPI tower = system.addCustomEntity(null, null, Entities.COMM_RELAY, Factions.NEUTRAL);
            tower.setCircularOrbit(system.getStar(), 200f, 3000f, 100f);
            tower.addTag(Tags.OBJECTIVE);
            tower.addTag(Ids.TAG_WATCHTOWER);
            c.expect(Misc.getNumStableLocations(system) == withRelay, "watchtowers do not count as stable locations (" + Misc.getNumStableLocations(system) + ")");
        });
        steps.add(c -> {
            MarketAPI b = c.market("b");
            int used = Misc.getNumIndustries(b);
            b.addIndustry(Ids.PIRATEHAVEN);
            c.expect(Misc.getNumIndustries(b) == used + 2, "privateer base takes two industry slots (" + used + " -> " + Misc.getNumIndustries(b) + ")");
            b.removeIndustry(Ids.PIRATEHAVEN, null, false);
            c.expect(Misc.getNumIndustries(b) == used, "both slots free again");
            b.getConstructionQueue().addToEnd(Ids.PIRATEHAVEN, 0);
            c.expect(Misc.getNumIndustries(b) == used + 2, "queued privateer base reserves two slots (" + Misc.getNumIndustries(b) + ")");
            b.getConstructionQueue().removeItem(Ids.PIRATEHAVEN);

            int max = Misc.getMaxIndustries(b);
            b.getStats().getDynamic().getMod(Stats.MAX_INDUSTRIES).modifyFlat("ie_check", used + 1 - max);
            c.expect(!b.instantiateIndustry(Ids.PIRATEHAVEN).isAvailableToBuild(), "one free slot: privateer base not buildable");
            b.getStats().getDynamic().getMod(Stats.MAX_INDUSTRIES).modifyFlat("ie_check", used + 2 - max);
            c.expect(b.instantiateIndustry(Ids.PIRATEHAVEN).isAvailableToBuild(), "two free slots: privateer base buildable");
            b.getStats().getDynamic().getMod(Stats.MAX_INDUSTRIES).unmodifyFlat("ie_check");

            b.addIndustry(Industries.PLANETARYSHIELD);
            Industry shield = b.getIndustry(Industries.PLANETARYSHIELD);
            shield.setAICoreId(Commodities.ALPHA_CORE);
            shield.setImproved(true);
            b.reapplyIndustries();
            float ground = b.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).computeEffective(100f);
            b.addIndustry("IndEvo_CloudPainter");
            b.reapplyIndustries();
            Industry after = b.getIndustry(Industries.PLANETARYSHIELD);
            c.expect(after != null && Commodities.ALPHA_CORE.equals(after.getAICoreId()), "1803 shield keeps its core next to a cloud painter");
            c.expect(after != null && after.isImproved(), "1803 shield keeps its improvement");
            c.near(b.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).computeEffective(100f), ground, 0.5f, "1803 ground defense unchanged (" + ground + ")");
        });
        steps.add(c -> {
            MarketAPI a = c.market("a");
            a.addIndustry(Ids.SCRAPYARD);
            a.reapplyIndustries();
            c.days(2f);
        });
        steps.add(c -> {
            MarketAPI a = c.market("a");
            SalvageYards yards = (SalvageYards) a.getIndustry(Ids.SCRAPYARD);
            c.put("points", ReflectionUtils.get("availableSalvagePoints", yards));
            c.note("salvage points before raids " + c.get("points"));
            FakeRaids.Raid first = new FakeRaids.Raid(a.getStarSystem());
            Global.getSector().getIntelManager().addIntel(first, true);
            FakeRaids.Raid second = new FakeRaids.Raid(a.getStarSystem());
            Global.getSector().getIntelManager().addIntel(second, true);
            FakeRaids.Blockade blockade = FakeRaids.blockade(a.getStarSystem(), a);
            Global.getSector().getIntelManager().addIntel(blockade, true);
            c.days(2f);
        });
        steps.add(c -> {
            SalvageYards yards = (SalvageYards) c.market("a").getIndustry(Ids.SCRAPYARD);
            int points = (Integer) ReflectionUtils.get("availableSalvagePoints", yards);
            int before = (Integer) c.get("points");
            c.note("salvage points after raids " + points);
            c.expect(points - before >= 3 * 30, "1401 1407 two raids and a blockade all paid (" + (points - before) + ")");
            c.put("paid", points);
            c.reload();
        });
        steps.add(c -> c.days(2f));
        steps.add(c -> {
            SalvageYards yards = (SalvageYards) c.market("a").getIndustry(Ids.SCRAPYARD);
            int points = (Integer) ReflectionUtils.get("availableSalvagePoints", yards);
            // pirate activity in the system adds 3 a day, a repeated raid payout adds at least 30
            c.expect(points - (Integer) c.get("paid") < 30, "1401 no double payout after a reload (" + c.get("paid") + " -> " + points + ")");
        });
        return steps;
    }
}
