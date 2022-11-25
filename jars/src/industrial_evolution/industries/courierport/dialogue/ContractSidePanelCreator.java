package industrial_evolution.industries.courierport.dialogue;

import com.fs.starfarer.api.Global;
import industrial_evolution.IndEvo_StringHelper;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.courierPort.*;
import industrial_evolution.campaign.econ.impl.courierPort.*;
import industrial_evolution.industries.courierport.*;
import industrial_evolution.industries.courierport.listeners.SubmarketCargoPicker;
import industrial_evolution.industries.courierport.listeners.SubmarketShipPicker;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import industrial_evolution.plugins.IndEvo_modPlugin;
import industrial_evolution.splinterFleet.dialogue.customPanelPlugins.InteractionDialogCustomPanelPlugin;
import industrial_evolution.splinterFleet.dialogue.customPanelPlugins.NoFrameCustomPanelPlugin;
import industrial_evolution.splinterFleet.dialogue.customPanelPlugins.VisualCustomPanel;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;
import java.util.LinkedList;
import java.util.List;

import static industrial_evolution.industries.courierport.ShippingCostCalculator.CONTRACT_BASE_FEE;

public class ContractSidePanelCreator {

    protected static final float PANEL_WIDTH_1 = 240;
    protected static final float PANEL_WIDTH_2 = VisualCustomPanel.PANEL_WIDTH - PANEL_WIDTH_1 - 8;
    protected static final float SHIP_ICON_WIDTH = 48;
    protected static final float ARROW_BUTTON_WIDTH = 20, BUTTON_HEIGHT = 30;
    protected static final float SELECT_BUTTON_WIDTH = 95f;
    protected static final float TEXT_FIELD_WIDTH = 80f;

    public static final String CONTRACT_MEMORY = "$IndEvo_ShippingContractMemory";

    public void showPanel(InteractionDialogAPI dialogue, ShippingContract contract) {
        VisualCustomPanel.createPanel(dialogue, true);

        backupContract(contract);
        showCustomPanel(dialogue, contract);
    }

    public static void backupContract(ShippingContract contract){
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        if(contract != null && !mem.contains(CONTRACT_MEMORY)) mem.set(CONTRACT_MEMORY, contract.getCopy(), 0f);
    }

    public static ShippingContract getContractBackup(){
        return (ShippingContract) Global.getSector().getMemoryWithoutUpdate().get(CONTRACT_MEMORY);
    }

    public static void clearBackup(){
        Global.getSector().getMemoryWithoutUpdate().unset(CONTRACT_MEMORY);
    }

    // NAME
    // SET NAME - TEXBOX
    // ORIGIN - CHOOSE PLANET - CHOOSE STORAGE - CHOOSE SCOPE < [ALL] [ALL SHIPS] [ALL CARGO] [BUTTON - SPECIFY] >
    // TARGET - CHOOSE PLANET - CHOOSE STORAGE
    // SET DURATION - < [ONCE] [14 DAYS] [1 MONTH] [2 MONTH] [3 MONTH] [6 MONTH] >
    // RESET - CONFIRM
    // CONTRACT INFO
    //       Cargo info, Ship info (maybe a list?)
    //       Cost info (with breakdown incl. distance)

    private void showCustomPanel(final InteractionDialogAPI dialogue, ShippingContract initContract) {
        float opad = 10f;
        float spad = 3f;

        final CustomPanelAPI panel = VisualCustomPanel.getPanel();
        TooltipMakerAPI panelTooltip = VisualCustomPanel.getTooltip();
        final CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        boolean newContract = false;
        if (initContract == null) {
            initContract = new ShippingContract();
            newContract = true;
        }

        final ShippingContract contract = initContract;
        String id = contract.getId();
        if(contract.scope == null) contract.scope = ShippingContract.Scope.EVERYTHING;

        TooltipMakerAPI lastUsedVariableButtonAnchor;

        panelTooltip.addSectionHeading(contract.name, Alignment.MID, opad);
        CustomPanelAPI renamePanel = panel.createCustomPanel(PANEL_WIDTH_1, BUTTON_HEIGHT, new NoFrameCustomPanelPlugin());

        //NAME PANEL
        TooltipMakerAPI desc = renamePanel.createUIElement(SELECT_BUTTON_WIDTH * 1.3f, BUTTON_HEIGHT, false);
        LabelAPI label = desc.addPara("Contract Name: ", 0f);

        renamePanel.addUIElement(desc).inTL(spad, opad);
        lastUsedVariableButtonAnchor = desc;

        TooltipMakerAPI textbox = renamePanel.createUIElement(TEXT_FIELD_WIDTH * 4f, BUTTON_HEIGHT, false);
        final TextFieldAPI textField = textbox.addTextField(TEXT_FIELD_WIDTH * 4f, BUTTON_HEIGHT);

        if (!newContract) textField.setText(contract.name);
        PositionAPI pos = renamePanel.addUIElement(textbox).rightOfMid(lastUsedVariableButtonAnchor, 0f);         //second in row
        pos.setYAlignOffset(23f);
        lastUsedVariableButtonAnchor = textbox;

        panelTooltip.addCustom(renamePanel, opad); //add panel

        //CADENCE
        CustomPanelAPI cadenceSelectionPanel = panel.createCustomPanel(PANEL_WIDTH_1, 50f, new NoFrameCustomPanelPlugin());
        TooltipMakerAPI anchor = cadenceSelectionPanel.createUIElement(SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, false);

        boolean prerequisiteForActive = true;

        Color baseColor = Misc.getButtonTextColor();
        Color bgColour = Misc.getDarkPlayerColor();
        Color brightColor = Misc.getBrightPlayerColor();

        int timing = contract.getRecurrentDays();
        List<Integer> dayList = new LinkedList<>();
        dayList.add(14);
        dayList.add(31);
        dayList.add(31 * 3);
        dayList.add(31 * 6);

        String buttonId = "button_cadence_once" + id;

        anchor = cadenceSelectionPanel.createUIElement(SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, false);
        ButtonAPI areaCheckbox = anchor.addAreaCheckbox("Once", new Object(), baseColor, bgColour, brightColor, //new Color(255,255,255,0)
                SELECT_BUTTON_WIDTH,
                BUTTON_HEIGHT,
                0f,
                false);

        areaCheckbox.setChecked(timing == 0);

        InteractionDialogCustomPanelPlugin.ButtonEntry entry = new InteractionDialogCustomPanelPlugin.ButtonEntry(areaCheckbox, buttonId) {
            @Override
            public void onToggle() {
                if (!textField.getText().isEmpty() && !textField.getText().equals(contract.name))
                    contract.name = textField.getText();
                contract.setRecurrentDays(0);
                showPanel(dialogue, contract);
            }
        };

        VisualCustomPanel.getPlugin().addButton(entry);
        cadenceSelectionPanel.addUIElement(anchor).inTL(spad, opad);         //second in row
        lastUsedVariableButtonAnchor = anchor;

        for (final Integer days : dayList) {
            buttonId = "button_cadence_" + days + id;
            String name = ShippingTooltipHelper.getCadenceString(days);

            anchor = cadenceSelectionPanel.createUIElement(SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, false);
            areaCheckbox = anchor.addAreaCheckbox(name, new Object(), baseColor, bgColour, brightColor, //new Color(255,255,255,0)
                    SELECT_BUTTON_WIDTH,
                    BUTTON_HEIGHT,
                    0f,
                    false);

            areaCheckbox.setChecked(timing == days);
            entry = new InteractionDialogCustomPanelPlugin.ButtonEntry(areaCheckbox, buttonId) {
                @Override
                public void onToggle() {
                    if (!textField.getText().isEmpty() && !textField.getText().equals(contract.name))
                        contract.name = textField.getText();
                    contract.setRecurrentDays(days);
                    showPanel(dialogue, contract);
                }
            };

            VisualCustomPanel.getPlugin().addButton(entry);

            cadenceSelectionPanel.addUIElement(anchor).rightOfMid(lastUsedVariableButtonAnchor, opad);
            lastUsedVariableButtonAnchor = anchor;
        }

        panelTooltip.addCustom(cadenceSelectionPanel, opad); //add panel

        panelTooltip.addSectionHeading("Origin and Destination", Alignment.MID, opad);

        // ORIGIN SELECTION PANEL

        CustomPanelAPI originSelectionPanel = panel.createCustomPanel(PANEL_WIDTH_1, 50f, new NoFrameCustomPanelPlugin());

        desc = originSelectionPanel.createUIElement(SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, false);
        label = desc.addPara("Ship from:", 0f);

        label.getPosition().inTMid(6f);
        label.setAlignment(Alignment.MID);
        label.getPosition().setXAlignOffset(6f);

        originSelectionPanel.addUIElement(desc).inTL(spad, opad);         //second in row
        lastUsedVariableButtonAnchor = desc;

        anchor = originSelectionPanel.createUIElement(SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, false);

        buttonId = "button_origin_" + id;

        ButtonAPI button = anchor.addButton("Pick Origin", buttonId, baseColor, bgColour, Alignment.MID, CutStyle.C2_MENU, SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, 0);
        entry = new InteractionDialogCustomPanelPlugin.ButtonEntry(button, buttonId) {
            @Override
            public void onToggle() {
                if (!textField.getText().isEmpty() && !textField.getText().equals(contract.name))
                    contract.name = textField.getText();
                VisualCustomPanel.clearPanel();

                dialogue.showCampaignEntityPicker("Select a planet to ship from", "Selected: ", "Confirm", playerFleet.getFaction(), ShippingTargetHelper.getValidOriginPlanets(), new CampaignEntityPickerListener() {
                    @Override
                    public String getMenuItemNameOverrideFor(SectorEntityToken entity) {
                        MarketAPI m = entity.getMarket();
                        int amt = (int) Misc.getStorageCargo(m).getSpaceUsed();
                        return m.getName() + " (" + m.getFaction().getDisplayName() + ")";
                    }

                    @Override
                    public void pickedEntity(SectorEntityToken entity) {
                        contract.fromMarketId = entity.getMarket().getId();
                        contract.fromSubmarketId = Submarkets.SUBMARKET_STORAGE;
                        contract.toMarketId = null;

                        showPanel(dialogue, contract);
                    }

                    @Override
                    public void cancelledEntityPicking() {
                        showPanel(dialogue, contract);
                    }

                    @Override
                    public String getSelectedTextOverrideFor(SectorEntityToken entity) {
                        MarketAPI m = entity.getMarket();
                        int amt = (int) Misc.getStorageCargo(m).getSpaceUsed();
                        return m.getName() + " (" + m.getFaction().getDisplayName() + ", items in storage" + amt;
                    }

                    @Override
                    public void createInfoText(TooltipMakerAPI info, SectorEntityToken entity) {
                        float opad = 10f;
                        MarketAPI m = entity.getMarket();
                        int amt = (int) Misc.getStorageCargo(m).getSpaceUsed();
                        info.addPara(m.getName() + " (" + m.getFaction().getDisplayName()
                                + ", size " + m.getSize() + ", " + amt + " items in storage", opad);
                    }

                    @Override
                    public boolean canConfirmSelection(SectorEntityToken entity) {
                        return entity != null && entity.getMarket() != null;
                    }

                    @Override
                    public float getFuelColorAlphaMult() {
                        return 0;
                    }

                    @Override
                    public float getFuelRangeMult() {
                        return 0;
                    }
                });
            }
        };

        VisualCustomPanel.getPlugin().addButton(entry);
        originSelectionPanel.addUIElement(anchor).rightOfMid(lastUsedVariableButtonAnchor, opad);
        lastUsedVariableButtonAnchor = anchor;

        //SUBMARKET

        MarketAPI fromMarket = contract.getFromMarket();
        prerequisiteForActive = fromMarket != null;

        if (prerequisiteForActive) {
            boolean first = true;

            float aspectRatio = 1f;
            float baseWidth = SELECT_BUTTON_WIDTH * 0.7f;

            for (SubmarketAPI s : ShippingTargetHelper.getValidOriginSubmarkets(fromMarket)) {
                final String submarketID = s.getSpecId();
                buttonId = "button_fromSubmarket_" + submarketID + id;

                baseColor = s.getFaction().getBaseUIColor();
                bgColour = s.getFaction().getDarkUIColor();
                brightColor = s.getFaction().getBrightUIColor();

                SpriteAPI sprite = Global.getSettings().getSprite(s.getSpec().getIcon());
                if(sprite == null) sprite = Global.getSettings().getSprite(fromMarket.getFaction().getLogo());

                aspectRatio = sprite.getHeight() / sprite.getWidth();

                anchor = originSelectionPanel.createUIElement(baseWidth, BUTTON_HEIGHT, false);
                areaCheckbox = anchor.addAreaCheckbox("", new Object(), baseColor, bgColour, brightColor, //new Color(255,255,255,0)
                        baseWidth * aspectRatio + 6f,
                        BUTTON_HEIGHT + 4f,
                        0f,
                        false);

                areaCheckbox.setChecked(submarketID.equals(contract.fromSubmarketId));

                entry = new InteractionDialogCustomPanelPlugin.ButtonEntry(areaCheckbox, buttonId) {
                    @Override
                    public void onToggle() {
                        if (!textField.getText().isEmpty() && !textField.getText().equals(contract.name))
                            contract.name = textField.getText();

                        IndEvo_modPlugin.log("buttonPress " + submarketID);

                        contract.fromSubmarketId = submarketID;
                        contract.toSubmarketId = Submarkets.SUBMARKET_STORAGE;
                        contract.scope = ShippingContract.Scope.EVERYTHING;

                        showPanel(dialogue, contract);
                    }
                };

                VisualCustomPanel.getPlugin().addButton(entry);

                originSelectionPanel.addUIElement(anchor).rightOfMid(lastUsedVariableButtonAnchor, first ? 10f : -7f);
                lastUsedVariableButtonAnchor = anchor;

                anchor = originSelectionPanel.createUIElement(baseWidth, BUTTON_HEIGHT, false);
                anchor.addImage(s.getSpec().getIcon(),
                        baseWidth * aspectRatio,
                        BUTTON_HEIGHT,
                        0f);

                pos = originSelectionPanel.addUIElement(anchor).rightOfMid(lastUsedVariableButtonAnchor, first ? 0f :-7f);
                pos.setXAlignOffset(-(baseWidth - 4f));

                first = false;
            }

            if(fromMarket.isPlayerOwned() && fromMarket.hasSubmarket(Submarkets.LOCAL_RESOURCES)){
                final SubmarketAPI s = fromMarket.getSubmarket(Submarkets.LOCAL_RESOURCES);
                final String submarketID = s.getSpecId();

                buttonId = "button_fromSubmarket_" + submarketID + id;

                baseColor = fromMarket.getFaction().getBaseUIColor();
                bgColour = fromMarket.getFaction().getDarkUIColor();
                brightColor = fromMarket.getFaction().getBrightUIColor();

                IndEvo_modPlugin.log("aspectRatio " + aspectRatio);

                anchor = originSelectionPanel.createUIElement(baseWidth, BUTTON_HEIGHT, false);
                areaCheckbox = anchor.addAreaCheckbox("", new Object(), baseColor, bgColour, brightColor, //new Color(255,255,255,0)
                        baseWidth * aspectRatio + 6f,
                        BUTTON_HEIGHT + 4f,
                        0f,
                        false);

                areaCheckbox.setChecked(submarketID.equals(contract.fromSubmarketId));

                entry = new InteractionDialogCustomPanelPlugin.ButtonEntry(areaCheckbox, buttonId) {
                    @Override
                    public void onToggle() {
                        if (!textField.getText().isEmpty() && !textField.getText().equals(contract.name))
                            contract.name = textField.getText();

                        IndEvo_modPlugin.log("buttonPress " + submarketID);

                        contract.fromSubmarketId = submarketID;
                        contract.toSubmarketId = Submarkets.SUBMARKET_STORAGE;
                        contract.scope = ShippingContract.Scope.EVERYTHING;

                        showPanel(dialogue, contract);
                    }
                };

                VisualCustomPanel.getPlugin().addButton(entry);

                originSelectionPanel.addUIElement(anchor).rightOfMid(lastUsedVariableButtonAnchor, -7f);
                lastUsedVariableButtonAnchor = anchor;

                anchor = originSelectionPanel.createUIElement(baseWidth, BUTTON_HEIGHT, false);
                anchor.addImage(fromMarket.getFaction().getLogo(),
                        baseWidth * aspectRatio,
                        BUTTON_HEIGHT,
                        0f);

                pos = originSelectionPanel.addUIElement(anchor).rightOfMid(lastUsedVariableButtonAnchor, -7f);
                pos.setXAlignOffset(-(baseWidth - 4f));
            }

        } else {
            desc = originSelectionPanel.createUIElement(SELECT_BUTTON_WIDTH * 2f, BUTTON_HEIGHT, false);
            label = desc.addPara("Select an origin planet!", Misc.getHighlightColor(), 0f);

            label.getPosition().inTMid(6f);
            label.setAlignment(Alignment.LMID);
            label.getPosition().setXAlignOffset(6f);

            originSelectionPanel.addUIElement(desc).rightOfMid(lastUsedVariableButtonAnchor, opad);
            lastUsedVariableButtonAnchor = desc;
        }

        panelTooltip.addCustom(originSelectionPanel, opad); //add panel

        //PLANET SELECT

        CustomPanelAPI targetSelectionPanel = panel.createCustomPanel(PANEL_WIDTH_1, 50f, new NoFrameCustomPanelPlugin());

        desc = targetSelectionPanel.createUIElement(SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, false);
        label = desc.addPara("Ship to:", 0f);

        label.getPosition().inTMid(6f);
        label.setAlignment(Alignment.MID);
        label.getPosition().setXAlignOffset(6f);

        targetSelectionPanel.addUIElement(desc).inTL(spad, opad);
        lastUsedVariableButtonAnchor = desc;

        baseColor = Misc.getButtonTextColor();
        bgColour = Misc.getDarkPlayerColor();
        brightColor = Misc.getBrightPlayerColor();

        anchor = targetSelectionPanel.createUIElement(SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, false);
        buttonId = "button_dest_" + id;

        button = anchor.addButton("Pick Target", buttonId, baseColor, bgColour, Alignment.MID, CutStyle.C2_MENU, SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, 0);
        entry = new InteractionDialogCustomPanelPlugin.ButtonEntry(button, buttonId) {
            @Override
            public void onToggle() {
                if (!textField.getText().isEmpty() && !textField.getText().equals(contract.name))
                    contract.name = textField.getText();
                VisualCustomPanel.clearPanel();

                dialogue.showCampaignEntityPicker("Select a planet to ship to", "Selected: ", "Confirm", playerFleet.getFaction(), ShippingTargetHelper.getValidTargetPlanets(contract), new CampaignEntityPickerListener() {
                    @Override
                    public String getMenuItemNameOverrideFor(SectorEntityToken entity) {
                        MarketAPI m = entity.getMarket();
                        return m.getName() + " (" + m.getFaction().getDisplayName() + ")";
                    }

                    @Override
                    public void pickedEntity(SectorEntityToken entity) {
                        contract.toMarketId = entity.getMarket().getId();
                        contract.toSubmarketId = Submarkets.SUBMARKET_STORAGE;
                        showPanel(dialogue, contract);
                    }

                    @Override
                    public void cancelledEntityPicking() {
                        showPanel(dialogue, contract);
                    }

                    @Override
                    public String getSelectedTextOverrideFor(SectorEntityToken entity) {
                        MarketAPI m = entity.getMarket();
                        return m.getName() + " (" + m.getFaction().getDisplayName() + ")";
                    }

                    @Override
                    public void createInfoText(TooltipMakerAPI info, SectorEntityToken entity) {
                        float opad = 10f;
                        MarketAPI m = entity.getMarket();
                        info.addPara(m.getName() + " (" + m.getFaction().getDisplayName()
                                + ", size " + m.getSize() + ")", opad);

                        info.addPara("Distance cost multiplicator: %s",
                                opad,
                                Misc.getHighlightColor(),
                                Misc.getRoundedValueMaxOneAfterDecimal(ShippingCostCalculator.getLYMult(contract.getFromMarket().getPrimaryEntity(), entity)) + "x");
                    }

                    @Override
                    public boolean canConfirmSelection(SectorEntityToken entity) {
                        return entity != null && entity.getMarket() != null;
                    }

                    @Override
                    public float getFuelColorAlphaMult() {
                        return 0;
                    }

                    @Override
                    public float getFuelRangeMult() {
                        return 0;
                    }
                });
            }
        };

        VisualCustomPanel.getPlugin().addButton(entry);
        targetSelectionPanel.addUIElement(anchor).rightOfMid(lastUsedVariableButtonAnchor, opad);         //second in row
        lastUsedVariableButtonAnchor = anchor;

        MarketAPI toMarket = contract.getToMarket();

        prerequisiteForActive = toMarket != null && contract.fromSubmarketId != null;

        if (prerequisiteForActive) {
            boolean first = true;
            for (final SubmarketAPI s : ShippingTargetHelper.getValidTargetSubmarkets(toMarket, contract.getFromSubmarket())) {

                final String submarketID = s.getSpecId();
                buttonId = "button_toSubmarket_" + submarketID + id;

                baseColor = s.getFaction().getBaseUIColor();
                bgColour = s.getFaction().getDarkUIColor();
                brightColor = s.getFaction().getBrightUIColor();

                SpriteAPI sprite = Global.getSettings().getSprite(s.getSpec().getIcon());
                if(sprite == null) sprite = Global.getSettings().getSprite(toMarket.getFaction().getLogo());

                float aspectRatio = sprite.getHeight() / sprite.getWidth();
                float baseWidth = SELECT_BUTTON_WIDTH * 0.7f;

                anchor = targetSelectionPanel.createUIElement(baseWidth, BUTTON_HEIGHT, false);
                areaCheckbox = anchor.addAreaCheckbox("", new Object(), baseColor, bgColour, brightColor, //new Color(255,255,255,0)
                        baseWidth * aspectRatio + 6f,
                        BUTTON_HEIGHT + 4f,
                        0f,
                        false);

                areaCheckbox.setChecked(submarketID.equals(contract.toSubmarketId));

                entry = new InteractionDialogCustomPanelPlugin.ButtonEntry(areaCheckbox, buttonId) {
                    @Override
                    public void onToggle() {
                        if (!textField.getText().isEmpty() && !textField.getText().equals(contract.name))
                            contract.name = textField.getText();

                        contract.toSubmarketId = submarketID;
                        showPanel(dialogue, contract);
                    }
                };

                VisualCustomPanel.getPlugin().addButton(entry);

                targetSelectionPanel.addUIElement(anchor).rightOfMid(lastUsedVariableButtonAnchor, first ? 10f : -7f);
                lastUsedVariableButtonAnchor = anchor;

                anchor = targetSelectionPanel.createUIElement(baseWidth, BUTTON_HEIGHT, false);
                anchor.addImage(s.getSpec().getIcon(),
                        baseWidth * aspectRatio,
                        BUTTON_HEIGHT,
                        0f);

                pos = targetSelectionPanel.addUIElement(anchor).rightOfMid(lastUsedVariableButtonAnchor, first ? 0f : -7f);
                pos.setXAlignOffset(-(baseWidth - 4f));

                first = false;
            }
        } else {
            desc = targetSelectionPanel.createUIElement(SELECT_BUTTON_WIDTH * 2f, BUTTON_HEIGHT, false);
            label = desc.addPara(fromMarket == null ? "Select a destination planet!" : "Select an origin submarket!", Misc.getHighlightColor(), 0f);

            label.getPosition().inTMid(6f);
            label.setAlignment(Alignment.LMID);
            label.getPosition().setXAlignOffset(6f);

            targetSelectionPanel.addUIElement(desc).rightOfMid(lastUsedVariableButtonAnchor, opad);
            lastUsedVariableButtonAnchor = desc;
        }

        panelTooltip.addCustom(targetSelectionPanel, 0f); //add panel


        prerequisiteForActive = contract.fromMarketId != null && contract.toMarketId != null && contract.fromSubmarketId != null && contract.toSubmarketId != null;

        if (prerequisiteForActive) {
            //SCOPE
            CustomPanelAPI scopeSelectionPanel = panel.createCustomPanel(PANEL_WIDTH_1, 50f, new NoFrameCustomPanelPlugin());

            SubmarketPlugin fromSubmarketPlugin = fromMarket.getSubmarket(contract.fromSubmarketId).getPlugin();
            SubmarketPlugin toSubmarketPlugin = toMarket.getSubmarket(contract.toSubmarketId).getPlugin();

            boolean showCargo = fromSubmarketPlugin.showInCargoScreen() && toSubmarketPlugin.showInCargoScreen();
            boolean showSips = fromSubmarketPlugin.showInFleetScreen() && toSubmarketPlugin.showInFleetScreen();

            //EVERYTHING
            anchor = scopeSelectionPanel.createUIElement(SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, false);
            String name = "Everything";
            ShippingContract.Scope scope = ShippingContract.Scope.EVERYTHING;
            buttonId = "button_scope_" + scope.toString() + id;

            baseColor = Misc.getButtonTextColor();
            bgColour = Misc.getDarkPlayerColor();
            brightColor = Misc.getBrightPlayerColor();

            areaCheckbox = anchor.addAreaCheckbox(name, new Object(), baseColor, bgColour, brightColor,
                    SELECT_BUTTON_WIDTH,
                    BUTTON_HEIGHT,
                    0f,
                    false);

            areaCheckbox.setChecked(contract.scope == scope);

            entry = new InteractionDialogCustomPanelPlugin.ButtonEntry(areaCheckbox, buttonId) {
                @Override
                public void onToggle() {
                    if (!textField.getText().isEmpty() && !textField.getText().equals(contract.name))
                        contract.name = textField.getText();

                    contract.scope = ShippingContract.Scope.EVERYTHING;
                    showPanel(dialogue, contract);
                }
            };

            VisualCustomPanel.getPlugin().addButton(entry);
            scopeSelectionPanel.addUIElement(anchor).inTL(spad, opad);         //second in row
            lastUsedVariableButtonAnchor = anchor;

            if (showCargo) {
                anchor = scopeSelectionPanel.createUIElement(SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, false);
                name = "All Cargo";
                scope = ShippingContract.Scope.ALL_CARGO;
                buttonId = "button_scope_" + scope.toString() + id;

                areaCheckbox = anchor.addAreaCheckbox(name, new Object(), baseColor, bgColour, brightColor,
                        SELECT_BUTTON_WIDTH,
                        BUTTON_HEIGHT,
                        0f,
                        false);

                areaCheckbox.setChecked(contract.scope == scope);

                entry = new InteractionDialogCustomPanelPlugin.ButtonEntry(areaCheckbox, buttonId) {
                    @Override
                    public void onToggle() {
                        if (!textField.getText().isEmpty() && !textField.getText().equals(contract.name))
                            contract.name = textField.getText();

                        if (contract.scope == ShippingContract.Scope.ALL_CARGO) contract.scope = ShippingContract.Scope.EVERYTHING;
                        else contract.scope = ShippingContract.Scope.ALL_CARGO;

                        showPanel(dialogue, contract);
                    }
                };

                VisualCustomPanel.getPlugin().addButton(entry);
                scopeSelectionPanel.addUIElement(anchor).rightOfMid(lastUsedVariableButtonAnchor, opad);        //second in row
                lastUsedVariableButtonAnchor = anchor;
            }

            if (showSips) {
                anchor = scopeSelectionPanel.createUIElement(SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, false);
                name = "All Ships";
                scope = ShippingContract.Scope.ALL_SHIPS;
                buttonId = "button_scope_" + scope.toString() + id;

                areaCheckbox = anchor.addAreaCheckbox(name, new Object(), baseColor, bgColour, brightColor,
                        SELECT_BUTTON_WIDTH,
                        BUTTON_HEIGHT,
                        0f,
                        false);

                areaCheckbox.setChecked(contract.scope == scope);

                entry = new InteractionDialogCustomPanelPlugin.ButtonEntry(areaCheckbox, buttonId) {
                    @Override
                    public void onToggle() {
                        if (!textField.getText().isEmpty() && !textField.getText().equals(contract.name))
                            contract.name = textField.getText();

                        if (contract.scope == ShippingContract.Scope.ALL_SHIPS) contract.scope = ShippingContract.Scope.EVERYTHING;
                        else contract.scope = ShippingContract.Scope.ALL_SHIPS;

                        showPanel(dialogue, contract);
                    }
                };

                VisualCustomPanel.getPlugin().addButton(entry);
                scopeSelectionPanel.addUIElement(anchor).rightOfMid(lastUsedVariableButtonAnchor, opad);        //second in row
                lastUsedVariableButtonAnchor = anchor;
            }

            if (showCargo) {
                anchor = scopeSelectionPanel.createUIElement(SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, false);
                name = "Select Cargo";
                scope = ShippingContract.Scope.SPECIFIC_CARGO;
                buttonId = "button_scope_" + scope.toString() + id;

                areaCheckbox = anchor.addAreaCheckbox(name, new Object(), baseColor, bgColour, brightColor,
                        SELECT_BUTTON_WIDTH,
                        BUTTON_HEIGHT,
                        0f,
                        false);

                areaCheckbox.setChecked(contract.scope == scope || contract.scope == ShippingContract.Scope.SPECIFIC_EVERYTHING);

                entry = new InteractionDialogCustomPanelPlugin.ButtonEntry(areaCheckbox, buttonId) {
                    @Override
                    public void onToggle() {
                        if (!textField.getText().isEmpty() && !textField.getText().equals(contract.name))
                            contract.name = textField.getText();

                        if (contract.scope == ShippingContract.Scope.SPECIFIC_CARGO){
                            contract.scope = ShippingContract.Scope.EVERYTHING;
                            contract.clearTargetCargo();
                            showPanel(dialogue, contract);

                        } else if (contract.scope == ShippingContract.Scope.SPECIFIC_EVERYTHING){
                            contract.scope = ShippingContract.Scope.SPECIFIC_SHIPS;
                            contract.clearTargetCargo();
                            showPanel(dialogue, contract);

                        } else if (contract.scope == ShippingContract.Scope.SPECIFIC_SHIPS){
                            contract.scope = ShippingContract.Scope.SPECIFIC_EVERYTHING;

                            VisualCustomPanel.clearPanel();
                            new SubmarketCargoPicker(dialogue, contract);
                        } else {
                            contract.scope = ShippingContract.Scope.SPECIFIC_CARGO;

                            VisualCustomPanel.clearPanel();
                            new SubmarketCargoPicker(dialogue, contract);
                        }
                    }
                };

                VisualCustomPanel.getPlugin().addButton(entry);
                scopeSelectionPanel.addUIElement(anchor).rightOfMid(lastUsedVariableButtonAnchor, opad);        //second in row
                lastUsedVariableButtonAnchor = anchor;
            }

            if (showSips) {
                anchor = scopeSelectionPanel.createUIElement(SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, false);
                name = "Select Ships";
                scope = ShippingContract.Scope.SPECIFIC_SHIPS;
                buttonId = "button_scope_" + scope.toString() + id;

                areaCheckbox = anchor.addAreaCheckbox(name, new Object(), baseColor, bgColour, brightColor,
                        SELECT_BUTTON_WIDTH,
                        BUTTON_HEIGHT,
                        0f,
                        false);

                areaCheckbox.setChecked(contract.scope == scope || contract.scope == ShippingContract.Scope.SPECIFIC_EVERYTHING);

                entry = new InteractionDialogCustomPanelPlugin.ButtonEntry(areaCheckbox, buttonId) {
                    @Override
                    public void onToggle() {
                        if (!textField.getText().isEmpty() && !textField.getText().equals(contract.name))
                            contract.name = textField.getText();

                        if (contract.scope == ShippingContract.Scope.SPECIFIC_SHIPS){
                            contract.scope = ShippingContract.Scope.EVERYTHING;
                            contract.clearTargetShips();
                            showPanel(dialogue, contract);

                        } else if (contract.scope == ShippingContract.Scope.SPECIFIC_EVERYTHING){
                            contract.scope = ShippingContract.Scope.SPECIFIC_CARGO;
                            contract.clearTargetShips();
                            showPanel(dialogue, contract);

                        } else if (contract.scope == ShippingContract.Scope.SPECIFIC_CARGO){
                            contract.scope = ShippingContract.Scope.SPECIFIC_EVERYTHING;

                            VisualCustomPanel.clearPanel();
                            new SubmarketShipPicker(dialogue, contract);
                        } else {
                            contract.scope = ShippingContract.Scope.SPECIFIC_SHIPS;

                            VisualCustomPanel.clearPanel();
                            new SubmarketShipPicker(dialogue, contract);
                        }
                    }
                };

                VisualCustomPanel.getPlugin().addButton(entry);
                scopeSelectionPanel.addUIElement(anchor).rightOfMid(lastUsedVariableButtonAnchor, opad);        //second in row
                lastUsedVariableButtonAnchor = anchor;
            }

            panelTooltip.addCustom(scopeSelectionPanel, 0f); //add panel

        }

        //CONFIRM

        panelTooltip.addSectionHeading("Confirmation", Alignment.MID, opad);

        CustomPanelAPI confirmPanel = panel.createCustomPanel(PANEL_WIDTH_1, 40f, new NoFrameCustomPanelPlugin());

        anchor = confirmPanel.createUIElement(SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, false);
        buttonId = "button_return_" + id;
        baseColor = Misc.getTextColor();
        bgColour = new Color(80, 20, 10, 255);

        button = anchor.addButton("Cancel", buttonId, baseColor, bgColour, Alignment.MID, CutStyle.C2_MENU, SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, 0);
        entry = new InteractionDialogCustomPanelPlugin.ButtonEntry(button, buttonId) {
            @Override
            public void onToggle() {
                if (!textField.getText().isEmpty() && !textField.getText().equals(contract.name))
                    contract.name = textField.getText();

                ShippingContract original = getContractBackup();
                clearBackup();
                if (original != null) ShippingContractMemory.addOrReplaceContract(original);

                if(!ShippingContractMemory.getContractList().isEmpty()) new ContractListSidePanelCreator().showPanel(dialogue);
                else VisualCustomPanel.clearPanel();

                CourierPortDialoguePlugin.reload();
            }
        };

        VisualCustomPanel.getPlugin().addButton(entry);
        confirmPanel.addUIElement(anchor).inTL(spad, opad);         //second in row
        lastUsedVariableButtonAnchor = anchor;

        prerequisiteForActive = contract.isValid();

        anchor = confirmPanel.createUIElement(SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, false);
        buttonId = "button_confirm_" + id;
        bgColour = prerequisiteForActive ? new Color(50, 130, 0, 255) : Misc.getGrayColor();

        button = anchor.addButton("Confirm", buttonId, baseColor, bgColour, Alignment.MID, CutStyle.C2_MENU, SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, 0);
        button.setEnabled(prerequisiteForActive);

        entry = new InteractionDialogCustomPanelPlugin.ButtonEntry(button, buttonId) {
            @Override
            public void onToggle() {
                if (!textField.getText().isEmpty() && !textField.getText().equals(contract.name))
                    contract.name = textField.getText();

                clearBackup();
                ShippingContractMemory.addOrReplaceContract(contract);
                if(!ShippingContractMemory.getContractList().isEmpty()) new ContractListSidePanelCreator().showPanel(dialogue);
                else VisualCustomPanel.clearPanel();

                CourierPortDialoguePlugin.reload();
            }
        };

        VisualCustomPanel.getPlugin().addButton(entry);
        confirmPanel.addUIElement(anchor).rightOfMid(lastUsedVariableButtonAnchor, opad);
        lastUsedVariableButtonAnchor = anchor;

        panelTooltip.addCustom(confirmPanel, 0f); //add panel

        //SUMMARY

        panelTooltip.addSectionHeading("Contract Summary", Alignment.MID, opad);

        SubmarketAPI fromSubmarket = contract.getFromSubmarket();
        SubmarketAPI toSubmarket = contract.getToSubmarket();
        String cadenceString = ShippingTooltipHelper.getCadenceString(contract.getRecurrentDays());
        float lyMultVal = ShippingCostCalculator.getLYMult(contract);
        String lyMultStr = Misc.getRoundedValueMaxOneAfterDecimal(lyMultVal);
        float shipCost = ShippingCostCalculator.getContractShipCost(contract);
        float cargoCost = ShippingCostCalculator.getContractCargoCost(contract);
        float total = ShippingCostCalculator.getTotalContractCost(contract);
        float pad = 5f;

        panelTooltip.setParaFont(Fonts.DEFAULT_SMALL);

        if (fromMarket != null) panelTooltip.addPara("From: %s" + (fromSubmarket != null ? ", %s" : ""),
                pad,
                contract.getFromMarket().getTextColorForFactionOrPlanet(),
                fromMarket.getName(),
                fromSubmarket != null ? fromSubmarket.getNameOneLine() : "");

        if (toMarket != null) panelTooltip.addPara("To: %s" + (toSubmarket != null ? ", %s" : ""),
                pad,
                contract.getToMarket().getTextColorForFactionOrPlanet(),
                toMarket.getName(),
                toSubmarket != null ? toSubmarket.getNameOneLine() : "");

        if(fromMarket != null && toMarket != null && fromSubmarket != null && toSubmarket != null){
            boolean ships = contract.scope != ShippingContract.Scope.ALL_CARGO && contract.scope != ShippingContract.Scope.SPECIFIC_CARGO;
            boolean cargo = contract.scope != ShippingContract.Scope.ALL_SHIPS && contract.scope != ShippingContract.Scope.SPECIFIC_SHIPS;
            String and = ships && cargo ? " and %s" : "";
            String shipStr = ShippingTooltipHelper.getShipAmtString(contract);
            String cargoStr = ShippingTooltipHelper.getCargoAmtString(contract);

            String firstHL = ships ? Misc.ucFirst(shipStr) : Misc.ucFirst(cargoStr);
            String secondHL = ships && cargo ? Misc.ucFirst(cargoStr) : "";

            panelTooltip.addPara("%s" + and, spad, Misc.getHighlightColor(), firstHL, secondHL);
        }

        panelTooltip.addPara("Cadence: " + (contract.getRecurrentDays() > 0 ? "every %s" : "%s"), pad, Misc.getHighlightColor(), cadenceString);

        if (fromMarket != null && toMarket != null) {
            String alphaCoreStr = ShippingTargetHelper.getMemoryAICoreId().equals(Commodities.ALPHA_CORE) ? " [-" + IndEvo_StringHelper.getAbsPercentString(ShippingCostCalculator.TOTAL_FEE_REDUCTION, true) + ", Alpha Core]" : "";

            panelTooltip.addPara("Cost forecast:", opad);
            panelTooltip.beginGridFlipped(300, 1, 100f, 3f);
            panelTooltip.addToGrid(0, 0, "Base fee", Misc.getDGSCredits(CONTRACT_BASE_FEE));

            if(shipCost > 10 && (contract.scope == ShippingContract.Scope.SPECIFIC_CARGO || contract.scope == ShippingContract.Scope.SPECIFIC_EVERYTHING))
                panelTooltip.addToGrid(0,
                        1,
                        "Cargo transport",
                        Misc.getDGSCredits(cargoCost) + alphaCoreStr);
            else panelTooltip.addToGrid(0,
                    1,
                    "Cargo cost per 1000 items",
                    Misc.getDGSCredits(ShippingCostCalculator.getCostForCargoSpace(1000, lyMultVal)) + alphaCoreStr);

            if(shipCost > 10 && (contract.scope == ShippingContract.Scope.SPECIFIC_SHIPS || contract.scope == ShippingContract.Scope.SPECIFIC_EVERYTHING ))
                panelTooltip.addToGrid(0,
                        2,
                        "Ships transport",
                        Misc.getDGSCredits(shipCost) + alphaCoreStr);
            else panelTooltip.addToGrid(0,
                    2,
                    "Ship cost per 10 DP",
                    Misc.getDGSCredits(ShippingCostCalculator.getCostForShipSpace(10, lyMultVal)) + alphaCoreStr);

            String betaCoreStr = ShippingTargetHelper.getMemoryAICoreId().equals(Commodities.BETA_CORE) ? " [-" + IndEvo_StringHelper.getAbsPercentString(ShippingCostCalculator.DISTANCE_MULT_REDUCTION, true) + ", Beta Core]" : "";
            panelTooltip.addToGrid(0, 3, "Distance multiplier", "x" + lyMultStr + betaCoreStr);

            if(fromSubmarket != null && fromSubmarket.getSpecId().equals(Submarkets.LOCAL_RESOURCES)){
                float stockpileCost = 0f;

                if(contract.scope == ShippingContract.Scope.SPECIFIC_CARGO){
                    for (CargoStackAPI stack : contract.targetCargo.getStacksCopy()){
                        stockpileCost += stack.getBaseValuePerUnit() * stack.getSize();
                    }
                } else {
                    for (CargoStackAPI stack : fromSubmarket.getCargo().getStacksCopy()){
                        stockpileCost += stack.getBaseValuePerUnit() * stack.getSize();
                    }
                }

                panelTooltip.addToGrid(0, 4, "Stockpile item cost", Misc.getDGSCredits(stockpileCost));
                total += stockpileCost;
            }

            if(contract.scope.toString().toLowerCase().contains("specific")) panelTooltip.addToGrid(0, 5, "Total", Misc.getDGSCredits(total));

            panelTooltip.addGrid(pad);
        } else panelTooltip.addPara("Cost forecast available after planet selection.", pad);

        panelTooltip.setParaFontDefault();

        // FLAVOUR TEXT
        //if(!contract.isValid()) panelTooltip.addPara("Contract invalid: " + ShippingTooltipHelper.getInvalidReason(contract), opad, Misc.getNegativeHighlightColor());

        VisualCustomPanel.addTooltipToPanel();
        CourierPortDialoguePlugin.reload();
    }
}
