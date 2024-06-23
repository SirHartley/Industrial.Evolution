package indevo.industries.changeling.industry.population;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.campaign.listeners.BaseIndustryOptionProvider;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest;
import com.fs.starfarer.api.impl.campaign.econ.impl.PopulationAndInfrastructure;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.industries.changeling.hullmods.HandBuiltHullmod;
import indevo.industries.changeling.industry.SubIndustry;
import indevo.industries.changeling.industry.SubIndustryData;
import indevo.industries.changeling.listener.ShipProductionSummaryMessageHandler;
import indevo.utils.ModPlugin;
import indevo.utils.helper.Misc;
import indevo.utils.helper.Settings;
import indevo.utils.helper.StringHelper;

import java.util.List;
import java.util.Random;

public class WarhammerSubIndustry extends SubIndustry implements EconomyTickListener {

    /*
Forge World
x Randomly builds known frigates and destroyers, rarely cruisers (with a special hullmod)*
x 2k flat income for every ship hull produced here
x 5k additional production budget per ship hull produced here
x industrial industries always operate as if at 100% hazard as long as there is no shortage

x accumulates unrest (up to 3) if population demands are not met
x higher base pop demands
x rural industries are 2x more expensive to run
x immediate pollution
x can only be built on very hot worlds
*/

    public static final float FLAT_INCOME_PER_HULL = 2000f;
    public static final float FLAT_PRODUCTION_BUDGET_PER_HULL = 5000f;
    public static final float RURAL_BUILDING_UPKEEP_MULT = 2f;
    public static final int BASE_DP_PER_MONTH = 1;
    public static final float BUILD_CHANCE_PER_MONTH = 0.15f;

    public int currentDpBudget = 0;
    public Random random = new Random();

    public static class WarhammerTooltipAdder extends BaseIndustryOptionProvider {

        public static void register() {
            ListenerManagerAPI manager = Global.getSector().getListenerManager();
            if (!manager.hasListenerOfClass(WarhammerTooltipAdder.class))
                manager.addListener(new WarhammerTooltipAdder(), true);
        }

        @Override
        public boolean isUnsuitable(Industry ind, boolean allowUnderConstruction) {
            return !isSuitable(ind);
        }

        public boolean isSuitable(Industry ind) {
            Industry pop = ind.getMarket().getIndustry(Industries.POPULATION);
            boolean isTarget = pop instanceof SwitchablePopulation && ((SwitchablePopulation) pop).getCurrent() instanceof WarhammerSubIndustry;
            return !Settings.getBoolean(Settings.GOVERNMENT_LARP_MODE) && isTarget;
        }

        @Override
        public void addToIndustryTooltip(Industry ind, Industry.IndustryTooltipMode mode, TooltipMakerAPI tooltip, float width, boolean expanded) {
            if (isUnsuitable(ind, true)) return;
            float opad = 10f;

            boolean military = ind.getSpec().getTags().contains("industrial");
            boolean rural = ind.getSpec().getTags().contains("rural");

            tooltip.addSectionHeading("Governance Effects: Forge World", Alignment.MID, opad);

            if (military) {
                tooltip.addPara("Industrial buildings: %s", opad, com.fs.starfarer.api.util.Misc.getTextColor(), com.fs.starfarer.api.util.Misc.getPositiveHighlightColor(), "hazard rating nullified");
            } else if (rural) {
                tooltip.addPara("Rural buildings: %s increased by %s", opad, com.fs.starfarer.api.util.Misc.getTextColor(), com.fs.starfarer.api.util.Misc.getPositiveHighlightColor(), "upkeep", StringHelper.getAbsPercentString(RURAL_BUILDING_UPKEEP_MULT, false));
            } else {
                tooltip.addPara("No effect on this building.", opad);
            }
        }
    }


    public WarhammerSubIndustry(SubIndustryData data) {
        super(data);
    }

    @Override
    public void apply() {
        ((SwitchablePopulation) industry).superApply();

        WarhammerTooltipAdder.register();
        Global.getSector().getListenerManager().addListener(this);

        if (((SwitchablePopulation) industry).locked && !market.hasCondition(Conditions.POLLUTION))
            market.addCondition(Conditions.POLLUTION);

        int size = market.getSize();
        SwitchablePopulation population = ((SwitchablePopulation) industry);

        population.demand(Commodities.METALS, size);
        population.demand(Commodities.RARE_METALS, size - 3);

        Pair<String, Integer> deficit = population.getMaxDeficit(Commodities.LUXURY_GOODS);
        if (deficit.two <= 0) {
            market.getStability().modifyFlat(getModId(), 1, "Construction material demand met");
        } else {
            market.getStability().modifyFlat(getModId(), -1, "Construction material shortage");
        }

        increaseIncomeAndBudgetForHullOutput();

        for (Industry ind : market.getIndustries()) {
            if (!hasDeficit(ind) && ind.getSpec().getTags().contains("industrial")) {
                ind.getUpkeep().modifyMult(getModId(), 1f / PopulationAndInfrastructure.getUpkeepHazardMult(market.getHazardValue()), getName() + " hazard upkeep red.");
            }

            if (ind.getSpec().getTags().contains("rural")) {
                ind.getUpkeep().modifyMult(getModId(), RURAL_BUILDING_UPKEEP_MULT, getName());
            }
        }
    }

    @Override
    public void unapply() {
        super.unapply();
        Global.getSector().getListenerManager().removeListener(this);

        industry.getIncome().unmodify(getModId());
        market.getStability().unmodify(getModId());
        Global.getSector().getPlayerStats().getDynamic().getMod("custom_production_mod").unmodify(getModId());

        for (Industry ind : market.getIndustries()) {
            ind.getUpkeep().unmodify(getModId());
            ind.getIncome().unmodify(getModId());
        }
    }

    private void increaseIncomeAndBudgetForHullOutput() {
        int output = market.getCommodityData(Commodities.SHIPS).getMaxSupply();

        for (Industry ind : market.getIndustries()) {
            MutableCommodityQuantity supply = ind.getSupply(Commodities.SHIPS);
            if (supply.getQuantity().getModifiedInt() > 0)
                ind.getIncome().modifyFlat(getModId(), supply.getQuantity().getModifiedInt() * FLAT_INCOME_PER_HULL, getName() + " - ship hull exports (" + output + ")");
        }

        Global.getSector().getPlayerStats().getDynamic().getMod("custom_production_mod").modifyFlat(getModId(), output * FLAT_PRODUCTION_BUDGET_PER_HULL, getName() + " - ship hull exports x" + output);
    }

    @Override
    public boolean isAvailableToBuild() {
        return super.isAvailableToBuild() && market.hasCondition(Conditions.VERY_HOT) && market.getPrimaryEntity() instanceof PlanetAPI && !market.getPrimaryEntity().hasTag(Tags.GAS_GIANT);
    }

    @Override
    public String getUnavailableReason() {
        if (!market.hasCondition(Conditions.VERY_HOT)) return "This planet is not hot enough.";
        if (!(market.getPrimaryEntity() instanceof PlanetAPI)) return "Unavailable on stations";
        if (market.getPrimaryEntity().hasTag(Tags.GAS_GIANT)) return "Unavailable on gas giants";
        return super.getUnavailableReason();
    }

    @Override
    public void addRightAfterDescription(TooltipMakerAPI tooltip, Industry.IndustryTooltipMode mode) {
        super.addRightAfterDescription(tooltip, mode);
        if (hasDeficit(industry))
            tooltip.addPara("Unrest is building due to shortages.", com.fs.starfarer.api.util.Misc.getNegativeHighlightColor(), 10f);
    }

    @Override
    public void reportEconomyTick(int iterIndex) {
    }

    @Override
    public void reportEconomyMonthEnd() {
        if (hasDeficit(industry) & RecentUnrest.get(market).getPenalty() < 5) {
            if (!market.hasSpaceport()) return;

            RecentUnrest.get(market).add(1, getName() + ": various shortages");
            Global.getSector().getCampaignUI().addMessage("The shortage of some required commodities at %s is causing %s.",
                    Global.getSettings().getColor("standardTextColor"), market.getName(), "unrest", com.fs.starfarer.api.util.Misc.getHighlightColor(), com.fs.starfarer.api.util.Misc.getNegativeHighlightColor());
        }

        if (!market.isPlayerOwned()) return;

        currentDpBudget += getDpPerMonth();

        if (random.nextFloat() < BUILD_CHANCE_PER_MONTH) {
            WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(random);

            SettingsAPI settings = Global.getSettings();
            for (String id : market.getFaction().getKnownShips()) {
                ShipHullSpecAPI spec = settings.getHullSpec(id);
                float dp = spec.getSuppliesToRecover();

                if (dp <= currentDpBudget) picker.add(id);
            }

            if (Global.getSettings().isDevMode()) {
                ModPlugin.log("budget: " + currentDpBudget);
                for (String s : picker.getItems()) ModPlugin.log(s);
            }

            String id = picker.pick();
            if (id == null || id.isEmpty()) {
                Global.getLogger(this.getClass()).error("Failed to pick a ship to build for " + market.getName());
                return;
            }
            FleetMemberAPI member = createAndPrepareMember(id, 4);
            if (member == null) return;
            currentDpBudget -= (int) member.getDeploymentPointsCost();

            member.getVariant().addPermaMod(HandBuiltHullmod.ID);

            CargoAPI cargo = Misc.getStorageCargo(market);
            cargo.initMothballedShips(market.getFactionId());
            cargo.getMothballedShips().addFleetMember(member);

            ShipProductionSummaryMessageHandler.getInstanceOrRegister().add(market, member);
        }
    }

    public FleetMemberAPI createAndPrepareMember(String hullID, int maxDmodAmt) {
        List<String> l = Global.getSettings().getHullIdToVariantListMap().get(hullID);
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
        picker.addAll(l);
        ShipVariantAPI variant;

        try {
            variant = Global.getSettings().getVariant(picker.pick());
            if (variant == null)
                variant = Global.getSettings().createEmptyVariant(com.fs.starfarer.api.util.Misc.genUID(), Global.getSettings().getHullSpec(hullID));
        } catch (Exception e) {
            Global.getLogger(this.getClass()).error("Failed to create variant for " + hullID);
            return null;
        }

        FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant);

        variant = variant.clone();
        variant.setOriginalVariant(null);

        int dModsAlready = DModManager.getNumDMods(variant);
        int dmods = maxDmodAmt > 0 ? Math.max(0, random.nextInt(maxDmodAmt) - dModsAlready) : 0;

        if (dmods > 0) DModManager.setDHull(variant);

        member.setVariant(variant, false, true);

        if (dmods > 0) DModManager.addDMods(member, false, dmods, random);

        member.setVariant(variant, true, true);
        member.updateStats();

        float retain = 1f / maxDmodAmt;
        FleetEncounterContext.prepareShipForRecovery(member, true, true, false, retain, retain, random);
        member.getVariant().autoGenerateWeaponGroups();

        member.updateStats();
        return member;
    }

    public boolean hasDeficit(Industry industry) {
        boolean hasDeficit = false;
        for (Pair<String, Integer> entry : industry.getAllDeficit())
            if (entry.two > 0) {
                hasDeficit = true;
                break;
            }

        return hasDeficit;
    }

    public String getModId() {
        return ((SwitchablePopulation) industry).getModId();
    }

    public int getDpPerMonth() {
        return market.getSize() - 3 + BASE_DP_PER_MONTH;
    }
}