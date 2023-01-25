package indevo.industries.privateer.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import indevo.ids.Ids;
import indevo.industries.EngineeringHub;
import indevo.utils.helper.IndustryHelper;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import indevo.industries.privateer.intel.PrivateerBaseRaidIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.*;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import indevo.utils.scripts.IndustryAddOrRemovePlugin;
import indevo.utils.timers.RaidTimeout;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.apache.log4j.Logger;

import java.awt.*;
import java.util.List;
import java.util.*;

public class PrivateerBase extends BaseIndustry implements EconomyTickListener, RaidIntel.RaidDelegate {

    public RaidIntel currentIntel = null;
    private int raidTimeoutMonths = 0;
    private final int supplyDiminishmentPerMonth = 2;
    private float aiCoreFPBonus = 1f;
    private Map<String, Integer> supplyMemory = new HashMap<>();
    private final List<String> decayStopTimer = new ArrayList<>();
    private int raidAmounts = 0;

    public boolean debug = false;
    public static final Logger log = Global.getLogger(PrivateerBase.class);

    //debug-Logger
    private void debugMessage(String Text) {
        if (debug) log.info(Text);
    }

    protected void updateSupplyAndDemandModifiers() {
        super.updateSupplyAndDemandModifiers();
        supplyBonus.unmodify();
    }

    public void apply() {
        super.apply(true);
        debug = Global.getSettings().isDevMode();

        Global.getSector().getListenerManager().addListener(this, true);

        if (supplyMemory != null && !supplyMemory.isEmpty()) {
            supply.clear();

            for (Map.Entry<String, Integer> e : supplyMemory.entrySet()) {
                supply(e.getKey(), e.getValue());
            }
        }

        Global.getSector().addTransientScript(new IndustryAddOrRemovePlugin(market, Ids.PIRATEHAVEN_SECONDARY, false));

    }

    @Override
    public void unapply() {
        super.unapply();
        Global.getSector().getListenerManager().removeListener(this);
        Global.getSector().addTransientScript(new IndustryAddOrRemovePlugin(market, Ids.PIRATEHAVEN_SECONDARY, true));
    }

    @Override
    public boolean isAvailableToBuild() {
        return Global.getSettings().getBoolean("PirateHaven")
                && (Misc.getMaxIndustries(market) - Misc.getNumIndustries(market)) >= 2
                && super.isAvailableToBuild()
                && IndustryHelper.getAmountOfIndustryInSystem(getId(), market.getStarSystem(), market.getFaction()) < 2;
    }

    @Override
    public boolean showWhenUnavailable() {
        return Global.getSettings().getBoolean("PirateHaven");
    }

    @Override
    public String getUnavailableReason() {
        if ((Misc.getMaxIndustries(market) - Misc.getNumIndustries(market)) < 2) {
            return "Requires two industry slots.";
        }

        if (IndustryHelper.getAmountOfIndustryInSystem(getId(), market.getStarSystem(), market.getFaction()) > 1) {
            return "Can only have two in a Star System.";
        }

        return super.getUnavailableReason();
    }

    public void reportEconomyTick(int iterIndex) {
        /*if(debug) {
            startRaid(getRaidTarget(), getBaseRaidFP());
            debugOutputs();
        }*/

        if (isFunctional() && !market.hasCondition(Ids.COND_PIRATES)) {
            market.addCondition(Ids.COND_PIRATES);
        }

        int lastIterInMonth = (int) Global.getSettings().getFloat("economyIterPerMonth") - 1;
        if (iterIndex != lastIterInMonth) return;

        debugMessage("reporting last tick");
        supplyDiminishment();
        raidTimeoutMonths -= raidTimeoutMonths > 0 ? 1 : 0;

        if (raidTimeoutMonths <= 0 && currentIntel == null && isFunctional()) {
            StarSystemAPI target = getRaidTarget();
            if (target != null) {
                startRaid(target, getBaseRaidFP());
            } else if (market.isPlayerOwned()) {
                MessageIntel intel = new MessageIntel("Your Privateers could not find a target for a raid.", Misc.getTextColor());
                intel.addLine("They humbly request you to %s.", Misc.getTextColor(), new String[]{"make some more enemies"}, Misc.getHighlightColor());
                intel.setIcon(Global.getSettings().getSpriteName("IndEvo", "notification"));
                intel.setSound(BaseIntelPlugin.getSoundStandardPosting());
                Global.getSector().getCampaignUI().addMessage(intel, CommMessageAPI.MessageClickAction.COLONY_INFO, this.market);
            }
        }
    }

    public void reportEconomyMonthEnd() {
    }

    public float getBaseRaidFP() {
        //the ai core bonus calculates twice because I fucked up when making this, and it would be a nerf to remove it now (and I don't want to nerf it)
        return ((((market.getSize() * 50) * market.getStats().getDynamic().getStat(Stats.COMBAT_FLEET_SIZE_MULT).computeMultMod()) * (0.75f + (float) Math.random() * 0.5f)) * aiCoreFPBonus) * aiCoreFPBonus;
    }

    public void notifyRaidEnded(RaidIntel raid, RaidIntel.RaidStageStatus status) {
        if (currentIntel == null || raid != currentIntel) return;

        //throws notification once the raid ends

        debugMessage("raid ended: " + raid.getSystem());

        currentIntel = null;

        StarSystemAPI system = raid.getSystem();

        if (status == RaidIntel.RaidStageStatus.SUCCESS) {
            debugMessage("succesful");

            setRaidIndustryOutput(system, true);

            float timeout = aiCoreId != null && aiCoreId.equals(Commodities.BETA_CORE) ? 2f : 3f;

            RaidTimeout.addRaidedSystem(system, timeout, !market.isPlayerOwned());
            if (market.isPlayerOwned()) spoilsOfWar(system);

        } else {
            raidTimeoutMonths += 1;
            RaidTimeout.addRaidedSystem(raid.getSystem(), 1f, !market.isPlayerOwned());

            //don't give output if failstage is smaller action stage
            if (raid.getFailStage() >= raid.getStageIndex(raid.getActionStage())) {
                setRaidIndustryOutput(raid.getSystem(), false);

                if (market.isPlayerOwned()) {
                    Global.getSector().getCampaignUI().addMessage("A raid on the %s has failed. Your Privateers only managed to acquire %s of commodities.",
                            Global.getSettings().getColor("standardTextColor"), system.getName(), "a pitiful amount", raid.getFaction().getColor(), Misc.getNegativeHighlightColor());
                }

            } else if (market.isPlayerOwned()) {
                Global.getSector().getCampaignUI().addMessage("A raid on the %s has failed to even reach the system.",
                        Global.getSettings().getColor("standardTextColor"), system.getName(), "", raid.getFaction().getColor(), Misc.getNegativeHighlightColor());
            }


            debugMessage("raidTimeout: " + raidTimeoutMonths);
        }
    }

    private void debugOutputs() {
        raidAmounts++;
        debugMessage("RaidAmount" + raidAmounts);
        ArrayList<FactionAPI> factionList = getActiveHostileFactions();
        StarSystemAPI bestSystem = null;

        for (int j = 0; j <= factionList.size(); j++) {
            int rnd = new Random().nextInt(factionList.size());
            FactionAPI targetFaction = factionList.get(rnd);

            StarSystemAPI target = getBestTargetSystem(targetFaction);
            if (target != null) {
                bestSystem = target;
                break;
            }

            factionList.remove(targetFaction);
        }

        if (bestSystem == null) return;
        setRaidIndustryOutput(bestSystem, true);
        spoilsOfWar(bestSystem);
    }

    private void spoilsOfWar(StarSystemAPI system) {
        ArrayList<MarketAPI> shipUsers = getHostileShipUser(system);
        ArrayList<MarketAPI> raidedMarkets = getHostileMarketsInSystem(system);

        if (!shipUsers.isEmpty()) {
            for (MarketAPI market : shipUsers) {
                getLoot(market, true);
            }

            for (MarketAPI nonShipMarket : raidedMarkets) {
                if (shipUsers.contains(nonShipMarket)) {
                    continue;
                }
                getLoot(nonShipMarket, false);
            }

            Global.getSector().getCampaignUI().addMessage("Thanks to the presence of one or more industrial polities in %s your privateers liberated %s of useful items.",
                    Global.getSettings().getColor("standardTextColor"), system.getName(), "a good amount", Misc.getHighlightColor(), Misc.getPositiveHighlightColor());

        } else {
            for (MarketAPI nonShipMarket : raidedMarkets) {
                getLoot(nonShipMarket, false);
            }

            Global.getSector().getCampaignUI().addMessage("As no real industrial presence exists on the planets of %s your privateers liberated %s of useful items.",
                    Global.getSettings().getColor("standardTextColor"), system.getName(), "a rather disappointing amount", Misc.getHighlightColor(), Misc.getHighlightColor());
        }
    }

    //loot drop handling
    protected void getLoot(MarketAPI target, boolean withBP) {
        FactionAPI playerFaction = Global.getSector().getPlayerFaction();
        float chanceOfDrop = 0.7f + (aiCoreId != null && aiCoreId.equals(Commodities.ALPHA_CORE) ? 0.2f : 0f);
        float chanceOfExtraDrop = 0.3f + (aiCoreId != null && aiCoreId.equals(Commodities.ALPHA_CORE) ? 0.1f : 0f);
        CargoAPI cargo = Global.getSector().getPlayerFaction().getProduction().getGatheringPoint().getSubmarket("storage").getCargo();
        Random random = new Random();

        String ship = "MarketCMD_ship____";
        String weapon = "MarketCMD_weapon__";
        String fighter = "MarketCMD_fighter_";

        // blueprints
        if (withBP) {
            WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
            boolean raidUnknownOnly = Global.getSettings().getBoolean("IndEvo_RaidForUnknownOnly");
            for (String id : target.getFaction().getKnownShips()) {
                if (raidUnknownOnly && playerFaction.knowsShip(id)) continue;
                picker.add(ship + id, 1f);
            }
            for (String id : target.getFaction().getKnownWeapons()) {
                if (raidUnknownOnly && playerFaction.knowsWeapon(id)) continue;
                picker.add(weapon + id, 1f);
            }
            for (String id : target.getFaction().getKnownFighters()) {
                if (raidUnknownOnly && playerFaction.knowsFighter(id)) continue;
                picker.add(fighter + id, 1f);
            }

            int num = getNumPicks(random, chanceOfDrop, chanceOfExtraDrop * 0.5f);
            for (int i = 0; i < num && !picker.isEmpty(); i++) {
                String id = picker.pickAndRemove();
                if (id == null) continue;

                if (id.startsWith(ship)) {
                    String specId = id.substring(ship.length());
                    if (Global.getSettings().getHullSpec(specId).hasTag(Tags.NO_BP_DROP) || Global.getSettings().getHullSpec(specId).getHints().contains(ShipHullSpecAPI.ShipTypeHints.UNBOARDABLE))
                        continue;

                    if (EngineeringHub.isTiandong(specId)) {
                        cargo.addSpecial(new SpecialItemData("tiandong_retrofit_bp", specId), 1);
                    } else if (EngineeringHub.isRoider(specId)) {
                        cargo.addSpecial(new SpecialItemData("roider_retrofit_bp", specId), 1);
                    } else {
                        cargo.addSpecial(new SpecialItemData(Items.SHIP_BP, specId), 1);
                    }
                } else if (id.startsWith(weapon)) {
                    String specId = id.substring(weapon.length());
                    if (Global.getSettings().getWeaponSpec(specId).hasTag(Tags.NO_BP_DROP)) continue;
                    cargo.addSpecial(new SpecialItemData(Items.WEAPON_BP, specId), 1);
                } else if (id.startsWith(fighter)) {
                    String specId = id.substring(fighter.length());
                    if (Global.getSettings().getFighterWingSpec(specId).hasTag(Tags.NO_BP_DROP)) continue;
                    cargo.addSpecial(new SpecialItemData(Items.FIGHTER_BP, specId), 1);
                }
            }
        }


        // weapons and fighters
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
        for (String id : target.getFaction().getKnownWeapons()) {
            WeaponSpecAPI w = Global.getSettings().getWeaponSpec(id);
            if (w.hasTag("no_drop")) continue;
            if (w.getAIHints().contains(WeaponAPI.AIHints.SYSTEM)) continue;

            if (!withBP &&
                    (w.getTier() > 1 || w.getSize() == WeaponAPI.WeaponSize.LARGE)) continue;

            picker.add(weapon + id, w.getRarity());
        }

        for (String id : target.getFaction().getKnownFighters()) {
            FighterWingSpecAPI f = Global.getSettings().getFighterWingSpec(id);
            if (f.hasTag(Tags.WING_NO_DROP)) continue;

            if (!withBP && f.getTier() > 0) continue;

            picker.add(fighter + id, f.getRarity());
        }

        int num = getNumPicks(random, chanceOfDrop, chanceOfExtraDrop * 0.5f) * 3;
        if (withBP) {
            num += target.getSize();
        }

        for (int i = 0; i < num && !picker.isEmpty(); i++) {
            String id = picker.pickAndRemove();
            if (id == null) continue;

            if (id.startsWith(weapon)) {
                cargo.addWeapons(id.substring(weapon.length()), 1);
            } else if (id.startsWith(fighter)) {
                cargo.addFighters(id.substring(fighter.length()), 1);
            }
        }

    }

    protected int getNumPicks(Random random, float pAny, float pMore) {
        if (random.nextFloat() >= pAny) return 0;

        int result = 1;
        for (int i = 0; i < 10; i++) {
            if (random.nextFloat() >= pMore) break;
            result++;
        }
        return result;
    }

    private void supplyDiminishment() {
        debugMessage("supplyDiminishment");
        for (MutableCommodityQuantity sup : getAllSupply()) {
            String id = sup.getCommodityId();

            //if it's timed out, skip the commodity and iterate to false
            if (decayStopTimer.contains(id)) {
                decayStopTimer.remove(id);
                continue;
            }

            int q = getBaseSupply(id) - supplyDiminishmentPerMonth;
            supply.remove(id);

            if (q > 0) {
                supply(id, q);
                supplyMemory.put(id, q);
            } else supplyMemory.remove(id);
        }
    }

    private int getBaseSupply(String id){
        return supplyMemory.containsKey(id) ? supplyMemory.get(id) : 0;
    }

    private void setRaidIndustryOutput(StarSystemAPI raidedSystem, boolean successful) {
        for (MarketAPI raidedMarket : getHostileMarketsInSystem(raidedSystem)) {
            debugMessage("Setting Base Output for raided Market " + raidedMarket.getName());

            for (Map.Entry<String, Integer> raidSupply : getMarketProductionAmounts(raidedMarket).entrySet()) {
                String id =raidSupply.getKey();

                //ignore ship hulls
                if (id.equals(Commodities.SHIPS)) {
                    continue;
                }

                float successMult = successful ? 0.85f : 0.33f;
                int raidSupplyValue = (int) Math.round(raidSupply.getValue() * successMult);
                int currentSupply = getBaseSupply(id);

                //is the base amount this produces smaller than the raided amount - if yes, set as new supply.
                if (currentSupply < raidSupplyValue) {
                    supply.remove(id);

                    supplyMemory.put(id, raidSupplyValue);
                    supply(id, raidSupplyValue);

                    if (getAICoreId() != null && getAICoreId().equals(Commodities.GAMMA_CORE)) {
                        decayStopTimer.add(raidSupply.getKey());
                    }
                }
            }
        }
    }

    private StarSystemAPI getRaidTarget() {
        ArrayList<FactionAPI> factionList = getActiveHostileFactions();
        StarSystemAPI bestSystem = null;

        //The entire following block is a good example of why returning null on anything is a shit idea
        if (factionList.size() > 0) {
            for (int j = 0; j <= factionList.size(); j++) {
                int rnd = new Random().nextInt(factionList.size());
                FactionAPI targetFaction = factionList.get(rnd);

                if (targetFaction == null) {
                    continue;
                }

                StarSystemAPI target = getBestTargetSystem(targetFaction);
                if (target != null) {
                    bestSystem = target;
                    break;
                }

                factionList.remove(targetFaction);
            }
        }

        return bestSystem;
    }

    private StarSystemAPI getBestTargetSystem(FactionAPI faction) {

        HashMap<StarSystemAPI, Float> systemRatingMap = new HashMap<>();

        debugMessage("targeted faction: " + faction.getDisplayName());

        for (StarSystemAPI system : getFactionStarSystemList(faction)) {
            if (system == null) continue;

            debugMessage("system name: " + system.getName());
            //check if it has been raided too recently
            if (RaidTimeout.containsSystem(system, !market.isPlayerOwned())) {
                debugMessage("System locked");
                continue;
            }

            float totalSystemVal = 0;

            //give each system a rating depending on total defences and export values, add it to a map
            for (MarketAPI market : getHostileMarketsInSystem(system)) {
                if (market.getPrimaryEntity().isInHyperspace()) continue;

                //bad:
                boolean hasStation = market.hasIndustry(Industries.ORBITALSTATION) || market.hasIndustry(Industries.ORBITALSTATION_MID) || market.hasIndustry(Industries.ORBITALSTATION_HIGH);
                boolean milHQ = market.hasIndustry(Industries.PATROLHQ);
                boolean milBA = market.hasIndustry(Industries.MILITARYBASE);
                boolean milCO = market.hasIndustry(Industries.HIGHCOMMAND);

                //good:
                float marketSize = market.getSize();
                boolean hasHIorOW = market.hasIndustry(Industries.HEAVYINDUSTRY) || market.hasIndustry(Industries.ORBITALWORKS) || market.hasIndustry("ms_modularFac") || market.hasIndustry("ms_massIndustry");
                int totalOutputAmounts = 0;
                for (Map.Entry<String, Integer> output : getMarketProductionAmounts(market).entrySet()) {
                    totalOutputAmounts += output.getValue();
                }

                float defenceRating = 0;
                for (CampaignFleetAPI fleet : Misc.getFleetsInOrNearSystem(system)) {
                    if (fleet.getFaction().isHostileTo(this.market.getFaction()))
                        defenceRating -= fleet.getFleetPoints();
                }

                defenceRating += getBaseRaidFP() * 1.5f;

                //calc:
                totalSystemVal += defenceRating; //if there are less things in the system than the raiding fleet has FP, make the system more desirable - else, fuck off

                totalSystemVal -= hasStation ? 20 : -10;
                totalSystemVal -= milHQ ? 1 * marketSize : -5;
                totalSystemVal -= milBA ? 3 * marketSize : 0;
                totalSystemVal -= milCO ? 4 * marketSize : 0;

                totalSystemVal += marketSize;
                totalSystemVal += hasHIorOW ? 20 : 0;
                totalSystemVal += (int) (totalOutputAmounts / 2f);

                debugMessage("system market: " + market.getName());
            }

            debugMessage("system rating: " + totalSystemVal);
            systemRatingMap.put(system, totalSystemVal);
        }

        //pick the system with the highest rating from the list and return it
        float bestSystemValue = -Float.MAX_VALUE;
        StarSystemAPI bestSystem = null;

        for (Map.Entry<StarSystemAPI, Float> ratedSystem : systemRatingMap.entrySet()) {
            if (ratedSystem.getValue() > bestSystemValue) {
                bestSystemValue = ratedSystem.getValue();
                bestSystem = ratedSystem.getKey();
            }
        }

        if (bestSystem != null) {
            debugMessage("raid target: " + bestSystem.getName());
        } else {
            debugMessage("no valid target!");
        }

        return bestSystem;
    }

    public void startRaid(StarSystemAPI target, float baseRaidFP) {
        debugMessage("starting a raid");

        StarSystemAPI system = market.getStarSystem();
        SectorEntityToken entity = market.getPrimaryEntity();
        FactionAPI faction = market.getFaction();

        //check target system for valid targets
        boolean hasTargets = false;
        for (MarketAPI curr : IndustryHelper.getMarketsInLocation(target)) {
            if (curr.getFaction().isHostileTo(faction)) {
                hasTargets = true;
                break;
            }
        }

        if (!hasTargets) return;

        //make new raid
        RaidIntel raid = new PrivateerBaseRaidIntel(target, faction, this);

        //float raidFP = 1000;
        float successMult = 0.5f;

        JumpPointAPI gather = null;
        List<JumpPointAPI> points = system.getEntities(JumpPointAPI.class);
        float min = Float.MAX_VALUE;
        for (JumpPointAPI curr : points) {
            float dist = Misc.getDistance(entity.getLocation(), curr.getLocation());
            if (dist < min) {
                min = dist;
                gather = curr;
            }
        }

        AssembleStage assemble = new AssembleStage(raid, gather);
        assemble.addSource(market);
        assemble.setSpawnFP(baseRaidFP);
        assemble.setAbortFP(baseRaidFP * successMult);
        raid.addStage(assemble);

        SectorEntityToken raidJump = RouteLocationCalculator.findJumpPointToUse(faction, target.getCenter());

        TravelStage travel = new TravelStage(raid, gather, raidJump, false);
        travel.setAbortFP(baseRaidFP * successMult);
        raid.addStage(travel);

        PirateRaidActionStage action = new PirateRaidActionStage(raid, target);
        action.setAbortFP(baseRaidFP * successMult);
        raid.addStage(action);

        raid.addStage(new ReturnStage(raid));

        debugMessage("Raid target: " + target.getName() + " / faction: " + faction.getDisplayName() + " / FP: " + baseRaidFP);

        //always notify!
        Global.getSector().getIntelManager().addIntel(raid, false);
        currentIntel = raid;
        RaidTimeout.addRaidedSystem(target, 3f, !market.isPlayerOwned());
    }

    public ArrayList<FactionAPI> getActiveHostileFactions() {
        ArrayList<FactionAPI> activeHostileFactions = new ArrayList<>();

        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            FactionAPI targetFaction = market.getFaction();

            if (!activeHostileFactions.contains(targetFaction)
                    && targetFaction.isHostileTo(this.market.getFaction())
                    && targetFaction.isShowInIntelTab()
                    && !market.isHidden()
                    && market.isInEconomy()) {

                activeHostileFactions.add(market.getFaction());
            }
        }
        return activeHostileFactions;
    }

    public ArrayList<StarSystemAPI> getFactionStarSystemList(FactionAPI faction) {
        ArrayList<StarSystemAPI> systemList = new ArrayList<>();

        for (MarketAPI market : Misc.getFactionMarkets(faction)) {
            if (!systemList.contains(market.getStarSystem()) && !market.isHidden() && market.isInEconomy()) {
                systemList.add(market.getStarSystem());
            }
        }
        return systemList;
    }

    public ArrayList<MarketAPI> getHostileMarketsInSystem(StarSystemAPI system) {
        ArrayList<MarketAPI> marketList = new ArrayList<>();

        for (MarketAPI market : IndustryHelper.getMarketsInLocation(system)) {
            if (market.getFaction().isHostileTo(this.market.getFaction())) {
                marketList.add(market);
            }
        }
        return marketList;
    }

    public HashMap<String, Integer> getMarketProductionAmounts(MarketAPI market) {
        HashMap<String, Integer> prod = new HashMap<>();

        for (Industry ind : market.getIndustries()) {
            for (MutableCommodityQuantity sup : ind.getAllSupply()) {
                if (prod.containsKey(sup.getCommodityId()) && prod.get(sup.getCommodityId()) < sup.getQuantity().getModifiedInt()) {
                    prod.put(sup.getCommodityId(), sup.getQuantity().getModifiedInt());
                    continue;
                }
                if (!prod.containsKey(sup.getCommodityId())) {
                    prod.put(sup.getCommodityId(), sup.getQuantity().getModifiedInt());
                }
            }
        }
        return prod;
    }

    public ArrayList<MarketAPI> getHostileShipUser(StarSystemAPI system) {
        ArrayList<MarketAPI> list = new ArrayList<>();
        for (MarketAPI market : getHostileMarketsInSystem(system)) {
            boolean HI = market.hasIndustry(Industries.HEAVYINDUSTRY);
            boolean OW = market.hasIndustry(Industries.ORBITALWORKS);
            boolean SY = market.hasIndustry("ms_modularFac") || market.hasIndustry("ms_massIndustry");

            boolean MB = market.hasIndustry(Industries.MILITARYBASE);
            boolean HC = market.hasIndustry(Industries.HIGHCOMMAND);

            if (HI || OW || MB || HC || SY) {
                list.add(market);
            }
        }
        return list;
    }

    @Override
    protected void addRightAfterDescriptionSection(TooltipMakerAPI tooltip, IndustryTooltipMode mode) {
        if (!this.market.isPlayerOwned()) {
            return;
        }

        if (currTooltipMode == IndustryTooltipMode.ADD_INDUSTRY && isAvailableToBuild()) {
            tooltip.addPara("%s", 10F, Misc.getHighlightColor(), new String[]{"Requires 2 industry slots!"});
            tooltip.addPara("%s", 3f, Misc.getHighlightColor(), new String[]{"Can only have two in the star system."});
        }

        if (isFunctional()) {
            float opad = 5.0F;

            if (isFunctional() && currTooltipMode == IndustryTooltipMode.NORMAL) {
                if (currentIntel != null) {
                    tooltip.addPara("There is currently %s, targeting the %s system.", 10F, Misc.getHighlightColor(), new String[]{"an active raid", currentIntel.getSystem().getName()});
                } else if (raidTimeoutMonths < 1) {
                    tooltip.addPara("There is currently %s. The next one will be attempted %s.", 10F, Misc.getHighlightColor(), new String[]{"no active raid", "this month"});
                } else {
                    tooltip.addPara("There is currently %s. The next one can be attempted in about %s.", 10F, Misc.getHighlightColor(), new String[]{"no active raid", raidTimeoutMonths + " months"});
                }
            }
        }
    }

    //AI core handling

    protected void addAlphaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = Misc.getHighlightColor();
        String pre = "Alpha-level AI core currently assigned. ";
        if (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Alpha-level AI core. ";
        }

        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + "Increases raid strength by %s, and %s the raiders bring home.", 0.0F, highlight, new String[]{"20%", "increases the amount of spoils"});
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + "Increases raid strength by %s, and %s the raiders bring home.", opad, highlight, new String[]{"20%", "increases the amount of spoils"});
        }
    }

    protected void addBetaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = Misc.getHighlightColor();
        String pre = "Beta-level AI core currently assigned. ";
        if (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Beta-level AI core. ";
        }

        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + "Increases raid strength by %s, decreases the time between %s on the same system.", 0f, highlight, new String[]{"10%", "consequent raids"});
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + "Increases raid strength by %s, decreases the time between %s on the same system.", opad, highlight, new String[]{"10%", "consequent raids"});
        }
    }

    protected void addGammaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = Misc.getHighlightColor();
        String pre = "Gamma-level AI core currently assigned. ";
        if (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Gamma-level AI core. ";
        }

        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + "Delays output decay by %s. Only works if installed before raid completion.", 0.0F, highlight, new String[]{"1 Month"});
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + "Delays output decay by %s. Only works if installed before raid completion.", 0.0F, highlight, new String[]{"1 Month"});
        }
    }

    protected void applyAICoreToIncomeAndUpkeep() {
        if (Commodities.ALPHA_CORE.equals(aiCoreId)) {
            aiCoreFPBonus = 1.2f;
        } else if (Commodities.BETA_CORE.equals(aiCoreId)) {
            aiCoreFPBonus = 1.1f;
        } else {
            aiCoreFPBonus = 1f;
        }
    }

    protected void updateAICoreToSupplyAndDemandModifiers() {
    }
}


