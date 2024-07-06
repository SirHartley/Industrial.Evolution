package indevo.industries.salvageyards.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.econ.impl.HeavyIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathCellsIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Pair;
import data.campaign.econ.industries.MS_fabUpgrader;
import data.campaign.econ.industries.MS_modularFac;
import indevo.ids.Ids;
import indevo.ids.ItemIds;
import indevo.industries.SharedSubmarketUser;
import indevo.utils.helper.MiscIE;
import indevo.utils.helper.Settings;
import indevo.utils.helper.StringHelper;
import indevo.utils.timers.NewDayListener;
import org.apache.log4j.Logger;

import java.awt.*;
import java.util.List;
import java.util.*;

public class SalvageYards extends SharedSubmarketUser implements FleetEventListener, EconomyTickListener, NewDayListener {

    public static final Logger log = Global.getLogger(SalvageYards.class);

    private static final int ALPHA_CORE_SALVAGE_POINT_VAL = 40;
    private static final int BETA_CORE_SALVAGE_POINT_VAL = 90;
    private static final int BASE_SALVAGE_POINT_VAL = 50;

    private static final float ALPHA_CORE_DECAY_MULT = 0.6F;
    private static final float GAMMA_CORE_DECAY_MULT = 0.7F;
    private static final float BASE_DECAY_MULT = 0.5F;
    private static final float PROD_QUALITY_MOD = -0.2f;

    private int availableSalvagePoints = 0;
    private final List<Integer> finishedIntel = new ArrayList<>();
    private Random random = new Random();
    private int baseOutput = 0;

    private int outputSalvagePointValue = 50;
    private boolean hasFinishedIntelArchive = false;

    private float outputDecayMult = 0.5F;

    public void apply() {
        super.apply(true);

        //Global.getLogger(SalvageYards.class).info("UPDATING SALVAGE YARDS: market " + market.getName() + " hasSystem" + (market.getStarSystem() != null ? market.getStarSystem().getName() : false));

        if (!isFunctional()) return;

        if (baseOutput == 0) baseOutput = getNewMonthlyOutput();
        applySupDemProfiles();

        Global.getSector().getListenerManager().addListener(this, true);
        applyListenerToFleetsInSystem();
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        addSharedSubmarket();
    }

    //now adds onto heavy industry directly
    private void modifyPlayerCustomProduction(int mod) {
        int max = 0;

        for (Industry ind : market.getIndustries()) {
            if (ind.getId().equals(getId())) continue;
            int i = ind.getSupply(Commodities.SHIPS).getQuantity().getModifiedInt();
            if (i > max) max = i;
        }

        //if there is another exporter, use whatever value is smaller to mod the budget
        if (max > 0) {
            Global.getSector().getPlayerStats().getDynamic().getMod("custom_production_mod").modifyFlatAlways(getModId(), Math.min(max, mod) * 25000f, market.getName() + " " + getNameForModifier());
        }
    }

    private void unmodifyPlayerCustomProduction() {
        Global.getSector().getPlayerStats().getDynamic().getMod("custom_production_mod").unmodify(getModId());
    }

    public void unapply() {
        super.unapply();

        market.getStats().getDynamic().getMod("production_quality_mod").unmodifyFlat(getModId());
        supply.clear();
        demand.clear();

        Global.getSector().getListenerManager().removeListener(this);

        if (!market.isPlayerOwned()) return;
        removeSharedSubmarket();
        unmodifyPlayerCustomProduction();
    }

    @Override
    public boolean isFunctional() {
        return Settings.getBoolean(Settings.SCRAPYARD) && super.isFunctional();
    }

    public int getHullCapacity() {
        Industry hi = getHI();

        float amt = 0;

        if (hi != null) {
            MutableStat.StatMod mod = hi.getSupply(Commodities.SHIPS).getQuantity().getFlatStatMod(getModId());
            if (mod != null) amt = mod.getValue();
        } else amt = getSupply(Commodities.SHIPS).getQuantity().getModifiedInt();

        if (amt < 1f) amt = applySupDemProfiles();

        return Math.round(Math.max(amt, 0));
    }

    private Industry getHI() {
        Industry hi = null;

        for (Industry ind : market.getIndustries()) {
            if (ind instanceof HeavyIndustry) {
                hi = ind;
                break;
            }

            if (!Global.getSettings().getModManager().isModEnabled("shadow_ships")) continue;

            if (ind instanceof MS_modularFac || ind instanceof MS_fabUpgrader) {
                hi = ind;
                break;
            }
        }

        return hi;

    }

    private float applySupDemProfiles() {
        supply.clear();
        demand.clear();

        Industry hi = getHI();

        if (hi != null) hi.getSupply(Commodities.SHIPS).getQuantity().unmodify(getModId());
        unmodifyPlayerCustomProduction();

        //after clearing everything, we can apply new ones if functional
        if (!isFunctional()) return 0f;

        //for AI
        int base;
        if (market.isPlayerOwned()) {
            if (Commodities.BETA_CORE.equals(getAICoreId())) base = market.getSize() + getHullOutputBonus();
            else base = 3 + getHullOutputBonus(); //base 3 plus bonus for players
        } else base = Math.max(3, baseOutput) + getHullOutputBonus();

        demand(Commodities.HEAVY_MACHINERY, base - 2);
        demand(Commodities.SHIPS, base);

        supply(Commodities.METALS, base - 1);
        supply(ItemIds.PARTS, base - 1);

        if (hi == null) {
            supply(Commodities.SHIPS, base - 2);
            market.getStats().getDynamic().getMod("production_quality_mod").modifyFlat(getModId(), PROD_QUALITY_MOD, "Salvage Yards without Heavy Industry");

        } else {
            hi.getSupply(Commodities.SHIPS).getQuantity().modifyFlat(getModId(), base - 2, "Salvage Yards");
        }

        Pair<String, Integer> deficit = getMaxDeficit(Commodities.HEAVY_MACHINERY, Commodities.SHIPS);
        if (market.isPlayerOwned())
            deficit.two = Math.min(deficit.two, 3); //if the market is player owned, reduce the deficit to the max output that comes from imported ship hulls (1), since that's what can actually reduce it.
        applyDeficitToProduction(1, deficit, Commodities.SHIPS, Commodities.METALS, ItemIds.PARTS);

        return base - 2;
    }

    private int getHullOutputBonus() {
        int bonus = (int) Math.floor(availableSalvagePoints / (outputSalvagePointValue * 1f));

        return Math.min(bonus, market.getSize() + 4);
    }

    private void applyListenerToFleetsInSystem() {
        //throw all current ships in the system on a list
        if (market == null || market.getStarSystem() == null)
            return; //somehow, it crashed here?? https://discord.com/channels/187635036525166592/619635013201428481/1067400301033820190

        List<CampaignFleetAPI> allFleets = new ArrayList<>(this.market.getStarSystem().getFleets());

        for (CampaignFleetAPI fleet : allFleets) {
            if (fleet.getEventListeners().contains(this)) continue;

            //Skip LP smugglers to avoid vanilla "ConcurrentModificationException" bug
            boolean b = false;
            for (FleetEventListener e : fleet.getEventListeners()) {
                if (e instanceof LuddicPathCellsIntel) {
                    b = true;
                    break;
                }
            }

            if (b) {
                continue;
            }

            fleet.addEventListener(this);
        }
    }

    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, FleetDespawnReason reason, Object param) {
    }

    //if a fleet loses FP through battle, add those FP to salvagePoints
    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {
        if (fleet != null && fleet.getFleetData() != null && isFunctional())
            availableSalvagePoints += Math.round(com.fs.starfarer.api.util.Misc.getSnapshotFPLost(fleet));
    }

    private static final int WEAPON_SP_MONTH_LIMIT = 50;
    private static final int WEAPON_SP_CARGO_SPACE_MULT = 2;
    private int currentWeaponBonusSP = 0;

    private int getWeaponSPMonthLimit() {
        return market.getSize() * WEAPON_SP_MONTH_LIMIT;
    }

    private int getWeaponSPDayLimit() {
        int dayLimit = Math.round(getWeaponSPMonthLimit() * 1f / MiscIE.getDaysOfCurrentMonth());
        int monthLimit = getWeaponSPMonthLimit();

        if (monthLimit - currentWeaponBonusSP > 0) {
            if (currentWeaponBonusSP + dayLimit > monthLimit) {
                return currentWeaponBonusSP + dayLimit - monthLimit;
            } else return dayLimit;
        } else return 0;
    }

    private float getSPPerUnit(CargoStackAPI stack) {
        return !stack.isWeaponStack() ? 0f : stack.getCargoSpacePerUnit() * WEAPON_SP_CARGO_SPACE_MULT;
    }

    private void autoFeed() {
        //autofeed
        if (!market.hasSubmarket(Ids.SHAREDSTORAGE) || getWeaponSPMonthLimit() - currentWeaponBonusSP <= 0)
            return;
        CargoAPI cargo = market.getSubmarket(Ids.SHAREDSTORAGE).getCargo();

        int dayLimit = getWeaponSPDayLimit();
        int added = 0;

        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            if (added >= dayLimit) break;

            if (stack.isWeaponStack()) {
                float stackTotalUnits = stack.getSize();
                float spPerUnit = getSPPerUnit(stack);
                int requiredUnits = (int) Math.ceil((dayLimit - added) / spPerUnit);
                float toAdd = 0;

                if (stackTotalUnits <= requiredUnits) {
                    toAdd = stackTotalUnits * spPerUnit;
                    cargo.removeStack(stack);
                } else {
                    toAdd = requiredUnits * spPerUnit;
                    cargo.removeWeapons(stack.getWeaponSpecIfWeapon().getWeaponId(), requiredUnits);
                }

                toAdd = Math.round(toAdd);

                availableSalvagePoints += toAdd;
                currentWeaponBonusSP += toAdd;
                added += toAdd;
            }
        }
    }

    public void reportEconomyTick(int iterIndex) {
    }

    public void reportEconomyMonthEnd() {

        currentWeaponBonusSP = 0;
        baseOutput = getNewMonthlyOutput();

        if (!isFunctional()) return;

        availableSalvagePoints *= outputDecayMult;
    }

    private boolean hasPirateActivity(MarketAPI market) {
        boolean hasActivity = false;
        if (market.hasCondition(Conditions.PIRATE_ACTIVITY)) {
            hasActivity = true;
        }
        return hasActivity;
    }

    private boolean systemHasPirateActivity() {

        List<MarketAPI> MarketsInSystem = MiscIE.getMarketsInLocation(market.getStarSystem());
        boolean hasActivity = false;
        for (MarketAPI Market : MarketsInSystem) {
            if (hasPirateActivity(Market)) {
                hasActivity = true;
                break;
            }
        }
        return hasActivity;
    }

    //Archives all intel that has finished up to now, so it doesn't trigger for this industry
    public void IntelArchive() {

        List<IntelInfoPlugin> thisSystemIntelList = new ArrayList<>(Global.getSector().getIntelManager().getIntel());
        for (IntelInfoPlugin intel : thisSystemIntelList) {
            if (intel instanceof RaidIntel && (market.getStarSystem() == ((RaidIntel) intel).getSystem()) && ((RaidIntel) intel).isEnded()) {
                finishedIntel.add(intel.getClass().hashCode());
            }
        }
        hasFinishedIntelArchive = true;
    }

    private int getNewMonthlyOutput() {
        int size = market.getSize() + 1;
        int addition = random.nextInt(2);
        int total = size + (addition * (random.nextBoolean() ? -1 : 1));

        return Math.max(1, total);
    }

    @Override
    public void onNewDay() {
        if (!hasFinishedIntelArchive) {
            IntelArchive();
        }

        autoFeed();

        //get all intel
        List<IntelInfoPlugin> allIntel = new ArrayList<>(Global.getSector().getIntelManager().getIntel(RaidIntel.class));
        for (IntelInfoPlugin intel : allIntel) {
            //check if it's raidIntel and targets this system
            if (intel instanceof RaidIntel && (market.getStarSystem() == ((RaidIntel) intel).getSystem()) && !finishedIntel.contains(intel.getClass().hashCode())) {
                //if its ended and succeeded, give player 30% raid FP
                int salvagePoints = 0;
                String msg = null;

                if (((RaidIntel) intel).isSucceeded()) {
                    salvagePoints = (int) (((RaidIntel) intel).getRaidFPAdjusted() * 0.30F);
                    msg = "incursionSuccess";
                    //Else if its ended and failed in the action stage, and the player has not visited for the duration of the action stage
                } else if (((RaidIntel) intel).isFailed()
                        && ((RaidIntel) intel).getFailStage() == ((RaidIntel) intel).getStageIndex(((RaidIntel) intel).getActionStage())
                        && market.getStarSystem().getDaysSinceLastPlayerVisit() >= ((RaidIntel) intel).getActionStage().getElapsed()) {
                    salvagePoints = (int) (((RaidIntel) intel).getRaidFPAdjusted() * 0.70F);
                    msg = "incursionFailed";
                }

                if (msg != null) {
                    availableSalvagePoints += salvagePoints;
                    finishedIntel.add(intel.getClass().hashCode());

                    log.info("Adding raid SP to Yards: " + msg + " " + " at " + market.getName() + ", " + market.getFaction().getId() + ", recovered " + salvagePoints + " units for intel " + intel.getClass().hashCode());

                    if (market.isPlayerOwned()) {
                        Map<String, String> toReplace = new HashMap<>();
                        toReplace.put("$amt", salvagePoints + "");
                        toReplace.put("$systemName", market.getStarSystem().getName());

                        msg = StringHelper.getStringAndSubstituteTokens(getId(), msg, toReplace);

                        Global.getSector().getCampaignUI().addMessage(msg,
                                com.fs.starfarer.api.util.Misc.getTextColor(),
                                toReplace.get("$systemName"),
                                toReplace.get("$amt"),
                                market.getFaction().getColor(),
                                com.fs.starfarer.api.util.Misc.getHighlightColor());
                    }
                }

            }
        }

        //As long as the market has pirate activity, add a flat amount of FP to the counter every day
        if (systemHasPirateActivity()) {
            availableSalvagePoints += 3;
        }
    }

    @Override
    protected void addRightAfterDescriptionSection(TooltipMakerAPI tooltip, Industry.IndustryTooltipMode mode) {

        if (!isBuilding() && currTooltipMode != Industry.IndustryTooltipMode.ADD_INDUSTRY && market.isPlayerOwned()) {
            float opad = 5.0F;

            if (this.isFunctional()) {
                if (market.isPlayerOwned() || currTooltipMode == Industry.IndustryTooltipMode.NORMAL) {
                    tooltip.addPara(StringHelper.getString(getId(), "sUnitsAvailableTooltip"),
                            opad, com.fs.starfarer.api.util.Misc.getHighlightColor(), new String[]{"" + availableSalvagePoints, "" + outputSalvagePointValue});
                }
            }
        }
    }

//Building checks and tooltips

    @Override
    public boolean isAvailableToBuild() {
        return Settings.getBoolean(Settings.SCRAPYARD) && isOnlyInstanceInSystem() && super.isAvailableToBuild();
    }

    @Override
    public boolean showWhenUnavailable() {
        return Settings.getBoolean(Settings.SCRAPYARD);
    }

    private boolean isOnlyInstanceInSystem() {
        return MiscIE.isOnlyInstanceInSystemExcludeMarket(getId(), market.getStarSystem(), market, market.getFaction());
    }

    @Override
    public String getUnavailableReason() {
        if (!isOnlyInstanceInSystem()) {
            return StringHelper.getString(getId(), "unavailableReason");
        } else {
            return super.getUnavailableReason();
        }
    }

    protected void addAlphaCoreDescription(TooltipMakerAPI tooltip, Industry.AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = com.fs.starfarer.api.util.Misc.getHighlightColor();
        String suffix = mode == Industry.AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == Industry.AICoreDescriptionMode.INDUSTRY_TOOLTIP ? "Short" : "Long";
        String pre = StringHelper.getString("IndEvo_AICores", "aCoreAssigned" + suffix);
        String effect = StringHelper.getString(getId(), "aCoreEffect");
        String[] highlightString = new String[]{ALPHA_CORE_SALVAGE_POINT_VAL - BASE_SALVAGE_POINT_VAL + "", StringHelper.getAbsPercentString(ALPHA_CORE_DECAY_MULT, true)};

        if (mode == Industry.AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + effect, 0.0F, highlight, highlightString);
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + effect, opad, highlight, highlightString);
        }
    }

    protected void addBetaCoreDescription(TooltipMakerAPI tooltip, Industry.AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = com.fs.starfarer.api.util.Misc.getHighlightColor();

        String suffix = mode == Industry.AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == Industry.AICoreDescriptionMode.INDUSTRY_TOOLTIP ? "Short" : "Long";
        String pre = StringHelper.getString("IndEvo_AICores", "bCoreAssigned" + suffix);
        String effect = StringHelper.getString(getId(), "bCoreEffect");
        String[] highlightString = new String[]{StringHelper.getString("colonySize"), BETA_CORE_SALVAGE_POINT_VAL - BASE_SALVAGE_POINT_VAL + ""};

        if (mode == Industry.AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + effect, 0.0F, highlight, highlightString);
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + effect, opad, highlight, highlightString);
        }
    }

    protected void addGammaCoreDescription(TooltipMakerAPI tooltip, Industry.AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = com.fs.starfarer.api.util.Misc.getHighlightColor();

        String suffix = mode == Industry.AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == Industry.AICoreDescriptionMode.INDUSTRY_TOOLTIP ? "Short" : "Long";
        String pre = StringHelper.getString("IndEvo_AICores", "gCoreAssigned" + suffix);
        String effect = StringHelper.getString(getId(), "gCoreEffect");
        String highlightString = StringHelper.getAbsPercentString(GAMMA_CORE_DECAY_MULT, true);

        if (mode == Industry.AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + effect, 0.0F, highlight, highlightString);
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + effect, opad, highlight, highlightString);
        }
    }

    protected void applyAlphaCoreModifiers() {
        outputDecayMult = ALPHA_CORE_DECAY_MULT;
        outputSalvagePointValue = ALPHA_CORE_SALVAGE_POINT_VAL;
    }

    protected void applyBetaCoreModifiers() {
        outputSalvagePointValue = BETA_CORE_SALVAGE_POINT_VAL;
    }

    @Override
    protected void applyGammaCoreModifiers() {
        outputDecayMult = GAMMA_CORE_DECAY_MULT;
    }

    @Override
    protected void applyNoAICoreModifiers() {
        outputSalvagePointValue = BASE_SALVAGE_POINT_VAL;
        outputDecayMult = BASE_DECAY_MULT;
        market.getStats().getDynamic().getMod("production_quality_mod").unmodifyFlat("ScrapYardsAICore");
    }

    protected void updateAICoreToSupplyAndDemandModifiers() {
    }

    protected void applyAICoreToIncomeAndUpkeep() {
    }

    @Override
    public boolean isLegalOnSharedSubmarket(CargoStackAPI stack) {
        return stack.isWeaponStack();
    }

    @Override
    public void addTooltipLine(TooltipMakerAPI tooltip, boolean expanded) {
        tooltip.addPara("Salvage Yards: disassembles %s in this storage to generate Salvage Points.", 10f, com.fs.starfarer.api.util.Misc.getHighlightColor(), "weapons");
    }
}






