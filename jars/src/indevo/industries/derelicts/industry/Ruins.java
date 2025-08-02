package indevo.industries.derelicts.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.DebugFlags;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Strings;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.IconRenderMode;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;

import java.awt.*;

public class Ruins extends BaseIndustry {

    public static final String INDUSTRY_ID_MEMORY_KEY = "$IndEvo_ruinsIndustryId";

    private static void log(String Text) {
        Global.getLogger(Ruins.class).info(Text);
    }

    private String upgradeSpec = null;

    @Override
    public void apply() {
        setUpgradeSpec();
        triggerUpgradeForAI();
    }

    @Override
    public boolean showShutDown() {
        return false;
    }

    @Override
    public boolean isAvailableToBuild() {
        return false;
    }

    @Override
    public boolean showWhenUnavailable() {
        return false;
    }

    @Override
    public boolean canInstallAICores() {
        return false;
    }

    @Override
    public void notifyBeingRemoved(MarketAPI.MarketInteractionMode mode, boolean forUpgrade) {
        super.notifyBeingRemoved(mode, forUpgrade);

        if (market.hasCondition(Ids.COND_RUINS)) {
            market.removeSpecificCondition(market.getCondition(Ids.COND_RUINS).getIdForPluginModifications());
        }
    }

    private void setUpgradeSpec() {
        MemoryAPI memory = market.getMemoryWithoutUpdate();
        if (!memory.contains(INDUSTRY_ID_MEMORY_KEY)) return;

        if (upgradeSpec == null) upgradeSpec = memory.getString(INDUSTRY_ID_MEMORY_KEY);

        //target was already set, parse from memory
        getSpec().setUpgrade(upgradeSpec);
        if (Global.getSettings().isDevMode()) log("picking upgrade for ruins: " + spec.getUpgrade());
    }

    private void triggerUpgradeForAI() {

        if (isFunctional()
                && market.getFaction() != Global.getSector().getPlayerFaction()
                && !market.isPlayerOwned()
                && upgradeSpec != null) {

            boolean sizeReq = market.getSize() > 4;
            boolean indSlots = Misc.getNumIndustries(market) < Misc.getMaxIndustries(market);
            boolean marketAmount = Misc.getFactionMarkets(market.getFaction()).size() > 2;

            if (sizeReq && indSlots && marketAmount) {
                startUpgrading();
            }
        }
    }

    @Override
    public boolean isIndustry() {
        return false;
    }

    protected void sendBuildOrUpgradeMessage() {
        if (market.isPlayerOwned()) {
            MessageIntel intel = new MessageIntel(getCurrentName() + " at " + market.getName(), Misc.getBasePlayerColor());
            intel.addLine(BaseIntelPlugin.BULLET + "Restoration completed");
            intel.setIcon(Global.getSector().getPlayerFaction().getCrest());
            intel.setSound(BaseIntelPlugin.getSoundStandardUpdate());
            Global.getSector().getCampaignUI().addMessage(intel, CommMessageAPI.MessageClickAction.COLONY_INFO, market);
        }
    }

    public void createTooltip(IndustryTooltipMode mode, TooltipMakerAPI tooltip, boolean expanded) {
        currTooltipMode = mode;

        float pad = 3f;
        float opad = 10f;

        FactionAPI faction = market.getFaction();
        Color color = faction.getBaseUIColor();
        Color dark = faction.getDarkUIColor();
        Color grid = faction.getGridUIColor();
        Color bright = faction.getBrightUIColor();

        Color gray = Misc.getGrayColor();
        Color highlight = Misc.getHighlightColor();
        Color bad = Misc.getNegativeHighlightColor();


        MarketAPI copy = market.clone();
        MarketAPI orig = market;

        //int numBeforeAdd = MiscIE.getNumIndustries(market);

        market = copy;
        boolean needToAddIndustry = !market.hasIndustry(getId());
        //addDialogMode = true;
        if (needToAddIndustry) market.getIndustries().add(this);

        if (mode != IndustryTooltipMode.NORMAL) {
            market.clearCommodities();
            for (CommodityOnMarketAPI curr : market.getAllCommodities()) {
                curr.getAvailableStat().setBaseValue(100);
            }
        }

//		if (addDialogMode) {
//			market.reapplyConditions();
//			apply();
//		}
        market.reapplyConditions();
        reapply();

        String type = "";
        if (isIndustry()) type = " - Industry";
        if (isStructure()) type = " - Structure";

        tooltip.addTitle(getCurrentName() + type, color);

        String desc = spec.getDesc();
        String override = getDescriptionOverride();
        if (override != null) {
            desc = override;
        }
        desc = Global.getSector().getRules().performTokenReplacement(null, desc, market.getPrimaryEntity(), null);

        tooltip.addPara(desc, opad);

//		Industry inProgress = MiscIE.getCurrentlyBeingConstructed(market);
//		if ((mode == IndustryTooltipMode.ADD_INDUSTRY && inProgress != null) ||
//				(mode == IndustryTooltipMode.UPGRADE && inProgress != null)) {
//			//tooltip.addPara("Another project (" + inProgress.getCurrentName() + ") in progress", bad, opad);
//			//tooltip.addPara("Already building: " + inProgress.getCurrentName() + "", bad, opad);
//			tooltip.addPara("Another construction in progress: " + inProgress.getCurrentName() + "", bad, opad);
//		}

        //tooltip.addPara("Type: %s", opad, gray, highlight, hullSize);
        if (isIndustry() && (mode == IndustryTooltipMode.ADD_INDUSTRY ||
                mode == IndustryTooltipMode.UPGRADE ||
                mode == IndustryTooltipMode.DOWNGRADE)
        ) {

            int num = Misc.getNumIndustries(market);
            int max = Misc.getMaxIndustries(market);


            // during the creation of the tooltip, the market has both the current industry
            // and the upgrade/downgrade. So if this upgrade/downgrade counts as an industry, it'd count double if
            // the current one is also an industry. Thus reduce num by 1 if that's the case.
            if (isIndustry()) {
                if (mode == IndustryTooltipMode.UPGRADE) {
                    for (Industry curr : market.getIndustries()) {
                        if (getSpec().getId().equals(curr.getSpec().getUpgrade())) {
                            if (curr.isIndustry()) {
                                num--;
                            }
                            break;
                        }
                    }
                } else if (mode == IndustryTooltipMode.DOWNGRADE) {
                    for (Industry curr : market.getIndustries()) {
                        if (getSpec().getId().equals(curr.getSpec().getDowngrade())) {
                            if (curr.isIndustry()) {
                                num--;
                            }
                            break;
                        }
                    }
                }
            }

            Color c = gray;
            c = Misc.getTextColor();
            Color h1 = highlight;
            if (num > max) {// || (num >= max && mode == IndustryTooltipMode.ADD_INDUSTRY)) {
                //c = bad;
                h1 = bad;
                num--;

                tooltip.addPara("Maximum number of industries reached", bad, opad);
            }
            //tooltip.addPara("Maximum of %s industries on a colony of this size. Currently: %s.",
//			LabelAPI label = tooltip.addPara("Maximum industries for a colony of this size: %s. Industries: %s. ",
//					opad, c, h1, "" + max, "" + num);
//			label.setHighlightColors(h2, h1);
        }


        addRightAfterDescriptionSection(tooltip, mode);

        if (isDisrupted()) {
            int left = (int) getDisruptedDays();
            if (left < 1) left = 1;
            String days = "days";
            if (left == 1) days = "day";

            tooltip.addPara("Operations disrupted! %s " + days + " until return to normal function.",
                    opad, Misc.getNegativeHighlightColor(), highlight, "" + left);
        }

        if (DebugFlags.COLONY_DEBUG || market.isPlayerOwned()) {
            if (mode == IndustryTooltipMode.NORMAL) {
                if (getSpec().getUpgrade() != null && !isBuilding()) {
                    tooltip.addPara("Click to manage or restore", Misc.getPositiveHighlightColor(), opad);
                } else {
                    tooltip.addPara("Click to manage", Misc.getPositiveHighlightColor(), opad);
                }
                //tooltip.addPara("Click to manage", market.getFaction().getBrightUIColor(), opad);
            }
        }

        if (mode == IndustryTooltipMode.QUEUED) {
            tooltip.addPara("Click to remove or adjust position in queue", Misc.getPositiveHighlightColor(), opad);
            tooltip.addPara("Currently queued for construction. Does not have any impact on the colony.", opad);

            int left = (int) (getSpec().getBuildTime());
            if (left < 1) left = 1;
            String days = "days";
            if (left == 1) days = "day";
            tooltip.addPara("Requires %s " + days + " to build.", opad, highlight, "" + left);

            //return;
        } else if (!isFunctional() && mode == IndustryTooltipMode.NORMAL) {
            tooltip.addPara("Currently under construction and not producing anything or providing other benefits.", opad);

            int left = (int) (buildTime - buildProgress);
            if (left < 1) left = 1;
            String days = "days";
            if (left == 1) days = "day";
            tooltip.addPara("Requires %s more " + days + " to finish building.", opad, highlight, "" + left);
        }


        if (!isAvailableToBuild() &&
                (mode == IndustryTooltipMode.ADD_INDUSTRY ||
                        mode == IndustryTooltipMode.UPGRADE ||
                        mode == IndustryTooltipMode.DOWNGRADE)) {
            String reason = getUnavailableReason();
            if (reason != null) {
                tooltip.addPara(reason, bad, opad);
            }
        }

        boolean category = getSpec().hasTag(Industries.TAG_PARENT);

        if (!category) {
            int credits = (int) Global.getSector().getPlayerFleet().getCargo().getCredits().get();
            String creditsStr = Misc.getDGSCredits(credits);
            if (mode == IndustryTooltipMode.UPGRADE || mode == IndustryTooltipMode.ADD_INDUSTRY) {
                int cost = (int) getBuildCost();
                String costStr = Misc.getDGSCredits(cost);

                int days = (int) getBuildTime();
                String daysStr = "days";
                if (days == 1) daysStr = "day";

                LabelAPI label = null;
                if (mode == IndustryTooltipMode.UPGRADE) {
                    label = tooltip.addPara("%s and %s " + daysStr + " to restore. You have %s.", opad,
                            highlight, costStr, "" + days, creditsStr);
                } else {
                    label = tooltip.addPara("%s and %s " + daysStr + " to build. You have %s.", opad,
                            highlight, costStr, "" + days, creditsStr);
                }
                label.setHighlight(costStr, "" + days, creditsStr);
                if (credits >= cost) {
                    label.setHighlightColors(highlight, highlight, highlight);
                } else {
                    label.setHighlightColors(bad, highlight, highlight);
                }
            } else if (mode == IndustryTooltipMode.DOWNGRADE) {
                float refundFraction = Global.getSettings().getFloat("industryRefundFraction");
                int cost = (int) (getBuildCost() * refundFraction);
                String refundStr = Misc.getDGSCredits(cost);

                tooltip.addPara("%s refunded for downgrade.", opad, highlight, refundStr);
            }


            addPostDescriptionSection(tooltip, mode);

            if (!getIncome().isUnmodified()) {
                int income = getIncome().getModifiedInt();
                tooltip.addPara("Monthly income: %s", opad, highlight, Misc.getDGSCredits(income));
                tooltip.addStatModGrid(250, 65, 10, pad, getIncome(), true, new TooltipMakerAPI.StatModValueGetter() {
                    public String getPercentValue(MutableStat.StatMod mod) {
                        return null;
                    }

                    public String getMultValue(MutableStat.StatMod mod) {
                        return null;
                    }

                    public Color getModColor(MutableStat.StatMod mod) {
                        return null;
                    }

                    public String getFlatValue(MutableStat.StatMod mod) {
                        return Misc.getWithDGS(mod.value) + Strings.C;
                    }
                });
            }

            if (!getUpkeep().isUnmodified()) {
                int upkeep = getUpkeep().getModifiedInt();
                tooltip.addPara("Monthly upkeep: %s", opad, highlight, Misc.getDGSCredits(upkeep));
                tooltip.addStatModGrid(250, 65, 10, pad, getUpkeep(), true, new TooltipMakerAPI.StatModValueGetter() {
                    public String getPercentValue(MutableStat.StatMod mod) {
                        return null;
                    }

                    public String getMultValue(MutableStat.StatMod mod) {
                        return null;
                    }

                    public Color getModColor(MutableStat.StatMod mod) {
                        return null;
                    }

                    public String getFlatValue(MutableStat.StatMod mod) {
                        return Misc.getWithDGS(mod.value) + Strings.C;
                    }
                });
            }

            addPostUpkeepSection(tooltip, mode);

            boolean hasSupply = false;
            for (MutableCommodityQuantity curr : supply.values()) {
                int qty = curr.getQuantity().getModifiedInt();
                if (qty <= 0) continue;
                hasSupply = true;
                break;
            }
            boolean hasDemand = false;
            for (MutableCommodityQuantity curr : demand.values()) {
                int qty = curr.getQuantity().getModifiedInt();
                if (qty <= 0) continue;
                hasDemand = true;
                break;
            }

            float maxIconsPerRow = 10f;
            if (hasSupply) {
                tooltip.addSectionHeading("Production", color, dark, Alignment.MID, opad);
                tooltip.beginIconGroup();
                tooltip.setIconSpacingMedium();
                float icons = 0;
                for (MutableCommodityQuantity curr : supply.values()) {
                    //if (qty <= 0) continue;

                    if (curr.getQuantity().getModifiedInt() > 0) {
                        tooltip.addIcons(market.getCommodityData(curr.getCommodityId()), curr.getQuantity().getModifiedInt(), IconRenderMode.NORMAL);
                    }

                    int plus = 0;
                    int minus = 0;
                    for (MutableStat.StatMod mod : curr.getQuantity().getFlatMods().values()) {
                        if (mod.value > 0) {
                            plus += (int) mod.value;
                        } else if (mod.desc != null && mod.desc.contains("shortage")) {
                            minus += (int) Math.abs(mod.value);
                        }
                    }
                    minus = Math.min(minus, plus);
                    if (minus > 0 && mode == IndustryTooltipMode.NORMAL) {
                        tooltip.addIcons(market.getCommodityData(curr.getCommodityId()), minus, IconRenderMode.DIM_RED);
                    }
                    icons += curr.getQuantity().getModifiedInt() + Math.max(0, minus);
                }
                int rows = (int) Math.ceil(icons / maxIconsPerRow);
                rows = 3;
                tooltip.addIconGroup(32, rows, opad);


            }
//			else if (!isFunctional() && mode == IndustryTooltipMode.NORMAL) {
//				tooltip.addPara("Currently under construction and not producing anything or providing other benefits.", opad);
//			}

            addPostSupplySection(tooltip, hasSupply, mode);

            if (hasDemand || hasPostDemandSection(hasDemand, mode)) {
                tooltip.addSectionHeading("Demand & effects", color, dark, Alignment.MID, opad);
            }
            if (hasDemand) {
                tooltip.beginIconGroup();
                tooltip.setIconSpacingMedium();
                float icons = 0;
                for (MutableCommodityQuantity curr : demand.values()) {
                    int qty = curr.getQuantity().getModifiedInt();
                    if (qty <= 0) continue;

                    CommodityOnMarketAPI com = orig.getCommodityData(curr.getCommodityId());
                    int available = com.getAvailable();

                    int normal = Math.min(available, qty);
                    int red = Math.max(0, qty - available);

                    if (mode != IndustryTooltipMode.NORMAL) {
                        normal = qty;
                        red = 0;
                    }
                    if (normal > 0) {
                        tooltip.addIcons(com, normal, IconRenderMode.NORMAL);
                    }
                    if (red > 0) {
                        tooltip.addIcons(com, red, IconRenderMode.DIM_RED);
                    }
                    icons += normal + Math.max(0, red);
                }
                int rows = (int) Math.ceil(icons / maxIconsPerRow);
                rows = 3;
                rows = 1;
                tooltip.addIconGroup(32, rows, opad);
            }

            addPostDemandSection(tooltip, hasDemand, mode);

            if (!needToAddIndustry) {
                //addAICoreSection(tooltip, AICoreDescriptionMode.TOOLTIP);
                addInstalledItemsSection(mode, tooltip, expanded);
            }

            tooltip.addPara("*Shown production and demand values are already adjusted based on current market size and local conditions.", gray, opad);
        }

        if (needToAddIndustry) {
            unapply();
            market.getIndustries().remove(this);
        }
        market = orig;
        if (!needToAddIndustry) {
            reapply();
        }
    }


}


