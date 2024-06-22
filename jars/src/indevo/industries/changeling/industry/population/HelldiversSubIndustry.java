package indevo.industries.changeling.industry.population;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SubmarketPlugin;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.BaseIndustryOptionProvider;
import com.fs.starfarer.api.campaign.listeners.ColonyPlayerHostileActListener;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.PlayerFleetPersonnelTracker;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.impl.campaign.submarkets.LocalResourcesSubmarketPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import indevo.industries.changeling.hullmods.Hellpods;
import indevo.industries.changeling.industry.SubIndustry;
import indevo.industries.changeling.industry.SubIndustryData;
import indevo.utils.helper.Misc;
import indevo.utils.helper.Settings;
import indevo.utils.helper.StringHelper;
import indevo.utils.timers.NewDayListener;
import org.lazywizard.lazylib.MathUtils;

import java.util.*;

import static com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathBaseManager.AI_CORE_ADMIN_INTEREST;
import static com.fs.starfarer.api.impl.campaign.population.CoreImmigrationPluginImpl.getWeightForMarketSizeStatic;

public class HelldiversSubIndustry extends SubIndustry implements EconomyTickListener, ColonyPlayerHostileActListener, NewDayListener {

    /*
Managed Democracy
•	x-50% positive income
•	xNo luddic cells
.   no luddic subpop
•	xStability can’t fall below 3 or go above 7
•	xLocked at size 5
•	xMilitary industries -upkeep
•   xMarines stored here passively gather experience

reduce income penalty by 10% for 365d for every successful raid or invasion
refit cruisers left in storage here with a "hellpods" hullmod that increases raid effectiveness and doubles casualties if possible (done)
increase marine stockpile limit and rate (+ 50% * market size)
s3: +1 small patrol, s4: +1 med patrol, s5: +1 large patrol
*/

    public static final int MAX_STAB = 7;
    public static final int MIN_STAB = 3;
    public static final float INCOME_RED = 50f;
    public static final int MAX_MARKET_SIZE = 5;
    public static final float HELLDIVERS_UPKEEP_RED = 0.7f;
    public static final float MAX_MARINES_AMT = 500f;
    public static final float MARINES_GAIN_EXP = 3f;
    public static final float MARINE_STOCKPILE_BONUS = 1.5f;
    public static final int DAYS_TO_HELLPOD_REFIT = 62;
    private Map<String, Integer> daysToApplicationForFleetMember = new LinkedHashMap<>();
    private Map<String, RaidMod> raidMods = new LinkedHashMap<>();

    public static class RaidMod {
        public int daysRemaining;
        public String source;
        public float mod;

        public RaidMod(int daysRemaining, String source, float mod) {
            this.daysRemaining = daysRemaining;
            this.source = source;
            this.mod = mod;
        }

        public void setDaysRemaining(int daysRemaining) {
            this.daysRemaining = daysRemaining;
        }

        public int getDaysRemaining() {
            return daysRemaining;
        }

        public boolean isExpired(){
            return daysRemaining <= 0;
        }

        public String getSource() {
            return source;
        }

        public float getMod() {
            return mod;
        }
    }

    public static class HelldiversTooltipAdder extends BaseIndustryOptionProvider {

        public static void register() {
            ListenerManagerAPI manager = Global.getSector().getListenerManager();
            if (!manager.hasListenerOfClass(HelldiversTooltipAdder.class))
                manager.addListener(new HelldiversTooltipAdder(), true);
        }

        @Override
        public boolean isUnsuitable(Industry ind, boolean allowUnderConstruction) {
            return !isSuitable(ind);
        }

        public boolean isSuitable(Industry ind) {
            Industry pop = ind.getMarket().getIndustry(Industries.POPULATION);
            boolean isTarget = pop instanceof SwitchablePopulation && ((SwitchablePopulation) pop).getCurrent() instanceof HelldiversSubIndustry;
            return !Settings.getBoolean(Settings.GOVERNMENT_LARP_MODE) && isTarget;
        }

        @Override
        public void addToIndustryTooltip(Industry ind, Industry.IndustryTooltipMode mode, TooltipMakerAPI tooltip, float width, boolean expanded) {
            if (isUnsuitable(ind, true)) return;
            float opad = 10f;

            tooltip.addSectionHeading("Governance Effects: Managed Democracy", Alignment.MID, opad);

            if (Misc.isMilitary(ind)) {
                tooltip.addPara("Military buildings: %s decreased by %s", opad, com.fs.starfarer.api.util.Misc.getTextColor(), com.fs.starfarer.api.util.Misc.getPositiveHighlightColor(), "upkeep", StringHelper.getAbsPercentString(HELLDIVERS_UPKEEP_RED, true));
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

    public void addMemberToHellpodApplicationListIfNeeded(FleetMemberAPI member){
        if (daysToApplicationForFleetMember.containsKey(member.getId()) || member.getVariant().hasHullMod(Hellpods.HULLMOD_ID) || member.getVariant().getHullSize() != ShipAPI.HullSize.CRUISER) return;
        daysToApplicationForFleetMember.put(member.getId(), DAYS_TO_HELLPOD_REFIT);
    }

    @Override
    public void onNewDay() {
        Misc.getStorageCargo(market).initMothballedShips(market.getFactionId());
        List<FleetMemberAPI> membersInStorage = Misc.getStorageCargo(market).getMothballedShips().getMembersListCopy();
        List<String> expired = new ArrayList<>();

        //iterate members and add hellpods if needed
        for (Map.Entry<String, Integer> entry : daysToApplicationForFleetMember.entrySet()) {
            int daysLeft = entry.getValue() -1;
            entry.setValue(daysLeft);

            if (daysLeft < 1) {
                for (FleetMemberAPI m : membersInStorage) if (m.getId().equals(entry.getKey())) {
                    m.getVariant().addPermaMod(Hellpods.HULLMOD_ID);
                    break;
                }

                expired.add(entry.getKey());
            }
        }

        //add new members
        for (FleetMemberAPI m : membersInStorage) addMemberToHellpodApplicationListIfNeeded(m);

        //remove expired
        for (String s : expired) daysToApplicationForFleetMember.remove(s);

        //raid mods
        for (RaidMod mod : raidMods.values()) mod.setDaysRemaining(mod.getDaysRemaining() - 1);
    }

    @Override
    public void addRightAfterDescription(TooltipMakerAPI tooltip, Industry.IndustryTooltipMode mode) {
        super.addRightAfterDescription(tooltip, mode);

        float opad = 10f;

        int size = market.getSize();

        int small = size > 4 ? 2 : 1;
        int med = size > 3 ? 1 : 0;
        int large = size > 4 ? 1 : 0;

        tooltip.addSectionHeading("Extra patriotic patrols", Alignment.MID, opad);
        tooltip.addPara(market.getFaction().getFleetTypeName(FleetTypes.PATROL_SMALL) + ": " + small + (small > 1 ? " fleets" : " fleet"), opad);
        if (med > 0) tooltip.addPara(market.getFaction().getFleetTypeName(FleetTypes.PATROL_MEDIUM) + ": " + med + " fleet", opad);
        if (large > 0) tooltip.addPara(market.getFaction().getFleetTypeName(FleetTypes.PATROL_LARGE) + ": " + large + " fleet", opad);

        //add list of ships and timings

        tooltip.addSectionHeading("Hellpod installation progress", Alignment.MID, opad);
        tooltip.addPara("Cruisers stored here will be refit with a rapid orbital deployment system, increasing marine effectiveness and casualties.", com.fs.starfarer.api.util.Misc.getGrayColor(), opad);
        tooltip.beginTable(market.getFaction(), 20f, "Ship", 270f, "Days remaining", 120f);

        int i = 0;
        int max = 10;

        Misc.getStorageCargo(market).initMothballedShips(market.getFactionId());
        List<FleetMemberAPI> membersInStorage = Misc.getStorageCargo(market).getMothballedShips().getMembersListCopy();
        Map<FleetMemberAPI, Integer> validMembersWithRefitTime = new LinkedHashMap<>();

        for (FleetMemberAPI m : membersInStorage) if(daysToApplicationForFleetMember.containsKey(m.getId())) validMembersWithRefitTime.put(m, daysToApplicationForFleetMember.get(m.getId()));

        for (Map.Entry<FleetMemberAPI, Integer > e : validMembersWithRefitTime.entrySet()) {
            tooltip.addRow(e.getKey().getHullSpec().getNameWithDesignationWithDashClass(), e.getValue() +" "+ StringHelper.getDayOrDays(e.getValue()));
            i++;
            if (i == max) break;
        }

        //add the table to the tooltip
        tooltip.addTable("Add cruisers to storage to have them refit.", validMembersWithRefitTime.size() - 10, opad);
    }

    //repeat raids in 3 months don't do anything
    //give minor bonus for tac bombs
    //more for sat bombing
    //raiding to disrupt should give more than raiding for valuables

    @Override
    public void reportRaidForValuablesFinishedBeforeCargoShown(InteractionDialogAPI dialog, MarketAPI market, MarketCMD.TempData actionData, CargoAPI cargo) {
        String id = market.getId() + "_getSamples";
        raidMods.put(id, new RaidMod(31*3, "Raid (Valuables): " + market.getName(), 0.1f));
    }

    @Override
    public void reportRaidToDisruptFinished(InteractionDialogAPI dialog, MarketAPI market, MarketCMD.TempData actionData, Industry industry) {
        String id = market.getId() + "_clearOutposts";
        raidMods.put(id, new RaidMod(31*3, "Raid (Disrupt): " + market.getName(), 0.15f));
    }

    @Override
    public void reportTacticalBombardmentFinished(InteractionDialogAPI dialog, MarketAPI market, MarketCMD.TempData actionData) {
        String id = market.getId() + "_^>vvv";
        raidMods.put(id, new RaidMod(31*6, "Tactical Bombardment: " + market.getName(), 0.1f));
    }

    @Override
    public void reportSaturationBombardmentFinished(InteractionDialogAPI dialog, MarketAPI market, MarketCMD.TempData actionData) {
        String id = market.getId() + "_>v^^<vv";
        raidMods.put(id, new RaidMod(31*12, "Saturation Bombardment: " + market.getName(), 0.25f));
    }

    public HelldiversSubIndustry(SubIndustryData data) {
        super(data);
    }

    @Override
    public void apply() {
        ((SwitchablePopulation) industry).superApply();

        HelldiversTooltipAdder.register();
        Global.getSector().getListenerManager().addListener(this);
        correctStability();

        String modID = ((SwitchablePopulation) industry).getModId();

        int size = market.getSize();
        market.getStats().getDynamic().getMod(Stats.PATROL_NUM_LIGHT_MOD).modifyFlat(modID, market.getSize() > 4 ? 2 : 1);
        if (size > 3) market.getStats().getDynamic().getMod(Stats.PATROL_NUM_MEDIUM_MOD).modifyFlat(modID, 1);
        if (size > 4) market.getStats().getDynamic().getMod(Stats.PATROL_NUM_LIGHT_MOD).modifyFlat(modID, 1);


        market.getIncomeMult().modifyPercent(((SwitchablePopulation) industry).getModId(), -INCOME_RED, getName());

        for (Map.Entry<String, RaidMod> raidModEntry : raidMods.entrySet()){
            if (raidModEntry.getValue().isExpired()) continue;
            RaidMod mod = raidModEntry.getValue();
            market.getIncomeMult().modifyPercent(raidModEntry.getKey(), mod.getMod(), mod.getSource() + " ("+ mod.getDaysRemaining() + " " + StringHelper.getDayOrDays(mod.getDaysRemaining()) + ")");
        }

        if (market.getSize() >= MAX_MARKET_SIZE) market.getPopulation().setWeight(getWeightForMarketSizeStatic(market.getSize()));

        for (Industry ind : market.getIndustries()) {
            if (Misc.isMilitary(ind)) {
                ind.getUpkeep().modifyMult(getId(), HELLDIVERS_UPKEEP_RED, getName());
            }
        }

        if (market.isPlayerOwned()) {
            SubmarketPlugin sub = com.fs.starfarer.api.util.Misc.getLocalResources(market);

            if (sub instanceof LocalResourcesSubmarketPlugin) {
                LocalResourcesSubmarketPlugin lr = (LocalResourcesSubmarketPlugin) sub;

                if(market.getCommodityData(Commodities.MARINES).getAvailable() > 0){
                    lr.getStockpilingBonus(Commodities.MARINES).modifyFlat(getId() + "_MARINES", size * MARINE_STOCKPILE_BONUS);
                }
            }
        }
    }

    @Override
    public boolean isAvailableToBuild() {
        return super.isAvailableToBuild() && market.getSize() <= MAX_MARKET_SIZE && market.getPrimaryEntity() instanceof PlanetAPI;
    }

    @Override
    public String getUnavailableReason() {
        if (market.getSize() > MAX_MARKET_SIZE) return "This planet is too populated";
        if (!(market.getPrimaryEntity() instanceof PlanetAPI)) return "Unavailable on stations";
        return super.getUnavailableReason();
    }

    @Override
    public void unapply() {
        super.unapply();
        String id = ((SwitchablePopulation) industry).getModId();
        market.getStability().unmodify(id);
        market.getIncomeMult().unmodify(id);

        market.getStats().getDynamic().getMod(Stats.PATROL_NUM_LIGHT_MOD).unmodifyFlat(id);
        market.getStats().getDynamic().getMod(Stats.PATROL_NUM_MEDIUM_MOD).unmodifyFlat(id);
        market.getStats().getDynamic().getMod(Stats.PATROL_NUM_HEAVY_MOD).unmodifyFlat(id);

        Global.getSector().getListenerManager().removeListener(this);

        for (Industry ind : market.getIndustries()) {
            if (Misc.isMilitary(ind)) {
                ind.getUpkeep().unmodify(getId());
            }
        }

        SubmarketPlugin sub = com.fs.starfarer.api.util.Misc.getLocalResources(market);
        if (sub instanceof LocalResourcesSubmarketPlugin) {
            LocalResourcesSubmarketPlugin lr = (LocalResourcesSubmarketPlugin) sub;
            lr.getStockpilingBonus(Commodities.MARINES).unmodify(getId() + "_MARINES");
        }

        for (Map.Entry<String, RaidMod> raidModEntry : raidMods.entrySet()) {
            market.getIncomeMult().unmodify(raidModEntry.getKey());
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