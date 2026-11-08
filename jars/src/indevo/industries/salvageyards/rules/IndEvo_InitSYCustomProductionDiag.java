package indevo.industries.salvageyards.rules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
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
import indevo.config.Setting;
import indevo.economy.plugins.RelicSpeedUpDialog;
import indevo.ids.Ids;
import indevo.ids.ItemIds;
import indevo.industries.salvageyards.industry.SalvageYards;
import indevo.industries.salvageyards.intel.YardsCustomProductionIntel;
import indevo.industries.salvageyards.memory.WreckRegistry;
import indevo.utils.helper.MiscIE;
import indevo.utils.helper.RelicHelper;
import indevo.utils.helper.StringHelper;
import indevo.utils.helper.BillingHelper;
import org.apache.log4j.Logger;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.util.List;
import java.util.*;

import static indevo.items.installable.SpecialItemEffectsRepo.SALVAGE_DRONES_SP_PER_FP;

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
        AS_FITTED,
        CONFIRM,
        RETURN,
    }

    protected SectorEntityToken entity;
    protected InteractionDialogPlugin originalPlugin;
    protected InteractionDialogAPI dialog;
    protected Map<String, MemoryAPI> memoryMap;

    protected static final String HAS_SP_SELECTED = "$IndEvo_HasSelectedSP_SY";

    protected static final String RARE_PARTS_SELECTOR_ID = "IndEvo_RareSliderID";
    protected static final String PARTS_SELECTOR_ID = "IndEvo_PartSliderID";
    protected static final String ACCELERATE_PREFIX = "IndEvo_SYAccelerate_";

    protected float costMult = 1f;
    protected float cost = 0f;
    protected boolean refreshOptions = false;
    protected YardsProductionData productionData;
    protected boolean asFitted = false;
    protected Map<String, Integer> selectedWeapons = new LinkedHashMap<>();
    protected Map<String, Integer> selectedWings = new LinkedHashMap<>();
    protected List<String> cappedItems = new ArrayList<>();

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
        costMult = Math.max(0.9f, Math.min(1.5f, costMod));

        optionSelected(null, Option.MAIN);
    }

    private MarketAPI getMarket() {
        return Global.getSector().getEconomy().getMarket(memoryMap.get(MemKeys.MARKET).getString("$id"));
    }


    private SalvageYards getYards() {
        return (SalvageYards) getMarket().getIndustry(Ids.SCRAPYARD);
    }

    private WreckRegistry getRegistry() {
        SalvageYards yards = getYards();
        return yards != null && yards.hasSalvageDrones() ? yards.getWreckRegistry() : null;
    }

    private boolean isWantsToSpendSP() {
        return memoryMap.get(MemKeys.MARKET).getBoolean(HAS_SP_SELECTED);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        OptionPanelAPI opts = dialog.getOptionPanel();

        opts.clearOptions();

        if (optionData.toString().startsWith(ACCELERATE_PREFIX)) {
            int index = Integer.parseInt(optionData.toString().substring(ACCELERATE_PREFIX.length()));
            YardsCustomProductionIntel order = getRunningOrders().get(index);

            optionSelected(null, Option.MAIN);
            new RelicSpeedUpDialog(order, () -> refreshOptions = true).show(dialog);
            return;
        }

        Option option = Option.valueOf(optionData.toString());

        float rarePartsInCargo = Global.getSector().getPlayerFleet().getCargo().getCommodityQuantity(ItemIds.RARE_PARTS);
        float rarePartsInMarket = MiscIE.getStorageCargo(getMarket()).getCommodityQuantity(ItemIds.RARE_PARTS);
        float rarePartsAvailable = rarePartsInCargo + rarePartsInMarket;

        float partsInCargo = Global.getSector().getPlayerFleet().getCargo().getCommodityQuantity(ItemIds.PARTS);
        float partsInMarket = MiscIE.getStorageCargo(getMarket()).getCommodityQuantity(ItemIds.PARTS);
        float partsAvailable = partsInCargo + partsInMarket;
        float partTradeInPrice = Math.round(Global.getSettings().getCommoditySpec(ItemIds.PARTS).getBasePrice() * Setting.SY_PART_VALUE_MULT.value());

        log.info("available parts cargo " + rarePartsInCargo + " market " + rarePartsInMarket);
        log.info("selected ships " + getProductionData().productionList.size());

        String rarepartsName = Global.getSettings().getCommoditySpec(ItemIds.RARE_PARTS).getName();
        String partsName = Global.getSettings().getCommoditySpec(ItemIds.PARTS).getName();

        switch (option) {
            case MAIN:
                opts.addOption("Select Hulls to build", Option.SELECT_SHIPS);

                if (getRegistry() != null) {
                    opts.addOption(asFitted ? "Registry hulls: As fitted" : "Registry hulls: Bare", Option.AS_FITTED);
                    opts.setTooltip(Option.AS_FITTED, "As fitted, hulls from the Wreck Registry come with the weapons, fighter wings and hullmods they had when they were lost, for the value of the weapons and wings. "
                            + "Hulls you have the blueprint for are then also built from the registry.");
                }

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

                addAccelerateOptions(opts);

                opts.addOption("Confirm", Option.CONFIRM);
                updateConfirmOption();

                opts.addOption("Return", Option.RETURN);
                opts.setShortcut(Option.RETURN, Keyboard.KEY_ESCAPE, false, false, false, true);

                updatePanel();
                break;
            case DOUBLE_CAP:
                optionSelected(null, Option.MAIN);
                break;
            case AS_FITTED:
                asFitted = !asFitted;
                updateRegistryOrder();
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

    private void updateConfirmOption() {
        YardsProductionData data = getProductionData();
        OptionPanelAPI opts = dialog.getOptionPanel();
        String reason = null;

        if (!data.hasOrder()) reason = "Nothing is selected.";
        else if (!BillingHelper.canAfford(data.getCost())) reason = "You do not have enough credits.";
        else if (getYards().getAvailableSalvagePoints() < data.getSalvageUnitCost()) reason = "The yards do not have enough salvage units.";

        opts.setEnabled(Option.CONFIRM, reason == null);
        opts.setTooltip(Option.CONFIRM, reason);
    }

    private void updateRegistryOrder() {
        YardsProductionData data = getProductionData();
        WreckRegistry registry = getRegistry();

        if (registry == null) {
            data.registryOrder = null;
            return;
        }

        WreckRegistry.Order order = new WreckRegistry.Order();
        FactionAPI player = Global.getSector().getPlayerFaction();

        for (Map.Entry<String, Integer> e : data.productionList.entrySet()) {
            String hullId = e.getKey();
            int used = player.knowsShip(hullId) && !asFitted ? 0 : Math.min(e.getValue(), registry.getHullCount(hullId));
            if (used <= 0) continue;

            order.hulls.put(hullId, used);
            order.salvageUnitCost += used * Global.getSettings().getHullSpec(hullId).getFleetPoints() * SALVAGE_DRONES_SP_PER_FP;

            if (!asFitted) continue;

            for (WreckRegistry.WreckFit fit : registry.getOldestFits(hullId, used)) {
                order.fits.add(fit);
                order.fitCost += fit.getValue() * costMult;
            }
        }

        order.weapons.putAll(selectedWeapons);
        order.wings.putAll(selectedWings);

        data.registryOrder = order.isEmpty() ? null : order;
    }

    private int getMaxQuantity(FactionProductionAPI.ItemInProductionAPI item, WreckRegistry registry) {
        String id = item.getSpecId();

        switch (item.getType()) {
            case SHIP:
                if (Global.getSector().getPlayerFaction().knowsShip(id)) return Integer.MAX_VALUE;
                return registry == null ? 0 : registry.getHullCount(id);
            case WEAPON:
                return registry == null ? 0 : registry.getWeaponCount(id);
            default:
                return registry == null ? 0 : registry.getWingCount(id);
        }
    }

    private String getItemName(FactionProductionAPI.ItemInProductionAPI item) {
        switch (item.getType()) {
            case SHIP:
                return item.getShipSpec().getHullName();
            case WEAPON:
                return item.getWeaponSpec().getWeaponName();
            default:
                return item.getWingSpec().getWingName();
        }
    }

    private List<YardsCustomProductionIntel> getRunningOrders() {
        List<YardsCustomProductionIntel> orders = new ArrayList<>();

        for (IntelInfoPlugin plugin : Global.getSector().getIntelManager().getIntel(YardsCustomProductionIntel.class)) {
            YardsCustomProductionIntel order = (YardsCustomProductionIntel) plugin;
            if (order.getMarket() == getMarket() && order.getSpeedUpDaysLeft() > 0) orders.add(order);
        }

        return orders;
    }

    private void addAccelerateOptions(OptionPanelAPI opts) {
        List<YardsCustomProductionIntel> orders = getRunningOrders();

        if (orders.isEmpty()) {
            String id = ACCELERATE_PREFIX + "none";
            opts.addOption(YardsCustomProductionIntel.SPEED_UP_LABEL, id);
            opts.setEnabled(id, false);
            opts.setTooltip(id, "Halves the remaining time of an order here, for relic components.");
            opts.addOptionTooltipAppender(id, (tooltip, hadOtherText) -> tooltip.addPara("No order is in progress here.", Misc.getNegativeHighlightColor(), hadOtherText ? 10f : 0f));
            return;
        }

        for (int i = 0; i < orders.size(); i++) {
            final YardsCustomProductionIntel order = orders.get(i);
            String id = ACCELERATE_PREFIX + i;
            int days = order.getSpeedUpDaysLeft();

            opts.addOption(order.getSpeedUpLabel() + " (" + days + " " + StringHelper.getDayOrDays(days) + " left)", id);
            opts.setEnabled(id, RelicSpeedUpDialog.isAvailable(order) && RelicHelper.canAfford(getMarket(), order.getSpeedUpCost()));
            opts.addOptionTooltipAppender(id, (tooltip, hadOtherText) -> RelicSpeedUpDialog.addTooltip(tooltip, order));
        }
    }

    private List<FleetMemberAPI> getValidFleetMemberList() {
        List<FleetMemberAPI> combinedFleet = new ArrayList<>(Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy());
        CargoAPI storageCargo = MiscIE.getStorageCargo(getMarket());

        if (storageCargo != null) {
            storageCargo.initMothballedShips("player");
            combinedFleet.addAll(storageCargo.getMothballedShips().getMembersListCopy());
        }

        return combinedFleet;
    }

    private void initHullPicker() {
        tradeInList.clear();
        final List<FleetMemberAPI> validSelectionList = getValidFleetMemberList();

        int shipsPerRow = Setting.SHIP_PICKER_ROW_COUNT.intValue();
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
                WreckRegistry registry = getRegistry();
                return registry == null ? new HashSet<>() : registry.getWingIds();
            }

            @Override
            public Set<String> getAvailableWeapons() {
                WreckRegistry registry = getRegistry();
                return registry == null ? new HashSet<>() : registry.getWeaponIds();
            }

            @Override
            public Set<String> getAvailableShipHulls() {
                Set<String> hulls = new HashSet<>(Global.getSector().getPlayerFaction().getKnownShips());
                WreckRegistry registry = getRegistry();
                if (registry != null) hulls.addAll(registry.getHullIds());
                return hulls;
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
                WreckRegistry registry = getRegistry();

                cappedItems.clear();
                for (FactionProductionAPI.ItemInProductionAPI item : new ArrayList<>(production.getCurrent())) {
                    int max = getMaxQuantity(item, registry);
                    if (item.getQuantity() <= max) continue;

                    cappedItems.add(getItemName(item));
                    production.removeItem(item.getType(), item.getSpecId(), item.getQuantity() - max);
                }

                productionData.baseCost = production.getTotalCurrentCost();
                productionData.productionList.clear();
                selectedWeapons.clear();
                selectedWings.clear();

                for (FactionProductionAPI.ItemInProductionAPI item : production.getCurrent()) {
                    int count = item.getQuantity();
                    if (count <= 0) continue;

                    switch (item.getType()) {
                        case SHIP:
                            productionData.productionList.put(item.getSpecId(), count);
                            break;
                        case WEAPON:
                            selectedWeapons.put(item.getSpecId(), count);
                            break;
                        case FIGHTER:
                            selectedWings.put(item.getSpecId(), count);
                            break;
                    }
                }

                updateRegistryOrder();
                refreshOptions = true;
            }
        });

        optionSelected(null, Option.MAIN);
    }

    private float getAdditionalCapByShips() {
        if (tradeInList.isEmpty()) return 0f;

        float total = 0f;
        for (FleetMemberAPI m : tradeInList) {
            total += m.getBaseValue() * 0.3f * (Math.max(0f, 1f - (IndEvo_InitSYMoveDModDiag.getDModList(m).size() / 10f)));
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

        WreckRegistry registry = getRegistry();
        if (registry != null) {
            panel.addPara("The salvage drones keep a Wreck Registry of hulls, weapons and fighter wings lost nearby. Each entry can be built once without a blueprint.", Misc.getGrayColor());
        }

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

        panel.addPara("Delivery time: " + Setting.SY_HULL_DELIVERY_TIME.intValue() + " " + StringHelper.getDayOrDays(Setting.SY_HULL_DELIVERY_TIME.intValue()));
        panel.highlightInLastPara(Misc.getHighlightColor(), Setting.SY_HULL_DELIVERY_TIME.intValue() + " " + StringHelper.getDayOrDays(Setting.SY_HULL_DELIVERY_TIME.intValue()));

        WreckRegistry.Order order = productionData.registryOrder;
        if (order != null && order.salvageUnitCost > 0) {
            String su = Misc.getWithDGS(order.salvageUnitCost);
            String available = Misc.getWithDGS(getYards().getAvailableSalvagePoints());
            panel.addPara("Salvage units for registry hulls: " + su + " of " + available);
            panel.highlightInLastPara(Misc.getHighlightColor(), su, available);
        }

        if (order != null && order.fitCost > 0) {
            String fits = Misc.getDGSCredits(order.fitCost);
            panel.addPara("Fitted weapons and wings: " + fits);
            panel.highlightInLastPara(Misc.getHighlightColor(), fits);
        }

        if (!cappedItems.isEmpty()) {
            panel.addPara("Limited to the Wreck Registry: " + String.join(", ", cappedItems), Misc.getNegativeHighlightColor());
        }

        panel.addPara(registry != null ? "Selected items:" : "Selected ships:");

        List<Pair<String, Integer>> rows = new ArrayList<>();
        for (Map.Entry<String, Integer> e : productionData.productionList.entrySet()) {
            ShipHullSpecAPI hs = Global.getSettings().getHullSpec(e.getKey());
            boolean fromRegistry = order != null && order.hulls.containsKey(e.getKey());
            rows.add(new Pair<>(hs.getHullNameWithDashClass() + " ship hull" + (fromRegistry ? " (registry)" : ""), e.getValue()));
        }

        for (Map.Entry<String, Integer> e : selectedWeapons.entrySet()) {
            rows.add(new Pair<>(Global.getSettings().getWeaponSpec(e.getKey()).getWeaponName(), e.getValue()));
        }

        for (Map.Entry<String, Integer> e : selectedWings.entrySet()) {
            rows.add(new Pair<>(Global.getSettings().getFighterWingSpec(e.getKey()).getWingName() + " wing", e.getValue()));
        }

        TooltipMakerAPI tooltip = panel.beginTooltip();
        if (!rows.isEmpty()) {

            tooltip.beginGridFlipped(300, 1, 30f, 3f);

            int i = 0;
            for (Pair<String, Integer> row : rows) {
                tooltip.addToGrid(0, i, row.one, row.two + "");
                i++;

                if (i > 9) {
                    break;
                }
            }
            tooltip.addGrid(pad);
            if (i > 9 && rows.size() > 10) {
                tooltip.addPara("... and " + (rows.size() - 9) + " more.", 3f);
            }
        } else {
            panel.addPara(registry != null ? "    Select anything you wish to order." : "    Select any ships you wish to order.");
        }

        panel.addTooltip();
        panel.addPara("Reduce the amount of D-Mods by spending " + rarepartsName + ".", Misc.getGrayColor());
        panel.addPara("Reduce the cost by up to 50% by trading in " + partsName + ".", Misc.getGrayColor());

        panel.addParagraph("-----------------------------------------------------------------------------");
        panel.setFontInsignia();

        if (!productionData.hasOrder())
            panel.addPara("No ships selected yet, D-Mod or cost forecast not possible.");
        else {
            panel.addPara(getForecastText());
            panel.highlightInLastPara(Misc.getHighlightColor(), getForecastHighlights());
        }
    }

    private String getForecastText() {
        YardsProductionData data = getProductionData();
        String cost = "Current total cost: " + Misc.getDGSCredits(data.getCost());
        if (data.productionList.isEmpty()) return cost;
        return "Average D-Mods per hull: " + data.getAverageDModAmount() + "\n" + cost;
    }

    private String[] getForecastHighlights() {
        YardsProductionData data = getProductionData();
        String cost = Misc.getDGSCredits(data.getCost());
        if (data.productionList.isEmpty()) return new String[]{cost};
        return new String[]{Integer.toString(data.getAverageDModAmount()), cost};
    }

    private void returnToMenu() {
        dialog.setPlugin(originalPlugin);
        dialog.setPromptText("You decide to...");
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
        CargoAPI storageCargo = MiscIE.getStorageCargo(getMarket());
        BillingHelper.chargeNow(cost);

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
            fleetCargo.removeCommodity(ItemIds.PARTS, partsInCargo);
            partsToRemove -= partsInCargo;

            storageCargo.removeCommodity(ItemIds.PARTS, partsToRemove);
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

        if (data.registryOrder != null) {
            getYards().getWreckRegistry().take(data.registryOrder);

            int su = data.registryOrder.salvageUnitCost;
            if (su > 0) {
                getYards().spendSalvagePoints(su);
                String s = "Used " + Misc.getWithDGS(su) + " salvage units";

                panel.setFontSmallInsignia();
                panel.addPara(s);
                panel.highlightInLastPara(Misc.getNegativeHighlightColor(), s);
                panel.setFontInsignia();
            }
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

        if (!productionData.hasOrder()) return;

        updateCounter += amount;
        if (updateCounter > 1) {
            updateCounter = 0;

            productionData.rarePartsSpent = (int) Math.round(dialog.getOptionPanel().getSelectorValue(RARE_PARTS_SELECTOR_ID));
            productionData.partsSpent = (int) Math.round(dialog.getOptionPanel().getSelectorValue(PARTS_SELECTOR_ID));

            updateConfirmOption();

            dialog.getTextPanel().replaceLastParagraph(getForecastText());
            dialog.getTextPanel().highlightInLastPara(Misc.getHighlightColor(), getForecastHighlights());
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
        public WreckRegistry.Order registryOrder = null;

        public float getCost() {
            float partCost = Math.round(Global.getSettings().getCommoditySpec(ItemIds.PARTS).getBasePrice() * Setting.SY_PART_VALUE_MULT.value());
            float fitCost = registryOrder == null ? 0f : registryOrder.fitCost;
            return baseCost + fitCost - (partsSpent * partCost);
        }

        public int getSalvageUnitCost() {
            return registryOrder == null ? 0 : registryOrder.salvageUnitCost;
        }

        public boolean hasOrder() {
            return !productionList.isEmpty() || registryOrder != null;
        }

        public int getRequiredParts() {
            return getFP() * Setting.RARE_PARTS_AMOUNT_PER_FP.intValue();
        }

        public int getMaxPartsAmt() {
            float partCost = Math.round(Global.getSettings().getCommoditySpec(ItemIds.PARTS).getBasePrice() * Setting.SY_PART_VALUE_MULT.value());
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

            return (int) Math.ceil(Setting.SY_BASE_DMODS.intValue() - (Setting.SY_BASE_DMODS.intValue() * percentageFilled));
        }
    }
}