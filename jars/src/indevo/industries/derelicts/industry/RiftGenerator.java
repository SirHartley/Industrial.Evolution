package indevo.industries.derelicts.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.industries.derelicts.scripts.PlanetMovingScript;
import indevo.utils.ModPlugin;
import indevo.utils.helper.IndustryHelper;
import indevo.utils.helper.Settings;
import indevo.utils.timers.NewDayListener;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static indevo.industries.derelicts.industry.Ruins.INDUSTRY_ID_MEMORY_KEY;

public class RiftGenerator extends BaseIndustry implements NewDayListener {
    public static final Logger log = Global.getLogger(RiftGenerator.class);

    private boolean debug = false;

    private int daysPassed = 999;
    private int daysRequired = 90;

    private final int alphaCoreDayReduction = 30;
    private final int betaCoreRangeIncrease = 3;

    private float rangeRadiusLY = 9f;

    public enum TargetMode {
        CORE,
        COLONY,
        FRINGE,
        RANDOM
    }

    @Override
    public void init(String id, MarketAPI market) {
        super.init(id, market);
        if (market.hasIndustry(getId())) spec.setDowngrade(null);
    }

    @Override
    public IndustrySpecAPI getSpec() {
        if (spec == null) spec = Global.getSettings().getIndustrySpec(id);
        if (market.hasIndustry(getId())) spec.setDowngrade(null);
        return spec;
    }

    @Override
    public void apply() {
        super.apply(true);

        debug = Global.getSettings().isDevMode();

        //needed so it doesn't display a downgrade option
        if (market.hasIndustry(getId())) spec.setDowngrade(null);

        Global.getSector().getListenerManager().addListener(this, true);
    }

    @Override
    public void unapply() {
        super.unapply();

        //needed so it doesn't display as buildable
        spec.setDowngrade(Ids.RUINS);

        Global.getSector().getListenerManager().removeListener(this);
    }

    @Override
    public boolean isAvailableToBuild() {
        String id = market.getMemoryWithoutUpdate().getString(INDUSTRY_ID_MEMORY_KEY);

        boolean check = (id != null && getId().equals(id))
                && (market.hasIndustry(Ids.RUINS) && !(market.getIndustry(Ids.RUINS).isUpgrading())); //no ruins id specified or wrong general ID

        return check && super.isAvailableToBuild();
    }

    @Override
    public boolean showWhenUnavailable() {
        return false;
    }

    @Override
    public void onNewDay() {
        if (isFunctional()) {
            daysPassed++;
        }
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        if (!market.isPlanetConditionMarketOnly() && !market.hasCondition(Ids.COND_CRYODISABLE)) {
            market.addCondition(Ids.COND_CRYODISABLE);
            ModPlugin.log("Adding cryo_disabler to market " + market.getId());
        }
    }

    @Override
    protected String getDescriptionOverride() {
        if (currTooltipMode == null || currTooltipMode != IndustryTooltipMode.NORMAL) {
            return "The claustrophobic tunnels of the underground facility eventually give way to a cavern with a massive center structure atop a spider web of power lines. Its purpose is unknown.";
        } else
            return "There is a suspicious absence of walkways and doors in the interiors of this facility. Data found on the extremely advanced network nodes wired through the machinery indicate it as a prototype large scale displacer unit."
                    + "\n\nThe control center is only reachable by crawling through a service tunnel. It was seemingly added as an afterthought.";
    }

    @Override
    protected void addRightAfterDescriptionSection(TooltipMakerAPI tooltip, IndustryTooltipMode mode) {
        super.addRightAfterDescriptionSection(tooltip, mode);

        if (isFunctional() && mode == IndustryTooltipMode.NORMAL) {
            if (isReadyToMove() && moveIsLegal()) {
                tooltip.addPara("The Generator is %s.", 10f, Misc.getPositiveHighlightColor(), new String[]{"ready to fire"});
            } else if (moveIsLegal()) {
                tooltip.addPara("The Generator is %s - it will be ready in %s.", 10f, Misc.getHighlightColor(), new String[]{"recharging", getDaysUntilReady() + " days"});
            } else {
                tooltip.addPara("The Generator is %s due to an %s disrupting the targeting sensors.", 10f, Misc.getNegativeHighlightColor(), new String[]{"unable to fire", "entity in orbit"});
            }

            tooltip.addPara("Current range: %s - can fluctuate depending on setting.", 2f, Misc.getHighlightColor(), new String[]{(int) getRangeLY() + " LY"});
        }
    }

    public boolean moveIsLegal() {
        boolean legal = true;

        if (IndustryHelper.planetHasRings(market)) legal = false;

        for (SectorEntityToken e : market.getStarSystem().getAllEntities()) {
            if (e.getOrbitFocus() != null
                    && e.getOrbitFocus().equals(market.getPrimaryEntity())
                    && e.getCustomEntityType() != null
                    && (e.getCustomEntityType().contains("boggled")
                    || e.getCustomEntityType().contains("mirror")
                    || e.getCustomEntityType().contains("shade"))) {

                legal = false;
                break;
            }
        }

        if (debug) log.info("Checking if move is legal - " + legal);

        return legal;
    }

    public boolean isReadyToMove() {
        return getDaysUntilReady() <= 1 || debug;
    }

    public int getDaysUntilReady() {
        return Math.max(getDaysRequired() - daysPassed, 0);
    }

    public void initRift(TargetMode mode) {
        log.info("Init Jump - " + mode);

        StarSystemAPI target = getTarget(mode);

        if (target != market.getStarSystem()) {
            movePlanet(target);
            RecentUnrest.get(market).add(1, "Recent Rift Generator usage");
        } else {
            log.warn("Cannot move planet: target returned base system");
        }
    }

    private List<StarSystemAPI> getNearbySystems(Vector2f center, float distLY) {
        List<StarSystemAPI> result = new ArrayList<>();
        for (StarSystemAPI s : getCleanedSystemList()) {
            float dist = Misc.getDistanceLY(center, s.getLocation());
            if (dist > distLY) continue;
            result.add(s);
        }

        return result;
    }

    private int getArtificialRiftAmount(StarSystemAPI system) {
        int count = 0;
        for (SectorEntityToken token : system.getJumpPoints()) {
            if (token.getId().contains(PlanetMovingScript.ARTIFICIAL_RIFT_ID)) count++;
        }

        return count;
    }

    private List<StarSystemAPI> getCleanedSystemList() {
        //return a list with all eligible systems - clean out systems that are blacklisted, contain rifts, or other stuff.

        List<StarSystemAPI> systemList = new ArrayList<>(Global.getSector().getStarSystems());
        List<StarSystemAPI> systemListToRemove = new ArrayList<>();
        systemList.remove(market.getStarSystem());

        //gamma core effect
        int riftAmountLimit = getAiCoreIdNotNull().equals(Commodities.GAMMA_CORE) ? 2 : 1;

        for (StarSystemAPI s : systemList) {
            boolean overRiftLimit = getArtificialRiftAmount(s) >= riftAmountLimit;
            //todo system blacklist goes here!

            if (overRiftLimit) systemListToRemove.add(s);
        }

        systemList.removeAll(systemListToRemove);
        return systemList;
    }

    private StarSystemAPI getClosestSystemToTarget(Vector2f target, List<StarSystemAPI> fromList) {
        StarSystemAPI bestTarget = market.getStarSystem();
        float shortestDistanceToTarget = Float.MAX_VALUE;

        for (StarSystemAPI s : fromList) {
            float distanceToTargetLY = Misc.getDistanceLY(target, s.getLocation());

            if (distanceToTargetLY < shortestDistanceToTarget) {
                shortestDistanceToTarget = distanceToTargetLY;
                bestTarget = s;
            }
        }

        return bestTarget;
    }

    private StarSystemAPI getClosestSystemToTarget(StarSystemAPI target, List<StarSystemAPI> fromList) {
        return getClosestSystemToTarget(target.getLocation(), fromList);
    }

    private StarSystemAPI getTarget(TargetMode mode) {
        StarSystemAPI target;
        List<StarSystemAPI> systemList = getCleanedSystemList();

        Vector2f currentLoc = market.getLocationInHyperspace();
        float range = getRangeLY();

        StarSystemAPI bestTarget = market.getStarSystem();
        float shortestDistanceToTarget = Float.MAX_VALUE;
        List<StarSystemAPI> targetList;

        //get sector center, either 0/0 or duzahk
        Vector2f center = Global.getSector().getStarSystem("Askonia") != null ? Global.getSector().getStarSystem("Askonia").getLocation() : new Vector2f(0, 0);

        switch (mode) {
            case COLONY:
                //get the closest faction market
                for (MarketAPI market : Misc.getFactionMarkets(this.market.getFaction())) {
                    if (market.getPrimaryEntity() != null && market.getPrimaryEntity().getTags().contains("nex_playerOutpost"))
                        continue;
                    if (market.getStarSystem() == this.market.getStarSystem()) continue;

                    float distanceToTargetLY = Misc.getDistanceLY(market.getLocationInHyperspace(), currentLoc);

                    if (debug)
                        log.info("Target Colony - checking " + market.getStarSystem().getName() + " at " + distanceToTargetLY);

                    if (distanceToTargetLY < shortestDistanceToTarget) {
                        shortestDistanceToTarget = distanceToTargetLY;
                        bestTarget = market.getStarSystem();
                        if (debug) log.info("updating best!");
                    }
                }

                //if it's an illegal target, get the closest system to it
                if (!systemList.contains(bestTarget)) {
                    bestTarget = getClosestSystemToTarget(bestTarget, systemList);
                }

                target = bestTarget;
                range *= 0.9f;
                break;
            case FRINGE:
                //get the system the farthest away from the core within a range*2 radius from here
                targetList = getNearbySystems(currentLoc, range * 2);
                targetList.remove(market.getStarSystem());

                float longestDistanceToTarget = 0f;

                for (StarSystemAPI system : targetList) {
                    float distanceToTargetLY = Misc.getDistanceLY(center, system.getLocation());

                    if (distanceToTargetLY > longestDistanceToTarget) {
                        longestDistanceToTarget = distanceToTargetLY;
                        bestTarget = system;
                    }
                }

                target = bestTarget;
                break;
            case CORE:
                //get a list of all star systems 15 ly around center or Duzahk
                targetList = getNearbySystems(center, 6f);
                targetList.remove(market.getStarSystem());

                //if there are no systems in the core, we'll pick the system closest to the sector center.
                if (targetList.isEmpty()) {
                    target = getClosestSystemToTarget(center, systemList);

                } else {
                    // otherwise, pick a random one from the list as target
                    target = targetList.get(new Random().nextInt(targetList.size() - 1));
                }

                range *= 0.8f;

                break;
            case RANDOM:
            default:
                //target a random system
                target = systemList.get(new Random().nextInt(systemList.size() - 1));

                //increase the range for lols
                range *= 1.3f;
        }


        float dist = Misc.getDistanceLY(target.getLocation(), currentLoc);
        if (debug) log.info("Target: " + target.getName() + " at distance " + dist);

        //if the target is in range, return it
        if (dist < range) return target;

        //else get the best system between target and market in range
        StarSystemAPI bestSystem = market.getStarSystem();
        shortestDistanceToTarget = Float.MAX_VALUE;

        for (StarSystemAPI system : systemList) {
            Vector2f loc = system.getLocation();
            float distanceToBaseLY = Misc.getDistanceLY(loc, currentLoc);

            //proceed if it's in range
            if (distanceToBaseLY < range) {
                float distanceToTargetLY = Misc.getDistanceLY(loc, target.getLocation());
                String s = system.getName() + " dist Base: " + distanceToBaseLY + " dist Target: " + distanceToTargetLY;

                if (distanceToTargetLY < shortestDistanceToTarget) {
                    shortestDistanceToTarget = distanceToTargetLY;
                    bestSystem = system;

                    s += " | setting as new best!";
                }

                if (debug) log.info(s);
            }
        }

        return bestSystem;
    }

    private void movePlanet(StarSystemAPI target) {
        log.info("Moving planet to " + target.getName());
        daysPassed = 0;
        PlanetMovingScript script = new PlanetMovingScript(market, target);

        Global.getSector().addScript(script);
    }

    private String getAiCoreIdNotNull() {
        if (getAICoreId() != null) {
            return getAICoreId();
        }
        return "none";
    }

    @Override
    protected void applyAICoreModifiers() {
        super.applyAICoreModifiers();
    }

    private float getRangeLY() {
        rangeRadiusLY = Settings.getInt(Settings.RG_RANGE);
        return getAiCoreIdNotNull().equals(Commodities.BETA_CORE) ? rangeRadiusLY + betaCoreRangeIncrease : rangeRadiusLY;
    }

    private int getDaysRequired() {
        daysRequired = Settings.getInt(Settings.RG_COOLDOWN_TIME);
        return getAiCoreIdNotNull().equals(Commodities.ALPHA_CORE) ? daysRequired - alphaCoreDayReduction : daysRequired;
    }

    //AI-Core tooltips
    @Override
    protected void addAlphaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        String pre = "Alpha-level AI core currently assigned. ";
        if (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Alpha-level AI core. ";
        }

        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(this.aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + "Reduces the Generator recharge time by %s.", 0f, Misc.getHighlightColor(), new String[]{alphaCoreDayReduction + " days"});
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + "Reduces the Generator recharge time by %s.", opad, Misc.getHighlightColor(), new String[]{alphaCoreDayReduction + " days"});
        }
    }

    @Override
    protected void addBetaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        String pre = "Beta-level AI core currently assigned. ";
        if (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Beta-level AI core. ";
        }

        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(this.aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + "Increases jump range by %s", 0f, Misc.getHighlightColor(), new String[]{Math.round(betaCoreRangeIncrease) + " light years"});
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + "Increases jump range by %s", opad, Misc.getHighlightColor(), new String[]{Math.round(betaCoreRangeIncrease) + " light years"});
        }
    }

    @Override
    protected void addGammaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        String pre = "Gamma-level AI core currently assigned. ";
        if (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Gamma-level AI core. ";
        }

        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(this.aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + "Allows to %s star systems that already have an Artificial Rift %s.", 0f, Misc.getHighlightColor(), new String[]{"retarget", "once"});
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + "Allows to %s star systems that already have an Artificial Rift %s.", opad, Misc.getHighlightColor(), new String[]{"retarget", "once"});
        }
    }

    @Override
    protected void applyAICoreToIncomeAndUpkeep() {
    }

    @Override
    protected void updateAICoreToSupplyAndDemandModifiers() {
    }
}
