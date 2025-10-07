package indevo.industries.warehouses.plugin.wi;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import indevo.dialogue.sidepanel.ButtonAction;
import indevo.dialogue.sidepanel.ButtonReportingCustomPanel;
import indevo.dialogue.sidepanel.ButtonReportingDialogueDelegate;
import indevo.industries.museum.data.MuseumConstants;
import indevo.industries.museum.data.ParadeFleetProfile;
import indevo.industries.museum.fleet.ParadeFleetAssignmentAI;
import indevo.industries.museum.plugins.ManageParadeDialoguePlugin;
import indevo.industries.warehouses.industry.Warehouses;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static indevo.industries.museum.data.MuseumConstants.FLEET_RETURNING_TAG;

class WarehouseManagementDialogueDelegate extends ButtonReportingDialogueDelegate {

    public static final float WIDTH = 600f;
    public static final float HEIGHT = Global.getSettings().getScreenHeight() - 300f;
    protected static final float BUTTON_30 = 28, BUTTON_80 = 80, BUTTON_120 = 120, BUTTON_150 = 150f, BUTTON_100 = 100f;

    public static final float ENTRY_HEIGHT = 166; //MUST be even
    public static final float ENTRY_WIDTH = WIDTH - 10f; //MUST be even

    private final Warehouses warehouses;
    private CustomPanelAPI basePanel;
    private CustomPanelAPI panel;
    private ScrollPanelAPI externalScroller;

    public WarehouseManagementDialogueDelegate(Warehouses warehouses) {
        this.warehouses = warehouses;
    }

    public void createCustomDialog(CustomPanelAPI panel, final CustomDialogCallback callback) {
        this.basePanel = panel;
        recreatePanel(callback);
    }

    public void recreatePanel(final CustomDialogCallback callback) {
        // capture current scroll before tearing down
        float savedYOffset = 0f;
        if (externalScroller != null) savedYOffset = externalScroller.getYOffset();

        if (panel != null) basePanel.removeComponent(panel);

        panel = Global.getSettings().createCustom(basePanel.getPosition().getWidth(), basePanel.getPosition().getHeight(), null);

        float opad = 10f;
        float spad = 5f;

        Color baseColor = Misc.getButtonTextColor();
        Color bgColour = Misc.getDarkPlayerColor();
        Color brightColor = Misc.getBrightPlayerColor();
        Color grayColour = new Color(110, 110, 110, 255);

        //--------- Add basic on/off option ---------

        //heading
        TooltipMakerAPI bottomPanel = panel.createUIElement(WIDTH, HEIGHT, false);
        bottomPanel.addSectionHeading("General configuration", Alignment.MID, opad);

        //button panel
        CustomPanelAPI selectorPanel = panel.createCustomPanel(WIDTH, BUTTON_30 + opad, new ButtonReportingCustomPanel(this));
        TooltipMakerAPI selectorAnchor = selectorPanel.createUIElement(BUTTON_150, BUTTON_30, false);

        ButtonAPI linkLocalStorageButton = selectorAnchor.addAreaCheckbox("Link main storage", new ButtonAction(this) {
            @Override
            public void execute() {
                warehouses.setFlyParades(true);
                recreatePanel(callback);
            }
        }, baseColor, bgColour, brightColor, BUTTON_150, BUTTON_30, 0);

        linkLocalStorageButton.setEnabled(warehouses.canLinkLocalStorage());
        linkLocalStorageButton.setChecked(warehouses.localStorageLinked());

        selectorAnchor.addTooltipTo(new TooltipMakerAPI.TooltipCreator() {
            @Override
            public boolean isTooltipExpandable(Object tooltipParam) {
                return false;
            }

            @Override
            public float getTooltipWidth(Object tooltipParam) {
                return 100f;
            }

            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {

            }
        }, linkLocalStorageButton, TooltipMakerAPI.TooltipLocation.ABOVE);

        selectorPanel.addUIElement(selectorAnchor).inLMid(-opad); //if we don't -opad it kinda does it by its own, no clue why
        TooltipMakerAPI lastUsedAnchor = selectorAnchor;

        selectorAnchor = selectorPanel.createUIElement(BUTTON_150, BUTTON_30, false);

        ButtonAPI showInCargoButton = selectorAnchor.addAreaCheckbox("Disable Parades", new ButtonAction(this) {
            @Override
            public void execute() {
                warehouses.setFlyParades(false);
                recreatePanel(callback);
            }
        }, baseColor, bgColour, brightColor, BUTTON_150, BUTTON_30, 0);

        showInCargoButton.setChecked(!warehouses.flyParades());

        selectorPanel.addUIElement(selectorAnchor).rightOfMid(lastUsedAnchor, opad);
        lastUsedAnchor = selectorAnchor;

        selectorAnchor = selectorPanel.createUIElement(BUTTON_150, BUTTON_30, false);

        boolean enabled = warehouses.getParadeFleetProfiles().size() < 100;
        Color buttonBgColour = enabled ? MuseumConstants.SUBMARKET_COLOUR : grayColour;
        Color buttonTextColour = Color.WHITE;

        ButtonAPI addProfileButton = selectorAnchor.addButton("+ add new profile", new ButtonAction(this) {
            @Override
            public void execute() {
                warehouses.getParadeFleetProfiles().add(new ParadeFleetProfile(warehouses));
                recreatePanel(callback);
            }
        }, buttonTextColour, buttonBgColour, Alignment.MID, CutStyle.ALL, BUTTON_150, BUTTON_30, 0);

        addProfileButton.setEnabled(enabled);

        selectorPanel.addUIElement(selectorAnchor).rightOfMid(lastUsedAnchor, WIDTH - BUTTON_150 * 3 - opad);

        bottomPanel.addCustom(selectorPanel, opad);

        //next line, new panel
        selectorPanel = panel.createCustomPanel(WIDTH, BUTTON_30 * 2, new ButtonReportingCustomPanel(this));
        TooltipMakerAPI textPanel = selectorPanel.createUIElement(WIDTH, BUTTON_30, false);

        textPanel.addSectionHeading("Parade profiles", Alignment.MID, opad);
        textPanel.addPara("Parade profiles are used to configure your parades. A random one will be picked if there are more enabled profiles than there are fleet slots (currently: " + warehouses.getMaxParades() + ").", opad);
        selectorPanel.addUIElement(textPanel).inTL(-5f, 0f);
        lastUsedAnchor = selectorAnchor;

        bottomPanel.addCustom(selectorPanel, opad);

        //parade fleet profile list
        float height = HEIGHT - 160f;

        selectorPanel = panel.createCustomPanel(WIDTH, height, new ButtonReportingCustomPanel(this));
        selectorAnchor = selectorPanel.createUIElement(WIDTH, height, true);
/*
        if (externalScroller != null) {
            selectorAnchor.setExternalScroller(externalScroller);
            externalScroller.setYOffset(listScrollY);
        }*/

        //--------------- PROFILES ---------------

        //add the profile list

        for (ParadeFleetProfile profile : warehouses.getParadeFleetProfiles()) {
            CustomPanelAPI entryPanel = panel.createCustomPanel(ENTRY_WIDTH - spad, ENTRY_HEIGHT, new ButtonReportingCustomPanel(this, baseColor, -1));

            //header

            String profileName = profile.getNamePreset();
            Color headerColour = grayColour;
            if (profile.isEnabled()) headerColour = bgColour;
            if (profile.hasActiveFleet()) headerColour = MuseumConstants.SUBMARKET_COLOUR;

            Color textColour = Color.WHITE;
            if (profile.isEnabled()) textColour = baseColor;
            if (profile.hasActiveFleet()) textColour = Color.WHITE;

            String activity = profile.isEnabled() ? "- Enabled" : "- Disabled";
            if (profile.getCurrentFleet() != null) {
                activity = "- Fleet on parade";
                if (profile.getCurrentFleet().hasTag(FLEET_RETURNING_TAG)) activity = "- Fleet returning";
            }

            TooltipMakerAPI entryAnchor = entryPanel.createUIElement(ENTRY_WIDTH - spad, BUTTON_30, false);
            entryAnchor.addSectionHeading("Profile - " + (profileName == null ? " Random Name" : profileName) + " " + activity, textColour, headerColour, Alignment.MID, opad);

            entryPanel.addUIElement(entryAnchor).inTL(0, 0);
            lastUsedAnchor = entryAnchor;

            //name

            TooltipMakerAPI textAnchor = entryPanel.createUIElement(BUTTON_100, BUTTON_30, false);
            textAnchor.addPara("Fleet Name: ", spad);//.getPosition().inLMid(opad);

            entryPanel.addUIElement(textAnchor).belowLeft(lastUsedAnchor, 0f);
            TooltipMakerAPI lastTextAnchor = textAnchor;
            lastUsedAnchor = textAnchor;

            String text = profileName == null ? "Random" : "Custom:";

            TooltipMakerAPI buttonAnchor = entryPanel.createUIElement(BUTTON_80, BUTTON_30, false);
            ButtonAPI nameModeBox = buttonAnchor.addAreaCheckbox(text, new ButtonAction(this) {
                @Override
                public void execute() {
                    if (profile.getNamePreset() != null) profile.resetNamePreset();
                    else profile.setNamePreset("Custom Name");
                    recreatePanel(callback);
                }
            }, baseColor, bgColour, brightColor, BUTTON_80, BUTTON_30, 0);

            entryPanel.addUIElement(buttonAnchor).rightOfMid(lastUsedAnchor, opad);
            lastUsedAnchor = buttonAnchor;

            if (profileName != null) {
                float width = BUTTON_150 * 2;
                TooltipMakerAPI nameFieldAnchor = entryPanel.createUIElement(width, BUTTON_30, false);
                TextFieldAPI nameField = nameFieldAnchor.addTextField(width, opad);
                nameField.setHandleCtrlV(true);
                nameField.showCursor();
                nameField.setText(profileName);

                entryPanel.addUIElement(nameFieldAnchor).rightOfBottom(lastUsedAnchor, opad);
                lastUsedAnchor = nameFieldAnchor;

                buttonAnchor = entryPanel.createUIElement(60f, BUTTON_30, false);
                ButtonAPI saveButton = buttonAnchor.addButton("Save", new ButtonAction(this) {
                    @Override
                    public void execute() {
                        profile.setNamePreset(nameField.getText());
                        recreatePanel(callback);
                    }
                }, baseColor, bgColour, Alignment.MID, CutStyle.ALL, 60f, BUTTON_30, 0);

                entryPanel.addUIElement(buttonAnchor).rightOfBottom(lastUsedAnchor, opad);
            }

            //Loadout

            textAnchor = entryPanel.createUIElement(BUTTON_100, BUTTON_30, false);
            textAnchor.addPara("Ships: ", spad);//.getPosition().inLMid(opad);

            entryPanel.addUIElement(textAnchor).belowLeft(lastTextAnchor, spad);
            lastTextAnchor = textAnchor;
            lastUsedAnchor = textAnchor;

            text = profile.getMemberIdPreset() == null ? "Random" : "Custom:";

            buttonAnchor = entryPanel.createUIElement(BUTTON_80, BUTTON_30, false);
            buttonAnchor.addAreaCheckbox(text, new ButtonAction(this) {
                @Override
                public void execute() {
                    if (profile.getMemberIdPreset() != null) profile.resetMembers();
                    else profile.setMemberList(new ArrayList<>());
                    recreatePanel(callback);
                }
            }, baseColor, bgColour, brightColor, BUTTON_80, BUTTON_30, 0);

            entryPanel.addUIElement(buttonAnchor).rightOfMid(lastUsedAnchor, opad);
            lastUsedAnchor = buttonAnchor;

            if (profile.getMemberIdPreset() != null) {
                buttonAnchor = entryPanel.createUIElement(BUTTON_80, BUTTON_30, false);
                buttonAnchor.addButton("Edit", new ButtonAction(this) {
                    @Override
                    public void execute() {
                        dismissOnNextCancel = false;
                        callback.dismissCustomDialog(1);

                        ((ManageParadeDialoguePlugin) dialog).showShipPicker(profile);

                    }
                }, baseColor, bgColour, Alignment.MID, CutStyle.ALL, BUTTON_80, BUTTON_30, 0);

                entryPanel.addUIElement(buttonAnchor).rightOfBottom(lastUsedAnchor, opad);
                lastUsedAnchor = buttonAnchor;

                float width = BUTTON_150 * 4;
                TooltipMakerAPI fleetMemberDisplayElement = entryPanel.createUIElement(width, BUTTON_30, false);

                SubmarketAPI sub = warehouses.getSubmarket();
                if (sub == null) {
                    warehouses.advance(0);
                    sub = warehouses.getSubmarket();
                }

                CargoAPI cargo = sub.getCargo();
                cargo.initMothballedShips(Factions.PLAYER);
                List<FleetMemberAPI> paradeMembers = new ArrayList<>();
                for (FleetMemberAPI m : cargo.getMothballedShips().getMembersListCopy())
                    if (profile.getMemberIdPreset().contains(m.getId())) paradeMembers.add(m);

                fleetMemberDisplayElement.addShipList(10, 1, BUTTON_30, baseColor, paradeMembers, 0f);

                entryPanel.addUIElement(fleetMemberDisplayElement).rightOfBottom(lastUsedAnchor, opad);
                lastUsedAnchor = fleetMemberDisplayElement;

            }

            //duration

            textAnchor = entryPanel.createUIElement(BUTTON_100, BUTTON_30, false);
            textAnchor.addPara("Duration: ", spad);//.getPosition().inLMid(opad);

            entryPanel.addUIElement(textAnchor).belowLeft(lastTextAnchor, spad);
            lastTextAnchor = textAnchor;
            lastUsedAnchor = textAnchor;

            int current = profile.getDurationPreset();
            float width = BUTTON_80;

            final int buttonDays1 = MuseumConstants.PARADE_DAY_OPTIONS[0];
            buttonAnchor = entryPanel.createUIElement(width, BUTTON_30, false);
            int months = Math.round((float) buttonDays1 / 31);
            String monthOrMonths = months > 1 ? " months" : " month";
            ButtonAPI button = buttonAnchor.addAreaCheckbox(months + monthOrMonths, new ButtonAction(this) {
                @Override
                public void execute() {
                    profile.setDuration(buttonDays1);
                    recreatePanel(callback);
                }
            }, baseColor, bgColour, brightColor, width, BUTTON_30, 0);
            button.setChecked(current == buttonDays1);

            entryPanel.addUIElement(buttonAnchor).rightOfBottom(lastUsedAnchor, opad);
            lastUsedAnchor = buttonAnchor;

            final int buttonDays2 = MuseumConstants.PARADE_DAY_OPTIONS[1];
            buttonAnchor = entryPanel.createUIElement(width, BUTTON_30, false);
            months = Math.round((float) buttonDays2 / 31);
            monthOrMonths = months > 1 ? " months" : " month";
            button = buttonAnchor.addAreaCheckbox(months + monthOrMonths, new ButtonAction(this) {
                @Override
                public void execute() {
                    profile.setDuration(buttonDays2);
                    recreatePanel(callback);
                }
            }, baseColor, bgColour, brightColor, width, BUTTON_30, 0);
            button.setChecked(current == buttonDays2);

            entryPanel.addUIElement(buttonAnchor).rightOfMid(lastUsedAnchor, opad);
            lastUsedAnchor = buttonAnchor;

            final int buttonDays3 = MuseumConstants.PARADE_DAY_OPTIONS[2];
            buttonAnchor = entryPanel.createUIElement(width, BUTTON_30, false);
            months = Math.round((float) buttonDays3 / 31);
            monthOrMonths = months > 1 ? " months" : " month";
            button = buttonAnchor.addAreaCheckbox(months + monthOrMonths, new ButtonAction(this) {
                @Override
                public void execute() {
                    profile.setDuration(buttonDays3);
                    recreatePanel(callback);
                }
            }, baseColor, bgColour, brightColor, width, BUTTON_30, 0);
            button.setChecked(current == buttonDays3);

            entryPanel.addUIElement(buttonAnchor).rightOfMid(lastUsedAnchor, opad);
            lastUsedAnchor = buttonAnchor;

            final int buttonDays4 = MuseumConstants.PARADE_DAY_OPTIONS[3];
            buttonAnchor = entryPanel.createUIElement(width, BUTTON_30, false);
            months = Math.round((float) buttonDays4 / 31);
            monthOrMonths = months > 1 ? " months" : " month";
            button = buttonAnchor.addAreaCheckbox(months + monthOrMonths, new ButtonAction(this) {
                @Override
                public void execute() {
                    profile.setDuration(buttonDays4);
                    recreatePanel(callback);
                }
            }, baseColor, bgColour, brightColor, width, BUTTON_30, 0);
            button.setChecked(current == buttonDays4);

            entryPanel.addUIElement(buttonAnchor).rightOfMid(lastUsedAnchor, opad);
            lastUsedAnchor = buttonAnchor;

            //options: Recall - disable - delete

            textAnchor = entryPanel.createUIElement(BUTTON_100, BUTTON_30, false);
            textAnchor.addPara("Options: ", spad);//.getPosition().inLMid(opad);

            entryPanel.addUIElement(textAnchor).belowLeft(lastTextAnchor, spad);
            lastTextAnchor = textAnchor;
            lastUsedAnchor = textAnchor;

            enabled = profile.getCurrentFleet() != null && !profile.getCurrentFleet().hasTag(FLEET_RETURNING_TAG);
            buttonBgColour = enabled ? new Color(140, 70, 20, 255) : grayColour;
            buttonTextColour = Color.WHITE;

            buttonAnchor = entryPanel.createUIElement(BUTTON_120, BUTTON_30, false);
            button = buttonAnchor.addButton("Recall Parade", new ButtonAction(this) {
                @Override
                public void execute() {
                    ParadeFleetAssignmentAI.get(profile.getCurrentFleet()).forceReturnToSource();
                    recreatePanel(callback);
                }
            }, buttonTextColour, buttonBgColour, Alignment.MID, CutStyle.ALL, BUTTON_120, BUTTON_30, 0);

            button.setEnabled(enabled);

            entryPanel.addUIElement(buttonAnchor).rightOfBottom(lastUsedAnchor, opad);
            lastUsedAnchor = buttonAnchor;

            int enabledProfiles = 0;
            for (ParadeFleetProfile p : warehouses.getParadeFleetProfiles()) if (p.isEnabled()) enabledProfiles++;

            enabled = !profile.isEnabled() || enabledProfiles > warehouses.getMaxParades(); //we forbid disabling profiles if the player has equal or less than the required amount of fleet slots
            Color buttonLightColour = enabled ? brightColor : Color.lightGray;
            buttonBgColour = enabled ? bgColour : grayColour;
            buttonTextColour = enabled ? baseColor : Color.WHITE;

            buttonAnchor = entryPanel.createUIElement(BUTTON_120, BUTTON_30, false);
            button = buttonAnchor.addAreaCheckbox((profile.isEnabled() ? "Disable" : "Enable") + " profile", new ButtonAction(this) {
                @Override
                public void execute() {
                    profile.setEnabled(!profile.isEnabled());
                    recreatePanel(callback);
                }
            }, buttonTextColour, buttonBgColour, buttonLightColour, BUTTON_120, BUTTON_30, 0);

            button.setEnabled(enabled);
            button.setChecked(profile.isEnabled());

            entryPanel.addUIElement(buttonAnchor).rightOfMid(lastUsedAnchor, opad);
            lastUsedAnchor = buttonAnchor;

            enabled = warehouses.getParadeFleetProfiles().size() > warehouses.getMaxParades() && profile.getCurrentFleet() == null;
            buttonBgColour = enabled ? new Color(80, 20, 10, 255) : grayColour;
            buttonTextColour = Color.WHITE;

            buttonAnchor = entryPanel.createUIElement(BUTTON_120, BUTTON_30, false);
            button = buttonAnchor.addButton("Delete profile", new ButtonAction(this) {
                @Override
                public void execute() {
                    warehouses.getParadeFleetProfiles().remove(profile);
                    recreatePanel(callback);
                }
            }, buttonTextColour, buttonBgColour, Alignment.MID, CutStyle.ALL, BUTTON_120, BUTTON_30, 0);

            button.setEnabled(enabled);

            entryPanel.addUIElement(buttonAnchor).rightOfMid(lastUsedAnchor, opad);
            lastUsedAnchor = buttonAnchor;


            //finalize

            selectorAnchor.addCustom(entryPanel, spad);
        }

        selectorPanel.addUIElement(selectorAnchor).inTL(-opad, 0f); //if we don't -opad it kinda does it by its own, no clue why
        bottomPanel.addCustom(selectorPanel, opad);

        panel.addUIElement(bottomPanel).inTL(0f, 0.0F);
        basePanel.addComponent(panel);

        // 3) fetch new scroller from the rebuilt UI
        ScrollPanelAPI newScroller = selectorAnchor.getExternalScroller();

        // 4) apply saved offset after layout
        if (newScroller != null) {
            newScroller.setYOffset(savedYOffset);
        }

        // 5) store for the next rebuild
        externalScroller = newScroller;
    }

    @Override
    public String getConfirmText() {
        return "Return";
    }

    @Override
    public void customDialogConfirm() {
        super.customDialogConfirm();
        dialog.close();
    }

    public void customDialogCancel() {
        if (dismissOnNextCancel) dialog.close();
        else dismissOnNextCancel = true;
    }
}
