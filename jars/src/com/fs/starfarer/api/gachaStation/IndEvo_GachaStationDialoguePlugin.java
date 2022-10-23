package com.fs.starfarer.api.gachaStation;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.IndEvo_IndustryHelper;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.econ.impl.courierPort.ShippingContractMemory;
import com.fs.starfarer.api.impl.campaign.econ.impl.courierPort.dialogue.CourierPortDialoguePlugin;
import com.fs.starfarer.api.splinterFleet.plugins.dialogue.AbilityPanelDialoguePlugin;
import com.fs.starfarer.api.splinterFleet.plugins.dialogue.customPanelPlugins.VisualCustomPanel;
import com.fs.starfarer.api.util.ListMap;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.lwjgl.input.Keyboard;

import java.util.HashMap;
import java.util.Map;

import static com.fs.starfarer.api.splinterFleet.plugins.fleetManagement.Behaviour.updateActiveDetachmentBehaviour;

public class IndEvo_GachaStationDialoguePlugin implements InteractionDialogPlugin {

    private enum Option {
        MAIN,
        MAIN_LOCKED,
        INITIAL_PAY_TO_RESTORE,
        SELECT,
        CONFIRM,
        LEAVE
    }

    //ability can be called in hyperspace but will only allow ABANDON_FLEET

    protected InteractionDialogAPI dialog;
    protected TextPanelAPI text;
    protected OptionPanelAPI options;

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        this.options = dialog.getOptionPanel();
        this.text = dialog.getTextPanel();

        //Misc.showCost(dialog.getTextPanel(), "Resources: required (available)", true, widthOverride, null, null, ids, qty, consumed);

        displayDefaultOptions();
    }

    public void displayDefaultOptions() {


        addTooltip(dialog.getTextPanel());

        OptionPanelAPI opts = dialog.getOptionPanel();
        opts.clearOptions();

        opts.addOption("New Contract", CourierPortDialoguePlugin.Option.NEW_CONTRACT);
        opts.addOption("Manage Contracts", CourierPortDialoguePlugin.Option.MANAGE_CONTRACTS);
        opts.setEnabled(CourierPortDialoguePlugin.Option.MANAGE_CONTRACTS, !ShippingContractMemory.getContractList().isEmpty());

        opts.addOption("Return", CourierPortDialoguePlugin.Option.RETURN);
        opts.setShortcut(CourierPortDialoguePlugin.Option.RETURN, Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        options.clearOptions();
        AbilityPanelDialoguePlugin.Option opt = (AbilityPanelDialoguePlugin.Option) optionData;

        switch (opt) {
            case MAIN:
                refreshCustomPanel();
                displayAbilityInactiveDialogueText();

                options.addOption("Return", AbilityPanelDialoguePlugin.Option.CLOSE_PANEL);
                options.setShortcut(AbilityPanelDialoguePlugin.Option.CLOSE_PANEL, Keyboard.KEY_ESCAPE, false, false, false, false);
                break;
            case CLOSE_PANEL:
                VisualCustomPanel.clearPanel();
                updateActiveDetachmentBehaviour();
                dialog.dismiss();
                break;
        }
    }

    @Override
    public void optionMousedOver(String optionText, Object optionData) {

    }

    @Override
    public void advance(float amount) {

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
        return null;
    }


    private Map<String, Float> createHullIdSelection() {
        Map<String, Float> hullMap = new HashMap<>();
        WeightedRandomPicker<ShipHullSpecAPI> picker = new WeightedRandomPicker<>();
        ListMap<ShipHullSpecAPI> specAPIListMap = getHullSizeHullSpecListMap();

        for (ShipAPI.HullSize size : new ShipAPI.HullSize[]{
                ShipAPI.HullSize.FRIGATE,
                ShipAPI.HullSize.DESTROYER,
                ShipAPI.HullSize.CRUISER,
                ShipAPI.HullSize.CAPITAL_SHIP}) {

            picker.addAll(specAPIListMap.get(size.toString()));

            if (picker.isEmpty()) continue;

            ShipHullSpecAPI spec = picker.pick();
            ShipVariantAPI var = Global.getSettings().createEmptyVariant(Misc.genUID(), spec);
            hullMap.put(spec.getHullId(), Global.getFactory().createFleetMember(FleetMemberType.SHIP, var).getDeploymentPointsCost());
            picker.clear();
        }

        return hullMap;
    }

    private ListMap<ShipHullSpecAPI> getHullSizeHullSpecListMap() {
        ListMap<ShipHullSpecAPI> lm = new ListMap<>();
        for (ShipAPI.HullSize size : new ShipAPI.HullSize[]{
                ShipAPI.HullSize.FRIGATE,
                ShipAPI.HullSize.DESTROYER,
                ShipAPI.HullSize.CRUISER,
                ShipAPI.HullSize.CAPITAL_SHIP}) {
            for (ShipHullSpecAPI spec : IndEvo_IndustryHelper.getAllLearnableShipHulls()) {
                if (spec.getHullSize().equals(size)) lm.getList(size.toString()).add(spec);
            }
        }

        return lm;
    }
}
