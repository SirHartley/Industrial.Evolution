package indevo.industries.changeling.industry.population;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SubmarketPlugin;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.BaseIndustryOptionProvider;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.PlayerFleetPersonnelTracker;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.industries.changeling.hullmods.HandBuiltHullmod;
import indevo.industries.changeling.industry.SubIndustry;
import indevo.industries.changeling.industry.SubIndustryData;
import indevo.industries.changeling.listener.ShipProductionSummaryMessageHandler;
import indevo.utils.ModPlugin;
import indevo.utils.helper.Misc;
import indevo.utils.helper.Settings;
import indevo.utils.helper.StringHelper;
import org.lazywizard.lazylib.MathUtils;

import java.util.List;
import java.util.Random;

import static com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathBaseManager.AI_CORE_ADMIN_INTEREST;
import static com.fs.starfarer.api.impl.campaign.population.CoreImmigrationPluginImpl.getWeightForMarketSizeStatic;

public class MonasticOrderSubIndustry extends SubIndustry implements EconomyTickListener {

    /*
Monastic Orders
•	x-50% positive income
•	xNo luddic cells
•	xStability can’t fall below 3 or go above 7
•	xLocked at size 5
•	xMilitary industries -upkeep
•   xMarines stored here passively gather experience
•	Randomly builds known frigates and destroyers, rarely cruisers (by hand with a special hullmod)*/

    public static final int MAX_STAB = 7;
    public static final int MIN_STAB = 3;
    public static final float INCOME_RED = 0.5f;
    public static final int MAX_MARKET_SIZE = 5;
    public static final float MONASTIC_UPKEEP_RED = 0.7f;

    public static final float MAX_MARINES_AMT = 500f;
    public static final float MARINES_GAIN_EXP = 3f;

    public static final int BASE_DP_PER_MONTH = 1;
    public static final float BUILD_CHANCE_PER_MONTH = 0.3f;

    public int getDpPerMonth() {
        return market.getSize() - 3 + BASE_DP_PER_MONTH;
    }

    public int currentDpBudget = 0;
    public Random random = new Random();

    public static class MonasticOrderTooltipAdder extends BaseIndustryOptionProvider {

        public static void register() {
            ListenerManagerAPI manager = Global.getSector().getListenerManager();
            if (!manager.hasListenerOfClass(MonasticOrderTooltipAdder.class))
                manager.addListener(new MonasticOrderTooltipAdder(), true);
        }

        @Override
        public boolean isUnsuitable(Industry ind, boolean allowUnderConstruction) {
            return !isSuitable(ind);
        }

        public boolean isSuitable(Industry ind) {
            Industry pop = ind.getMarket().getIndustry(Industries.POPULATION);
            boolean isTarget = pop instanceof SwitchablePopulation && ((SwitchablePopulation) pop).getCurrent() instanceof MonasticOrderSubIndustry;
            return !Settings.getBoolean(Settings.GOVERNMENT_LARP_MODE) && isTarget;
        }

        @Override
        public void addToIndustryTooltip(Industry ind, Industry.IndustryTooltipMode mode, TooltipMakerAPI tooltip, float width, boolean expanded) {
            if (isUnsuitable(ind, true)) return;
            float opad = 10f;

            boolean military = ind.getSpec().getTags().contains("military");

            tooltip.addSectionHeading("Governance Effects: Monastic Orders", Alignment.MID, opad);

            if (military) {
                tooltip.addPara("Military buildings: %s decreased by %s", opad, com.fs.starfarer.api.util.Misc.getTextColor(), com.fs.starfarer.api.util.Misc.getPositiveHighlightColor(), "upkeep", StringHelper.getAbsPercentString(MONASTIC_UPKEEP_RED, true));
            } else {
                tooltip.addPara("No effect on this building.", opad);
            }
        }
    }

    @Override
    public void reportEconomyTick(int iterIndex) {
        if (!market.isPlayerOwned()) return;

        PlayerFleetPersonnelTracker.PersonnelAtEntity e = PlayerFleetPersonnelTracker.getInstance().getDroppedOffAt(Commodities.MARINES, market.getPrimaryEntity(), com.fs.starfarer.api.util.Misc.getStorage(market).getSubmarket(), true);

        if (e != null) {
            SubmarketPlugin cargo = com.fs.starfarer.api.util.Misc.getStorage(market);

            float num = e.data.num;
            if (num == 0f && cargo.getCargo().getMarines() > 0) {
                e.data.add(cargo.getCargo().getMarines());
                num = e.data.num;
            }

            float addition = calculateExperienceBonus(Math.round(num));;
            e.data.addXP(addition);

            ModPlugin.log("amt " + addition + " current " + e.data.xp + " num marines " + e.data.num);
        }

        int lastIterInMonth = (int) Global.getSettings().getFloat("economyIterPerMonth") - 1;

        if (iterIndex != lastIterInMonth) {
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
                currentDpBudget -= member.getDeploymentPointsCost();

                member.getVariant().addPermaMod(HandBuiltHullmod.ID);

                CargoAPI cargo = Misc.getStorageCargo(market);
                cargo.initMothballedShips(market.getFactionId());
                cargo.getMothballedShips().addFleetMember(member);

                ShipProductionSummaryMessageHandler.getInstanceOrRegister().add(market, member);
            }
        }
    }

    public static float calculateExperienceBonus(int numMarines) {
        // Maximum bonus percentage when there are 500 or fewer marines

        if (false){
            float maxBonusPercentagePerTick = MARINES_GAIN_EXP;

            if (numMarines <= MAX_MARINES_AMT) {
                return maxBonusPercentagePerTick;
            } else {
                float relativeNumMarines = MAX_MARINES_AMT / numMarines;
                float bonusPercentage = maxBonusPercentagePerTick * relativeNumMarines;
                return Math.min(bonusPercentage, maxBonusPercentagePerTick);
            }
        }

        return MARINES_GAIN_EXP;
    }

    @Override
    public void reportEconomyMonthEnd() {

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

    public MonasticOrderSubIndustry(SubIndustryData data) {
        super(data);
    }

    @Override
    public void apply() {
        ((SwitchablePopulation) industry).superApply();

        MonasticOrderTooltipAdder.register();
        Global.getSector().getListenerManager().addListener(this);
        correctStability();

        market.getIncomeMult().modifyMult(((SwitchablePopulation) industry).getModId(), INCOME_RED, getName());
        if (market.getSize() >= MAX_MARKET_SIZE)
            market.getPopulation().setWeight(getWeightForMarketSizeStatic(market.getSize()));

        for (Industry ind : market.getIndustries()) {
            if (ind.getSpec().getTags().contains("military")) {
                ind.getUpkeep().modifyMult(getId(), MONASTIC_UPKEEP_RED, getName());
            }
        }

        //MarineExperience handling
    }

    @Override
    public boolean isAvailableToBuild() {
        //return super.isAvailableToBuild() && market.getSize() <= MAX_MARKET_SIZE && market.getPrimaryEntity() instanceof PlanetAPI && !market.getPrimaryEntity().hasTag(Tags.GAS_GIANT);
        return Global.getSettings().isDevMode();
    }

    @Override
    public String getUnavailableReason() {
        if (market.getSize() > MAX_MARKET_SIZE) return "This planet is too populated";
        if (!(market.getPrimaryEntity() instanceof PlanetAPI)) return "Unavailable on stations";
        if (market.getPrimaryEntity().hasTag(Tags.GAS_GIANT)) return "Unavailable on gas giants";
        return super.getUnavailableReason();
    }

    @Override
    public void unapply() {
        super.unapply();
        String id = ((SwitchablePopulation) industry).getModId();
        market.getStability().unmodify(id);
        market.getIncomeMult().unmodify(id);

        Global.getSector().getListenerManager().removeListener(this);

        for (Industry ind : market.getIndustries()) {
            if (ind.getSpec().getTags().contains("military")) {
                ind.getUpkeep().unmodify(getId());
            }
        }
    }

    public void correctStability() {
        int current = market.getStability().getModifiedInt();
        int absDiff = Math.abs(MathUtils.clamp(current, MIN_STAB, MAX_STAB) - current);
        int mod = current > MAX_STAB ? absDiff * -1 : current < MIN_STAB ? absDiff : 0;
        market.getStability().modifyFlat(((SwitchablePopulation) industry).getModId(), mod, "Base value");
    }

    //negate pather interest
    @Override
    public float getPatherInterest(Industry industry) {
        return -getLuddicPathMarketInterest(industry.getMarket());
    }

    public static float getLuddicPathMarketInterest(MarketAPI market) {
        if (market.getFactionId().equals(Factions.LUDDIC_PATH)) return 0f;
        float total = 0f;

        String aiCoreId = market.getAdmin().getAICoreId();
        if (aiCoreId != null) {
            total += AI_CORE_ADMIN_INTEREST;
        }

        for (Industry ind : market.getIndustries()) {
            // Wisp: Fixes an infinite loop with TASC, which _also_ calls getPatherInterest on industries.
            // <https://fractalsoftworks.com/forum/index.php?topic=18011.msg416817#msg416817>
            if (ind instanceof SwitchablePopulation || ind.getId().equals("BOGGLED_CHAMELEON")) continue;
            total += ind.getPatherInterest();
        }

        if (total > 0) {
            total += new Random(market.getName().hashCode()).nextFloat() * 0.1f;
        }

        if (market.getFactionId().equals(Factions.LUDDIC_CHURCH)) {
            total *= 0.1f;
        }

        return total;
    }
}
