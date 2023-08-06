package indevo.industries.salvageyards.rules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Sounds;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.*;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.ValueDisplayMode;
import com.fs.starfarer.api.util.Highlights;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import indevo.ids.Ids;
import indevo.ids.ItemIds;
import indevo.industries.salvageyards.industry.SalvageYards;
import indevo.industries.salvageyards.intel.YardsCustomProductionIntel;
import indevo.utils.helper.Settings;
import indevo.utils.helper.StringHelper;
import org.apache.log4j.Logger;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.util.List;
import java.util.*;

public class IndEvo_InitSYCustomProductionDiag extends BaseCommandPlugin implements InteractionDialogPlugin {

    /*
     * Player can give the yards rare ship parts and money to make ships
     *
     * Produce ships at 80% cost
     * pay parts to reduce d-mods
     *
     * base: 7 D-mods
     * reduce D-mods by adding parts via slider (modify displayed dialogue)
     * Required parts to reduce depend on total hull FP that are being produced (More FP-More parts)
     *
     * Enter dialogue
     * Pick ships
     * move slider to adjust D-Mods (slider max dep. on parts in inventory)
     * confirm
     *
     * register intel
     * always 60 days to produce
     * always 80% cost
     * max volume 25k*current SY output
     * */


    private enum Option {
        MAIN,
        SACRIFICE_SHIPS,
        DOUBLE_CAP,
        SELECT_SHIPS,
        CONFIRM,
        RETURN,
    }

    protected SectorEntityToken entity;
    protected InteractionDialogPlugin originalPlugin;
    protected InteractionDialogAPI dialog;
    protected Map<String, MemoryAPI> memoryMap;

    protected static final String HAS_SP_SELECTED = "$IndEvo_HasSelectedSP_SY";
    protected static final int BASE_D_MODS = Settings.SY_BASE_DMODS;
    protected static final int PART_VALUE_MULT = Global.getSettings().getInt("IndEvo_SYPartValueMult");
    public static final int DELIVERY_TIME = Settings.SY_HULL_DELIVERY_TIME;

    protected static final String RARE_PARTS_SELECTOR_ID = "IndEvo_RareSliderID";
    protected static final String PARTS_SELECTOR_ID = "IndEvo_PartSliderID";

    protected float costMult = 1f;
    protected float cost = 0f;
    protected boolean refreshOptions = false;
    protected YardsProductionData productionData;

    private List<FleetMemberAPI> tradeInList = new ArrayList<>();

    public static final Logger log = Global.getLogger(IndEvo_InitSYCustomProductionDiag.class);
    boolean debug = false;

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        debug = Global.getSettings().isDevMode();

        if (!(dialog instanceof IndEvo_InitSYCustomProductionDiag)) {
            this.dialog = dialog;
            this.memoryMap = memoryMap;
            if (dialog == null) return false;

            entity = dialog.getInteractionTarget();
            originalPlugin = dialog.getPlugin();
            dialog.setPlugin(this);
        }

        dialog.setPromptText("Configure the Production Contract");

        init(dialog);
        return true;
    }

    private YardsProductionData getProductionData() {
        return productionData;
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        log.info("initializing SY dialog plugin");

        if (getProductionData() == null) {
            log.info("initializing production data");
            productionData = new YardsProductionData();
        }

        float costMod = 1.8f - getMarket().getFaction().getRelationship(Global.getSector().getPlayerFaction().getId());
        costMult = Math.min(1.5f, costMod);
        costMult = Math.max(0.9f, costMod);

        optionSelected(null, Option.MAIN);
    }

    private MarketAPI getMarket() {
        return Global.getSector().getEconomy().getMarket(memoryMap.get(MemKeys.MARKET).getString("$id"));
    }


    private boolean isWantsToSpendSP() {
        return memoryMap.get(MemKeys.MARKET).getBoolean(HAS_SP_SELECTED);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        OptionPanelAPI opts = dialog.getOptionPanel();

        opts.clearOptions();

        Option option = Option.valueOf(optionData.toString());

        float rarePartsInCargo = Global.getSector().getPlayerFleet().getCargo().getCommodityQuantity(ItemIds.RARE_PARTS);
        float rarePartsInMarket = Misc.getStorageCargo(getMarket()).getCommodityQuantity(ItemIds.RARE_PARTS);
        float rarePartsAvailable = rarePartsInCargo + rarePartsInMarket;

        float partsInCargo = Global.getSector().getPlayerFleet().getCargo().getCommodityQuantity(ItemIds.PARTS);
        float partsInMarket = Misc.getStorageCargo(getMarket()).getCommodityQuantity(ItemIds.PARTS);
        float partsAvailable = partsInCargo + partsInMarket;
        float partTradeInPrice = Math.round(Global.getSettings().getCommoditySpec(ItemIds.PARTS).getBasePrice() * PART_VALUE_MULT);

        log.info("available parts cargo " + rarePartsInCargo + " market " + rarePartsInMarket);
        log.info("selected ships " + getProductionData().productionList.size());

        String rarepartsName = Global.getSettings().getCommoditySpec(ItemIds.RARE_PARTS).getName();
        String partsName = Global.getSettings().getCommoditySpec(ItemIds.PARTS).getName();

        switch (option) {
            case MAIN:
                opts.addOption("Select Hulls to build", Option.SELECT_SHIPS);

                opts.addSelector(rarepartsName + " (Reduces D-Mods)", RARE_PARTS_SELECTOR_ID, Misc.getHighlightColor(),
                        300f,
                        50f,
                        0f,
                        Math.min(getProductionData().getRequiredParts(), rarePartsAvailable),
                        ValueDisplayMode.VALUE,
                        rarePartsAvailable >= 1f ? "Use " + rarepartsName + " to reduce the D-Mods on the produced hulls. You have " + (int) Math.round(rarePartsAvailable) + " available." : "You do not have any " + rarepartsName);

                opts.addSelector(partsName + " (Reduces cost)", PARTS_SELECTOR_ID, Misc.getHighlightColor(),
                        300f,
                        50f,
                        0f,
                        Math.min(getProductionData().getMaxPartsAmt(), partsAvailable),
                        ValueDisplayMode.VALUE,
                        partsAvailable >= 1f ? "Use " + partsName + " to reduce the total cost by up to 50%. The yards will give you a better rate for them than you might get otherwise. Parts get traded in at " + Misc.getDGSCredits(partTradeInPrice) + ". You have " + (int) Math.round(partsAvailable) + " available." : "You do not have any " + partsName);

                opts.addOption("Scrap ships for additional Budget", Option.SACRIFICE_SHIPS, "You can consign ship hulls to be disassembled and used for your production order. Fitted weapons will also be disassembled and count for the budget.");

                opts.addOption("Double the production Budget", Option.DOUBLE_CAP);

                SetStoryOption.StoryOptionParams params = new SetStoryOption.StoryOptionParams(Option.DOUBLE_CAP,
                        1, "IndEvo_SYDoubleCapBonusXP", Sounds.STORY_POINT_SPEND,
                        "Temporarily increased the production capacity of the Salvage Yards on " + getMarket().getName());

                SetStoryOption.set(dialog, params,
                        new SetStoryOption.BaseOptionStoryPointActionDelegate(dialog, params) {
                            @Override
                            public void confirm() {
                                memoryMap.get(MemKeys.MARKET).set(HAS_SP_SELECTED, true);
                                optionSelected(null, Option.MAIN);
                            }

                            @Override
                            public void createDescription(TooltipMakerAPI info) {
                                super.createDescription(info);
                                info.setParaFontDefault();
                                info.addPara("Will double the production capacity of the Salvage Yards for a single order. " +
                                                "Should you select this option, but not confirm the production contract at this moment, the bonus will be retained for the future.",
                                        0f,
                                        Misc.getHighlightColor(),
                                        "bonus will be retained"
                                );
                            }
                        });

                opts.setEnabled(Option.DOUBLE_CAP, !isWantsToSpendSP());

                opts.addOption("Confirm", Option.CONFIRM);

                boolean allowed = !getProductionData().productionList.isEmpty() && Global.getSector().getPlayerFleet().getCargo().getCredits().get() >= getProductionData().getCost();
                dialog.getOptionPanel().setEnabled(Option.CONFIRM, allowed);

                if (!allowed)
                    dialog.getOptionPanel().setTooltip(Option.CONFIRM, "Nothing is selected or you do not have enough credits.");
                else dialog.getOptionPanel().setTooltip(Option.CONFIRM, null);

                opts.addOption("Return", Option.RETURN);
                opts.setShortcut(Option.RETURN, Keyboard.KEY_ESCAPE, false, false, false, true);

                updatePanel();
                break;
            case DOUBLE_CAP:
                optionSelected(null, Option.MAIN);
                break;
            case SACRIFICE_SHIPS:
                initHullPicker();
                break;
            case SELECT_SHIPS:
                initShipPicker();
                break;
            case CONFIRM:
                finalizeSelection();
                break;
            case RETURN:
                returnToMenu();
                break;
            default:
                throw new IllegalArgumentException("Unexpected argument: " + option);
        }
    }

    private List<FleetMemberAPI> getValidFleetMemberList() {
        List<FleetMemberAPI> combinedFleet = new ArrayList<>(Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy());
        CargoAPI storageCargo = Misc.getStorageCargo(getMarket());

        if (storageCargo != null) {
            storageCargo.initMothballedShips("player");
            combinedFleet.addAll(storageCargo.getMothballedShips().getMembersListCopy());
        }

        return combinedFleet;
    }

    private void initHullPicker() {
        tradeInList.clear();
        final List<FleetMemberAPI> validSelectionList = getValidFleetMemberList();

        int shipsPerRow = Settings.SHIP_PICKER_ROW_COUNT;
        int rows = validSelectionList.size() > shipsPerRow ? (int) Math.ceil(validSelectionList.size() / (float) shipsPerRow) : 1;
        int cols = Math.min(validSelectionList.size(), shipsPerRow);

        cols = Math.max(cols, 4);

        dialog.showFleetMemberPickerDialog("Select hulls to scrap", "Confirm", "Cancel", rows,
                cols, 88f, true, true, validSelectionList, new FleetMemberPickerListener() {

                    @Override
                    public void pickedFleetMembers(List<FleetMemberAPI> members) {
                        if (members != null && !members.isEmpty()) {
                            tradeInList = members;

                            updatePanel();
                        }
                    }

                    @Override
                    public void cancelledFleetMemberPicking() {
                    }
                });


        optionSelected(null, Option.MAIN);
    }

    private void updatePanel() {
        TextPanelAPI panel = dialog.getTextPanel();
        panel.clear();

        addTooltip(panel);
    }

    private void initShipPicker() {
        dialog.showCustomProductionPicker(new BaseCustomProductionPickerDelegateImpl() {
            @Override
            public Set<String> getAvailableFighters() {
                return new HashSet<>();
            }

            @Override
            public Set<String> getAvailableWeapons() {
                return new HashSet<>();
            }

            @Override
            public Set<String> getAvailableShipHulls() {
                return Global.getSector().getPlayerFaction().getKnownShips();
            }

            @Override
            public float getCostMult() {
                return costMult;
            }

            @Override
            public float getMaximumValue() {
                // TODO: 24/04/2021 get a "used production value" int and subtract it from this to avoid double booking
                return getCapacity();
            }

            @Override
            public void notifyProductionSelected(FactionProductionAPI production) {
                log.info("finished picking, returning entries");

                YardsProductionData productionData = getProductionData();
                productionData.baseCost = production.getTotalCurrentCost();
                productionData.productionList.clear();

                for (FactionProductionAPI.ItemInProductionAPI item : production.getCurrent()) {
                    int count = item.getQuantity();

                    if (item.getType() == FactionProductionAPI.ProductionItemType.SHIP) {
                        productionData.productionList.put(item.getSpecId(), count);
                    }
                }

                refreshOptions = true;
            }
        });

        optionSelected(null, Option.MAIN);
    }

    private float getAdditionalCapByShips() {
        if (tradeInList.isEmpty()) return 0f;

        float total = 0f;
        for (FleetMemberAPI m : tradeInList) {
            total += m.getBaseSellValue();
        }

        return total;
    }

    private float getCapacity() {
        float storyPointBonus = isWantsToSpendSP() ? 2f : 1f;
        return 3
                + getAdditionalCapByShips()
                + (((SalvageYards) getMarket().getIndustry(Ids.SCRAPYARD)).getHullCapacity() * Global.getSettings().getInt("productionCapacityPerSWUnit"))
                * storyPointBonus;
    }

    private void addTooltip(TextPanelAPI panel) {
        float pad = 5f;
        YardsProductionData productionData = getProductionData();
        String rarepartsName = Global.getSettings().getCommoditySpec(ItemIds.RARE_PARTS).getName();
        String partsName = Global.getSettings().getCommoditySpec(ItemIds.PARTS).getName();

        panel.addPara("\"Spend enough time taking 'em apart, you get to know how to make one. It's probably even going to be spaceworthy! Bring us the parts we can't just scrounge up, pay us, and we'll build you a beauty!\" - The foreman lowers his voice, \"Without serial numbers, of course.\"");
        panel.addPara("The Salvage Yards can construct ships for you if you provide the blueprints and components that are too rare to \"acquire\".\n\n");

        panel.addPara("Contract information:");
        panel.setFontSmallInsignia();

        panel.addParagraph("-----------------------------------------------------------------------------");
        panel.addPara("Maximum capacity: " + Misc.getDGSCredits(getCapacity()));
        panel.highlightInLastPara(Misc.getHighlightColor(), Misc.getDGSCredits(getCapacity()));

        if (!tradeInList.isEmpty()) {
            float add = getAdditionalCapByShips();

            panel.addPara(BaseIntelPlugin.BULLET + "Additional " + Misc.getDGSCredits(add) + " by trading in " + tradeInList.size() + " hulls");
            panel.highlightInLastPara(Misc.getHighlightColor(), Misc.getDGSCredits(add));
        }

        if (isWantsToSpendSP()) {
            panel.addPara(BaseIntelPlugin.BULLET + "Doubled by Story Point");
            panel.highlightInLastPara(Misc.getPositiveHighlightColor(), "Story Point");
        }

        String costMultStr = StringHelper.getAbsPercentString(costMult, false);
        Pair<String, Color> repInt = StringHelper.getRepIntTooltipPair(getMarket().getFaction());

        panel.addPara("Produced at cost: " + costMultStr + " " + repInt.one);

        Highlights h = new Highlights();
        h.setText(costMultStr, repInt.one);
        h.setColors(Misc.getHighlightColor(), repInt.two);
        panel.setHighlightsInLastPara(h);

        panel.addPara("Delivery time: " + DELIVERY_TIME + " " + StringHelper.getDayOrDays(DELIVERY_TIME));
        panel.highlightInLastPara(Misc.getHighlightColor(), DELIVERY_TIME + " " + StringHelper.getDayOrDays(DELIVERY_TIME));

        panel.addPara("Selected ships:");

        TooltipMakerAPI tooltip = panel.beginTooltip();
        if (!productionData.productionList.isEmpty()) {

            tooltip.beginGridFlipped(300, 1, 30f, 3f);

            int i = 0;
            for (Map.Entry<String, Integer> e : productionData.productionList.entrySet()) {
                ShipHullSpecAPI hs = Global.getSettings().getHullSpec(e.getKey());
                tooltip.addToGrid(0, i, hs.getHullNameWithDashClass() + " ship hull", e.getValue() + "");
                i++;

                if (i > 9) {
                    break;
                }
            }
            tooltip.addGrid(pad);
            if (i > 9 && productionData.productionList.size() > 10) {
                tooltip.addPara("... and " + (productionData.productionList.size() - 9) + " additional hulls.", 3f);
            }
        } else {
            panel.addPara("    Select any ships you wish to order.");
        }

        panel.addTooltip();
        panel.addPara("Reduce the amount of D-Mods by spending " + rarepartsName + ".", Misc.getGrayColor());
        panel.addPara("Reduce the cost by up to 50% by trading in " + partsName + ".", Misc.getGrayColor());

        panel.addParagraph("-----------------------------------------------------------------------------");
        panel.setFontInsignia();

        if (productionData.productionList.isEmpty())
            panel.addPara("No ships selected yet, D-Mod or cost forecast not possible.");
        else {
            String appendDModCount = "Average D-Mods per hull: " + productionData.getAverageDModAmount() + "\n" +
                    "Current total cost: " + Misc.getDGSCredits(productionData.getCost());
            panel.addPara(appendDModCount);
            panel.highlightInLastPara(Misc.getHighlightColor(), Integer.toString(productionData.getAverageDModAmount()), Misc.getDGSCredits(productionData.getCost()));
        }
    }

    private void returnToMenu() {
        dialog.setPlugin(originalPlugin);
        new ShowDefaultVisual().execute(null, dialog, Misc.tokenize(""), memoryMap);
        FireAll.fire(null, dialog, memoryMap, "IndEvo_YardsBaseMenu");
    }

    public void finalizeSelection() {
        removeCostFromInventory();

        YardsCustomProductionIntel intel = new YardsCustomProductionIntel(getMarket(), getProductionData());
        Global.getSector().getIntelManager().addIntel(intel);
        intel.init();

        returnToMenu();
    }

    private void removeCostFromInventory() {
        YardsProductionData data = getProductionData();

        int cost = Math.round(data.getCost());
        int rarePartsToRemove = data.rarePartsSpent;
        int partsToRemove = data.partsSpent;

        AddRemoveCommodity.addCreditsLossText(cost, dialog.getTextPanel());
        if (rarePartsToRemove > 0)
            AddRemoveCommodity.addCommodityLossText(ItemIds.RARE_PARTS, rarePartsToRemove, dialog.getTextPanel());
        if (partsToRemove > 0)
            AddRemoveCommodity.addCommodityLossText(ItemIds.PARTS, partsToRemove, dialog.getTextPanel());

        CargoAPI fleetCargo = Global.getSector().getPlayerFleet().getCargo();
        CargoAPI storageCargo = Misc.getStorageCargo(getMarket());
        fleetCargo.getCredits().subtract(cost);

        float rarePartsInCargo = fleetCargo.getCommodityQuantity(ItemIds.RARE_PARTS);

        if (rarePartsInCargo >= rarePartsToRemove) {
            fleetCargo.removeCommodity(ItemIds.RARE_PARTS, rarePartsToRemove);
        } else {
            fleetCargo.removeCommodity(ItemIds.RARE_PARTS, rarePartsInCargo);
            rarePartsToRemove -= rarePartsInCargo;

            storageCargo.removeCommodity(ItemIds.RARE_PARTS, rarePartsToRemove);
        }

        float partsInCargo = fleetCargo.getCommodityQuantity(ItemIds.PARTS);
        if (partsInCargo >= partsToRemove) {
            fleetCargo.removeCommodity(ItemIds.PARTS, partsToRemove);
        } else {
            fleetCargo.removeCommodity(ItemIds.RARE_PARTS, partsInCargo);
            partsToRemove -= partsInCargo;

            storageCargo.removeCommodity(ItemIds.RARE_PARTS, partsToRemove);
        }

        TextPanelAPI panel = dialog.getTextPanel();

        if (!tradeInList.isEmpty()) {
            storageCargo.initMothballedShips("player");

            FleetDataAPI storageFleet = storageCargo.getMothballedShips();
            FleetDataAPI playerFleet = Global.getSector().getPlayerFleet().getFleetData();

            for (FleetMemberAPI member : tradeInList) {
                storageFleet.removeFleetMember(member);
                playerFleet.removeFleetMember(member);
            }

            String s = "Scrapped " + tradeInList.size() + " ships.";

            panel.setFontSmallInsignia();
            panel.addPara(s);
            panel.highlightInLastPara(Misc.getNegativeHighlightColor(), s);
            panel.setFontInsignia();
        }

        if (isWantsToSpendSP()) {
            String s = "Used a Story Point";

            panel.setFontSmallInsignia();
            panel.addPara(s);
            panel.highlightInLastPara(Misc.getPositiveHighlightColor(), s);
            panel.setFontInsignia();

            getMemoryMap().get(MemKeys.MARKET).unset(HAS_SP_SELECTED);
        }
    }

    @Override
    public void optionMousedOver(String optionText, Object optionData) {
        /*if (optionData != null) {
            updatePanel();
        }*/
    }

    private float updateCounter = 0;

    @Override
    public void advance(float amount) {
        YardsProductionData productionData = getProductionData();
        if (refreshOptions) {
            optionSelected(null, Option.MAIN);
            refreshOptions = false;
        }

        if (productionData.productionList.isEmpty()) return;

        updateCounter += amount;
        if (updateCounter > 1) {
            updateCounter = 0;

            productionData.rarePartsSpent = (int) Math.round(dialog.getOptionPanel().getSelectorValue(RARE_PARTS_SELECTOR_ID));
            productionData.partsSpent = (int) Math.round(dialog.getOptionPanel().getSelectorValue(PARTS_SELECTOR_ID));

            boolean allowed = Global.getSector().getPlayerFleet().getCargo().getCredits().get() >= getProductionData().getCost();
            dialog.getOptionPanel().setEnabled(Option.CONFIRM, allowed);

            if (!allowed) dialog.getOptionPanel().setTooltip(Option.CONFIRM, "You do not have enough credits");
            else dialog.getOptionPanel().setTooltip(Option.CONFIRM, null);


            String appendDModCount = "Average D-Mods per hull: " + productionData.getAverageDModAmount() + "\n" +
                    "Current total cost: " + Misc.getDGSCredits(productionData.getCost());
            dialog.getTextPanel().replaceLastParagraph(appendDModCount);
            dialog.getTextPanel().highlightInLastPara(Misc.getHighlightColor(), Integer.toString(productionData.getAverageDModAmount()), Misc.getDGSCredits(productionData.getCost()));
        }
    }

    @Override
    public void backFromEngagement(EngagementResultAPI battleResult) {

    }

    @Override
    public Object getContext() {
        return null;
    }

    @Override
    public Map<String, MemoryAPI> getMemoryMap() {
        return memoryMap;
    }


    public static class YardsProductionData {
        public Map<String, Integer> productionList = new HashMap<>();
        public int rarePartsSpent = 0;
        public int partsSpent = 0;
        public float baseCost = 0;

        public float getCost() {
            float partCost = Math.round(Global.getSettings().getCommoditySpec(ItemIds.PARTS).getBasePrice() * PART_VALUE_MULT);
            return baseCost - (partsSpent * partCost);
        }

        public int getRequiredParts() {
            return getFP() * Settings.RARE_PARTS_AMOUNT_PER_FP;
        }

        public int getMaxPartsAmt() {
            float partCost = Math.round(Global.getSettings().getCommoditySpec(ItemIds.PARTS).getBasePrice() * PART_VALUE_MULT);
            return (int) Math.round((baseCost / 2) / partCost);
        }

        private int getFP() {
            int totalFP = 0;

            for (Map.Entry<String, Integer> e : productionList.entrySet()) {
                totalFP += Global.getSettings().getHullSpec(e.getKey()).getFleetPoints() * e.getValue();
            }

            return totalFP;
        }

        public int getAverageDModAmount() {
            int requiredParts = getRequiredParts();
            float percentageFilled = rarePartsSpent / (requiredParts * 1f);

            return (int) Math.ceil(BASE_D_MODS - (BASE_D_MODS * percentageFilled));
        }
    }
}