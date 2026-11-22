package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial;
import com.fs.starfarer.api.util.Misc;
import indevo.exploration.meteor.entities.IceTreasureoidEntity;
import indevo.exploration.salvage.specials.CreditStashSpecial;
import indevo.ids.Ids;
import indevo.industries.RestorationDocks;
import indevo.industries.academy.industry.Academy;
import indevo.industries.assembler.industry.VariableAssembler;
import indevo.industries.changeling.conditions.GovernmentCondition;
import indevo.industries.changeling.industry.population.WarhammerSubIndustry;
import indevo.industries.derelicts.industry.HullForge;
import indevo.industries.salvageyards.plugins.YardsContract;
import indevo.utils.helper.ExtModeHelper;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

// survival mode factors: every changed number in normal mode, then again with the sector flag on
public final class SurvivalChecks {
    private static final String HULL = "onslaught";
    private static final int ROLLS = 20;

    private SurvivalChecks() {
    }

    private static Object invoke(MethodHandle handle, Object... args) throws Exception {
        try {
            return handle.invokeWithArguments(args);
        } catch (Exception | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static MethodHandle method(Class<?> owner, String name, Class<?> ret, Class<?>... args) throws Exception {
        return MethodHandles.privateLookupIn(owner, MethodHandles.lookup()).findVirtual(owner, name, MethodType.methodType(ret, args));
    }

    private static MethodHandle setter(Class<?> owner, String name, Class<?> type) throws Exception {
        return MethodHandles.privateLookupIn(owner, MethodHandles.lookup()).findSetter(owner, name, type);
    }

    private static float hireMult(Academy academy) throws Exception {
        return (Float) invoke(method(Academy.class, "getHireChanceMult", float.class, String.class), academy, "ext_officersAvailableFraction");
    }

    private static Map<String, Float> sample(Ctx c) throws Exception {
        Map<String, Float> v = new HashMap<>();
        MarketAPI a = c.market("a");
        MarketAPI b = c.market("b");
        ShipHullSpecAPI hull = Global.getSettings().getHullSpec(HULL);
        FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "onslaught_Elite");

        HullForge forge = (HullForge) a.getIndustry(Ids.HULLFORGE);
        v.put("forge", (Float) invoke(method(HullForge.class, "getConstructionCost", float.class, ShipHullSpecAPI.class), forge, hull));
        v.put("scrap", YardsContract.getScrapValue(member));
        v.put("fuel", (float) VariableAssembler.getDepositAmount(Commodities.FUEL, 1000, 0f, 1, 1f));
        v.put("marines", (float) VariableAssembler.getDepositAmount(Commodities.MARINES, 1000, 0f, 1, 1f));
        v.put("docks", RestorationDocks.getSingleDModRepairCost(member, 3, 1f));
        v.put("stored", Academy.getStoredOfficerSalaryMult());
        v.put("idle", Misc.getOfficerInactiveSalaryMult());
        v.put("hireOwned", hireMult(Academy.get(a)));
        b.setPlayerOwned(false);
        v.put("hireNpc", hireMult(Academy.get(b)));
        b.setPlayerOwned(true);
        v.put("env", ExtModeHelper.mult("ext_environmentalDamageMult"));
        v.put("quality", ExtModeHelper.mod("ext_shipForSaleQualityMod"));

        CreditStashSpecial stash = new CreditStashSpecial();
        CreditStashSpecial.CreditStashSpecialData data = new CreditStashSpecial.CreditStashSpecialData();
        data.creditAmt = 10000f;
        invoke(setter(CreditStashSpecial.class, "data", CreditStashSpecial.CreditStashSpecialData.class), stash, data);
        v.put("stash", (Float) invoke(method(CreditStashSpecial.class, "getPayout", float.class), stash));
        return v;
    }

    // stock variants are shared instances, a d-mod there shows on every ship of that variant
    private static int stockDMods() {
        int total = 0;
        for (String id : Global.getSettings().getHullIdToVariantListMap().get(HULL)) {
            ShipVariantAPI variant = Global.getSettings().getVariant(id);
            if (variant == null) continue;
            total += DModManager.getNumDMods(variant);
            if (variant.getHullSpec().isDHull()) total++;
        }
        return total;
    }

    private static void forgeWorld(Ctx c, String mode) {
        WarhammerSubIndustry forge = (WarhammerSubIndustry) GovernmentCondition.getGovernment(c.market("b"));
        forge.init(GovernmentCondition.getPopulation(c.market("b")));
        forge.random = new Random(7);
        int stock = stockDMods();
        int dmods = 0;
        boolean shared = false;
        for (int i = 0; i < ROLLS; i++) {
            FleetMemberAPI member = forge.createAndPrepareMember(HULL);
            dmods += DModManager.getNumDMods(member.getVariant());
            for (String id : Global.getSettings().getHullIdToVariantListMap().get(HULL)) {
                if (member.getVariant() == Global.getSettings().getVariant(id)) shared = true;
            }
        }
        c.note(mode + ": Forge World " + ROLLS + " ships, " + dmods + " d-mods, quality " + c.market("b").getShipQualityFactor() + " + " + ExtModeHelper.mod("ext_shipForSaleQualityMod"));
        c.expect(stockDMods() == stock, mode + ": stock " + HULL + " variants keep their d-mods after Forge World builds (" + stock + " before, " + stockDMods() + " after)");
        c.expect(!shared, mode + ": Forge World ships never use the stock variant instance");
    }

    private static void iceWrecks(Ctx c, String mode) throws Exception {
        SectorEntityToken anchor = c.market("a").getPrimaryEntity();
        MethodHandle entity = setter(BaseCustomEntityPlugin.class, "entity", SectorEntityToken.class);
        int free = 0;
        int none = 0;
        int other = 0;
        for (int i = 0; i < ROLLS; i++) {
            IceTreasureoidEntity ice = new IceTreasureoidEntity();
            invoke(entity, ice, anchor);
            ice.random = new Random(i);
            ice.spawnWreck();
            Object special = Misc.getSalvageSpecial(ice.relatedWreck);
            if (special == null) none++;
            else if (special instanceof ShipRecoverySpecial.ShipRecoverySpecialData data && "caught in ice, now freed".equals(data.desc)) free++;
            else other++;
            anchor.getContainingLocation().removeEntity(ice.relatedWreck);
        }
        c.note(mode + ": ice wrecks " + free + " free recovery, " + none + " story point only, recoverable fraction " + Misc.getDerelictRecoverableFraction());
        c.put("iceFree" + mode, free);
        c.expect(other == 0, mode + ": ice wrecks get only the vanilla battle wreck specials (" + other + " other)");
        c.expect(none > 0, mode + ": some ice wrecks need a story point");
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            Global.getSector().setExtMode(false);
            Colony.pair(c, 6);
            MarketAPI a = c.market("a");
            MarketAPI b = c.market("b");
            a.addIndustry(Ids.HULLFORGE);
            a.addIndustry(Ids.ACADEMY);
            b.addIndustry(Ids.ACADEMY);
            ColonyChecks.government(b, "warhammer");
            a.reapplyIndustries();
            b.reapplyIndustries();

            c.expect(!Misc.isExtended(), "normal mode at the start");
            Map<String, Float> n = sample(c);
            c.note("normal: " + n);
            c.expect(n.get("env") == 1f && n.get("quality") == 0f, "normal: helper factors are neutral");
            c.near(n.get("fuel"), 1000f, 0.5f, "normal: VPC fuel deposit unchanged");
            c.near(n.get("marines"), 1000f, 0.5f, "normal: VPC marine deposit unchanged");
            c.near(n.get("stash"), 10000f, 0.5f, "normal: Credit Stash pays the full amount");
            c.near(n.get("stored"), n.get("idle"), 0.0001f, "normal: stored officers draw the vanilla idle share " + n.get("idle"));
            c.near(n.get("hireOwned"), 1f, 0.0001f, "normal: own Academy hire chance unchanged");
            c.near(n.get("hireNpc"), 1f, 0.0001f, "normal: other Academy hire chance unchanged");
            forgeWorld(c, "normal");
            iceWrecks(c, "normal");

            Global.getSector().setExtMode(true);
            c.expect(Misc.isExtended(), "survival mode after the flag flip");
            Map<String, Float> s = sample(c);
            c.note("survival: " + s);
            float capital = Global.getSettings().optFloatFromArray("ext_shipBuyPriceMult", 3, 1f);
            float fuel = Global.getSettings().getFloat("ext_fuelPriceMult");
            float marines = Global.getSettings().getFloat("ext_marinesPriceMult");
            float parts = Global.getSettings().getFloat("ext_partsPriceMult");
            float hire = Global.getSettings().getFloat("ext_officersAvailableFraction");
            c.near(s.get("forge"), n.get("forge") * capital, 1f, "survival: Hull Forge cost x" + capital + " for a capital");
            c.near(s.get("scrap"), n.get("scrap") * capital, 1f, "survival: Salvage Yards scrap value x" + capital + " for a capital");
            c.near(s.get("fuel"), Math.round(1000f / fuel), 0.5f, "survival: VPC fuel deposit / " + fuel);
            c.near(s.get("marines"), Math.round(1000f / marines), 0.5f, "survival: VPC marine deposit / " + marines);
            c.near(s.get("docks"), n.get("docks") * parts, 1f, "survival: Restoration Docks d-mod cost x" + parts);
            c.near(s.get("stash"), 3000f, 0.5f, "survival: Credit Stash pays 30%");
            c.near(s.get("stored"), s.get("idle"), 0.0001f, "survival: stored officers draw the vanilla idle share " + s.get("idle"));
            c.near(s.get("hireOwned"), 1f, 0.0001f, "survival: own Academy hire chance unchanged");
            c.near(s.get("hireNpc"), hire, 0.0001f, "survival: other Academy hire chance x" + hire);
            c.near(s.get("env"), Global.getSettings().getFloat("ext_environmentalDamageMult"), 0.0001f, "survival: meteor damage factor from ext_environmentalDamageMult");
            c.near(s.get("quality"), Global.getSettings().getFloat("ext_shipForSaleQualityMod"), 0.0001f, "survival: Forge World quality mod from ext_shipForSaleQualityMod");
            forgeWorld(c, "survival");
            iceWrecks(c, "survival");

            Global.getSector().setExtMode(false);
            c.expect(!Misc.isExtended(), "normal mode again at the end");
        });
        return steps;
    }
}
