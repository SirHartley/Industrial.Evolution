package com.fs.starfarer.api.impl.campaign.rulecmd.academyRules.temp;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.IndEvo_StringHelper;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.IndEvo_Academy;
import com.fs.starfarer.api.impl.campaign.ids.IndEvo_Items;
import com.fs.starfarer.api.impl.campaign.ids.IndEvo_ids;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.misc.IndEvo_YardsCustomProductionIntel;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll;
import com.fs.starfarer.api.impl.campaign.rulecmd.ShowDefaultVisual;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvageYardsRules.IndEvo_InitSYCustomProductionDiag;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Highlights;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import org.apache.log4j.Logger;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.fs.starfarer.api.impl.campaign.rulecmd.academyRules.IndEvo_AcademyVariables.ACADEMY_MARKET_ID;


public class IndEvo_InitPersonalityTransferMenu extends BaseCommandPlugin implements InteractionDialogPlugin {

    //transfer skills, experience points and personality if within +/- 1 level
    //train officer +1 level up to level 6
    //Train officer +1 elite skill, randomly chosen, up to 3 times (with item)

    private enum Option {
        MAIN,
        PICK_ORIGINAL,
        PICK_TARGET,
        CONFIRM,
        RETURN_TOTAL,
        RETURN,
    }

    public static final Logger log = Global.getLogger(IndEvo_InitPersonalityTransferMenu.class);
    boolean debug = false;

    protected SectorEntityToken entity;
    protected InteractionDialogPlugin originalPlugin;
    protected InteractionDialogAPI dialog;
    protected Map<String, MemoryAPI> memoryMap;

    protected IndEvo_Academy academy;
    protected MarketAPI market;

    protected PersonAPI fromPerson = null;
    protected PersonAPI toPerson = null;

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        debug = Global.getSettings().isDevMode();

        if(!(dialog instanceof IndEvo_InitPersonalityTransferMenu)){
            this.dialog = dialog;
            this.memoryMap = memoryMap;
            if (dialog == null) return false;

            entity = dialog.getInteractionTarget();
            originalPlugin = dialog.getPlugin();
            dialog.setPlugin(this);
        }

        init(dialog);
        return true;
    }

    private IndEvo_InitSYCustomProductionDiag.YardsProductionData getProductionData(){
        return productionData;
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        log.info("initializing ACADEMY dialog plugin");

        this.market = getMarket();
        this.academy = (IndEvo_Academy) market.getIndustry(IndEvo_ids.ACADEMY);

        optionSelected(null, Option.MAIN);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        OptionPanelAPI opts = dialog.getOptionPanel();

        opts.clearOptions();

        Option option = Option.valueOf(optionData.toString());

        switch (option) {
            case MAIN:
                opts.addOption("Select Base Officer", Option.PICK_ORIGINAL, "Pick the officer to be used as base for the knowlege engram.");
                if(getValidOriginPersonList().isEmpty()){
                    opts.setEnabled(Option.PICK_ORIGINAL, false);
                    opts.setTooltip(Option.PICK_ORIGINAL, "You do not have any officers that could serve as base.");
                }

                opts.addOption("Select Target Officer", Option.PICK_TARGET, "Pick the officer you wish to overwrite.");
                if(fromPerson == null){
                    opts.setEnabled(Option.PICK_TARGET, false);
                    opts.setTooltip(Option.PICK_TARGET, "Select Base Officer first.");
                } else if(getValidTargetPersonList().isEmpty()){
                    opts.setEnabled(Option.PICK_ORIGINAL, false);
                    opts.setTooltip(Option.PICK_ORIGINAL, "You do not have any officers within one level of the base person.");
                }

                opts.addOption("Confirm", Option.CONFIRM, "Confirm your selection.");
                if(fromPerson == null || toPerson == null){
                    opts.setEnabled(Option.CONFIRM, false);
                    opts.setTooltip(Option.CONFIRM, "Select Base and Target Officer first.");
                }

                if(!market.isPlayerOwned() && Global.getSector().getPlayerFleet().getCargo().getCredits().get() >= getCost()){
                    opts.setEnabled(Option.CONFIRM, false);
                    opts.setTooltip(Option.CONFIRM, "You do not have sufficient funds to pay for the transfer.");
                }

                opts.addOption("Return", Option.RETURN_TOTAL);
                opts.setShortcut(Option.RETURN_TOTAL, Keyboard.KEY_ESCAPE, false, false, false, true);

                updatePanel();
                break;

            case PICK_TARGET:
            case PICK_ORIGINAL:
            case CONFIRM:
                finalizeSelection();
                break;
            case RETURN_TOTAL:
                returnToMenu();
                break;
            case RETURN:
                refreshOptions();
                break;
            default:
                throw new IllegalArgumentException("Unexpected argument: " + option);
        }
    }

    private List<PersonAPI> getValidOriginPersonList(){
        ArrayList<PersonAPI> finalList = new ArrayList<>();

        finalList.addAll(academy.getOfficerStorage());

        for (OfficerDataAPI officer : Global.getSector().getPlayerFleet().getFleetData().getOfficersCopy()) {
            if(Misc.isUnremovable(officer.getPerson())) continue;
            finalList.add(officer.getPerson());
        }

        return finalList;
    }

    private List<PersonAPI> getValidTargetPersonList(){
        ArrayList<PersonAPI> finalList = new ArrayList<>();
        if(fromPerson == null) return finalList;

        int targetLevel = fromPerson.getStats().getLevel();

        for (PersonAPI person : academy.getOfficerStorage()){
            int level = person.getStats().getLevel();
            if(level > targetLevel +1 || level < targetLevel -1) continue;

            finalList.add(person);
        }

        for (OfficerDataAPI officer : Global.getSector().getPlayerFleet().getFleetData().getOfficersCopy()) {
            if(Misc.isUnremovable(officer.getPerson())) continue;
            int level = officer.getPerson().getStats().getLevel();
            if(level > targetLevel +1 || level < targetLevel -1) continue;

            finalList.add(officer.getPerson());
        }

        finalList.remove(fromPerson);

        return finalList;
    }

    private float getCost(){
        return 0f;
    }

    private void refreshOptions() {
        optionSelected(null, Option.MAIN);
    }

    private void addTooltip(TextPanelAPI panel) {
        float pad = 5f;
        IndEvo_InitSYCustomProductionDiag.YardsProductionData productionData = getProductionData();
        String rarepartsName = Global.getSettings().getCommoditySpec(IndEvo_Items.RARE_PARTS).getName();
        String partsName = Global.getSettings().getCommoditySpec(IndEvo_Items.PARTS).getName();

        panel.addPara("\"Spend enough time taking 'em apart, you get to know how to make one. It's probably even going to be spaceworthy! Bring us the parts we can't just scrounge up, pay us, and we'll build you a beauty!\" - The foreman lowers his voice, \"Without serial numbers, of course.\"");
        panel.addPara("The Salvage Yards can construct ships for you if you provide the blueprints and components that are too rare to \"acquire\".\n\n");

        panel.addPara("Contract information:");
        panel.setFontSmallInsignia();

        panel.addParagraph("-----------------------------------------------------------------------------");
        panel.addPara("Maximum capacity: " + Misc.getDGSCredits(getCapacity()));
        panel.highlightInLastPara(Misc.getHighlightColor(), Misc.getDGSCredits(getCapacity()));

        if(!tradeInList.isEmpty()){
            float add = getAdditionalCapByShips();

            panel.addPara(BaseIntelPlugin.BULLET + "Additional " + Misc.getDGSCredits(add) + " by trading in " + tradeInList.size() + " hulls");
            panel.highlightInLastPara(Misc.getHighlightColor(), Misc.getDGSCredits(add));
        }

        if(isWantsToSpendSP()){
            panel.addPara(BaseIntelPlugin.BULLET + "Doubled by Story Point");
            panel.highlightInLastPara(Misc.getPositiveHighlightColor(), "Story Point");
        }

        String costMultStr = IndEvo_StringHelper.getAbsPercentString(costMult, false);
        Pair<String, Color> repInt = IndEvo_StringHelper.getRepIntTooltipPair(getMarket().getFaction());

        panel.addPara("Produced at cost: " + costMultStr + " " + repInt.one);

        Highlights h = new Highlights();
        h.setText(costMultStr, repInt.one);
        h.setColors(Misc.getHighlightColor(), repInt.two);
        panel.setHighlightsInLastPara(h);

        panel.addPara("Delivery time: " + DELIVERY_TIME + " " + IndEvo_StringHelper.getDayOrDays(DELIVERY_TIME));
        panel.highlightInLastPara(Misc.getHighlightColor(), DELIVERY_TIME + " " + IndEvo_StringHelper.getDayOrDays(DELIVERY_TIME));

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
            if (i > 9 &&  productionData.productionList.size() > 10) {
                tooltip.addPara("... and " + ( productionData.productionList.size() - 9) + " additional hulls.", 3f);
            }
        } else {
            panel.addPara("    Select any ships you wish to order.");
        }

        panel.addTooltip();
        panel.addPara("Reduce the amount of D-Mods by spending " + rarepartsName + ".", Misc.getGrayColor());
        panel.addPara("Reduce the cost by up to 50% by trading in " + partsName + ".", Misc.getGrayColor());

        panel.addParagraph("-----------------------------------------------------------------------------");
        panel.setFontInsignia();

        if(productionData.productionList.isEmpty()) panel.addPara("No ships selected yet, D-Mod or cost forecast not possible.");
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

        IndEvo_YardsCustomProductionIntel intel = new IndEvo_YardsCustomProductionIntel(getMarket(), getProductionData());
        Global.getSector().getIntelManager().addIntel(intel);
        intel.init();

        returnToMenu();
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
    }

    private void updatePanel() {
        TextPanelAPI panel = dialog.getTextPanel();
        panel.clear();

        addTooltip(panel);
    }
    
    public MarketAPI getMarket(){
        MarketAPI market;
        if(memoryMap.get(MemKeys.LOCAL).get("$id").equals("station_galatia_academy"))
            market = Global.getSector().getEconomy().getMarket(ACADEMY_MARKET_ID);
        else
            market = Global.getSector().getEconomy().getMarket(memoryMap.get(MemKeys.MARKET).getString("$id"));
        return market;
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
}
