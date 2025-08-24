package indevo.industries.museum.plugins;

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
import indevo.dialogue.sidepanel.EscapeBlockingDialoguePluginAPI;
import indevo.industries.museum.data.MuseumConstants;
import indevo.industries.museum.data.ParadeFleetProfile;
import indevo.industries.museum.fleet.ParadeFleetAssignmentAI;
import indevo.industries.museum.industry.Museum;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static indevo.industries.museum.data.MuseumConstants.FLEET_RETURNING_TAG;

class ParadeManagementDialogueDelegate extends ButtonReportingDialogueDelegate {

    private final Museum museum;
    private CustomPanelAPI basePanel;
    private CustomPanelAPI panel;
    private ScrollPanelAPI externalScroller;

    public EscapeBlockingDialoguePluginAPI dialog;
    boolean dismissOnNextCancel = true;

    public ParadeManagementDialogueDelegate(Museum museum, EscapeBlockingDialoguePluginAPI dialog) {
        this.museum = museum;
        this.dialog = dialog;
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
        TooltipMakerAPI bottomPanel = panel.createUIElement(MuseumManageParadeOptionProvider.WIDTH, MuseumManageParadeOptionProvider.HEIGHT, false);
        bottomPanel.addSectionHeading("Parade configuration", Alignment.MID, opad);

        //button panel
        CustomPanelAPI selectorPanel = panel.createCustomPanel(MuseumManageParadeOptionProvider.WIDTH, MuseumManageParadeOptionProvider.BUTTON_30 + opad, new ButtonReportingCustomPanel(this));
        TooltipMakerAPI selectorAnchor = selectorPanel.createUIElement(MuseumManageParadeOptionProvider.BUTTON_150, MuseumManageParadeOptionProvider.BUTTON_30, false);

        ButtonAPI paradeOnButton = selectorAnchor.addAreaCheckbox("Enable Parades", new ButtonAction(this) {
            @Override
            public void execute() {
                museum.setFlyParades(true);
                recreatePanel(callback);
            }
        }, baseColor, bgColour, brightColor, MuseumManageParadeOptionProvider.BUTTON_150, MuseumManageParadeOptionProvider.BUTTON_30, 0);

        paradeOnButton.setChecked(museum.flyParades());

        selectorPanel.addUIElement(selectorAnchor).inLMid(-opad); //if we don't -opad it kinda does it by its own, no clue why
        TooltipMakerAPI lastUsedAnchor = selectorAnchor;

        selectorAnchor = selectorPanel.createUIElement(MuseumManageParadeOptionProvider.BUTTON_150, MuseumManageParadeOptionProvider.BUTTON_30, false);

        ButtonAPI showInCargoButton = selectorAnchor.addAreaCheckbox("Disable Parades", new ButtonAction(this) {
            @Override
            public void execute() {
                museum.setFlyParades(false);
                recreatePanel(callback);
            }
        }, baseColor, bgColour, brightColor, MuseumManageParadeOptionProvider.BUTTON_150, MuseumManageParadeOptionProvider.BUTTON_30, 0);

        showInCargoButton.setChecked(!museum.flyParades());

        selectorPanel.addUIElement(selectorAnchor).rightOfMid(lastUsedAnchor, opad);
        lastUsedAnchor = selectorAnchor;

        selectorAnchor = selectorPanel.createUIElement(MuseumManageParadeOptionProvider.BUTTON_150, MuseumManageParadeOptionProvider.BUTTON_30, false);

        boolean enabled = museum.getParadeFleetProfiles().size() < (MuseumConstants.DEFAULT_MAX_PARADES + MuseumConstants.ALPHA_CORE_EXTRA_PARADES) * 3f;
        Color buttonBgColour = enabled ? MuseumConstants.SUBMARKET_COLOUR : grayColour;
        Color buttonTextColour = Color.WHITE;

        ButtonAPI addProfileButton = selectorAnchor.addButton("+ add new profile", new ButtonAction(this) {
            @Override
            public void execute() {
                museum.getParadeFleetProfiles().add(new ParadeFleetProfile(museum));
                recreatePanel(callback);
            }
        }, buttonTextColour, buttonBgColour, Alignment.MID, CutStyle.ALL, MuseumManageParadeOptionProvider.BUTTON_150, MuseumManageParadeOptionProvider.BUTTON_30, 0);

        addProfileButton.setEnabled(enabled);

        selectorPanel.addUIElement(selectorAnchor).rightOfMid(lastUsedAnchor, MuseumManageParadeOptionProvider.WIDTH - MuseumManageParadeOptionProvider.BUTTON_150 * 3 - opad);

        bottomPanel.addCustom(selectorPanel, opad);

        //next line, new panel
        selectorPanel = panel.createCustomPanel(MuseumManageParadeOptionProvider.WIDTH, MuseumManageParadeOptionProvider.BUTTON_30 * 2, new ButtonReportingCustomPanel(this));
        TooltipMakerAPI textPanel = selectorPanel.createUIElement(MuseumManageParadeOptionProvider.WIDTH, MuseumManageParadeOptionProvider.BUTTON_30, false);

        textPanel.addSectionHeading("Parade profiles", Alignment.MID, opad);
        textPanel.addPara("Parade profiles are used to configure your parades. A random one will be picked if there are more enabled profiles than there are fleet slots (currently: " + museum.getMaxParades() + ").", opad);
        selectorPanel.addUIElement(textPanel).inTL(-5f, 0f);
        lastUsedAnchor = selectorAnchor;

        bottomPanel.addCustom(selectorPanel, opad);

        //parade fleet profile list
        float height = MuseumManageParadeOptionProvider.HEIGHT - 160f;

        selectorPanel = panel.createCustomPanel(MuseumManageParadeOptionProvider.WIDTH, height, new ButtonReportingCustomPanel(this));
        selectorAnchor = selectorPanel.createUIElement(MuseumManageParadeOptionProvider.WIDTH, height, true);
/*
        if (externalScroller != null) {
            selectorAnchor.setExternalScroller(externalScroller);
            externalScroller.setYOffset(listScrollY);
        }*/

        //--------------- PROFILES ---------------

        //add the profile list

        for (ParadeFleetProfile profile : museum.getParadeFleetProfiles()) {
            CustomPanelAPI entryPanel = panel.createCustomPanel(MuseumManageParadeOptionProvider.ENTRY_WIDTH - spad, MuseumManageParadeOptionProvider.ENTRY_HEIGHT, new ButtonReportingCustomPanel(this, baseColor, -1));

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

            TooltipMakerAPI entryAnchor = entryPanel.createUIElement(MuseumManageParadeOptionProvider.ENTRY_WIDTH - spad, MuseumManageParadeOptionProvider.BUTTON_30, false);
            entryAnchor.addSectionHeading("Profile - " + (profileName == null ? " Random Name" : profileName) + " " + activity, textColour, headerColour, Alignment.MID, opad);

            entryPanel.addUIElement(entryAnchor).inTL(0, 0);
            lastUsedAnchor = entryAnchor;

            //name

            TooltipMakerAPI textAnchor = entryPanel.createUIElement(MuseumManageParadeOptionProvider.BUTTON_100, MuseumManageParadeOptionProvider.BUTTON_30, false);
            textAnchor.addPara("Fleet Name: ", spad);//.getPosition().inLMid(opad);

            entryPanel.addUIElement(textAnchor).belowLeft(lastUsedAnchor, 0f);
            TooltipMakerAPI lastTextAnchor = textAnchor;
            lastUsedAnchor = textAnchor;

            String text = profileName == null ? "Random" : "Custom:";

            TooltipMakerAPI buttonAnchor = entryPanel.createUIElement(MuseumManageParadeOptionProvider.BUTTON_80, MuseumManageParadeOptionProvider.BUTTON_30, false);
            ButtonAPI nameModeBox = buttonAnchor.addAreaCheckbox(text, new ButtonAction(this) {
                @Override
                public void execute() {
                    if (profile.getNamePreset() != null) profile.resetNamePreset();
                    else profile.setNamePreset("Custom Name");
                    recreatePanel(callback);
                }
            }, baseColor, bgColour, brightColor, MuseumManageParadeOptionProvider.BUTTON_80, MuseumManageParadeOptionProvider.BUTTON_30, 0);

            entryPanel.addUIElement(buttonAnchor).rightOfMid(lastUsedAnchor, opad);
            lastUsedAnchor = buttonAnchor;

            if (profileName != null) {
                float width = MuseumManageParadeOptionProvider.BUTTON_150 * 2;
                TooltipMakerAPI nameFieldAnchor = entryPanel.createUIElement(width, MuseumManageParadeOptionProvider.BUTTON_30, false);
                TextFieldAPI nameField = nameFieldAnchor.addTextField(width, opad);
                nameField.setHandleCtrlV(true);
                nameField.showCursor();
                nameField.setText(profileName);

                entryPanel.addUIElement(nameFieldAnchor).rightOfBottom(lastUsedAnchor, opad);
                lastUsedAnchor = nameFieldAnchor;

                buttonAnchor = entryPanel.createUIElement(60f, MuseumManageParadeOptionProvider.BUTTON_30, false);
                ButtonAPI saveButton = buttonAnchor.addButton("Save", new ButtonAction(this) {
                    @Override
                    public void execute() {
                        profile.setNamePreset(nameField.getText());
                        recreatePanel(callback);
                    }
                }, baseColor, bgColour, Alignment.MID, CutStyle.ALL, 60f, MuseumManageParadeOptionProvider.BUTTON_30, 0);

                entryPanel.addUIElement(buttonAnchor).rightOfBottom(lastUsedAnchor, opad);
            }

            //Loadout

            textAnchor = entryPanel.createUIElement(MuseumManageParadeOptionProvider.BUTTON_100, MuseumManageParadeOptionProvider.BUTTON_30, false);
            textAnchor.addPara("Ships: ", spad);//.getPosition().inLMid(opad);

            entryPanel.addUIElement(textAnchor).belowLeft(lastTextAnchor, spad);
            lastTextAnchor = textAnchor;
            lastUsedAnchor = textAnchor;

            text = profile.getMemberIdPreset() == null ? "Random" : "Custom:";

            buttonAnchor = entryPanel.createUIElement(MuseumManageParadeOptionProvider.BUTTON_80, MuseumManageParadeOptionProvider.BUTTON_30, false);
            buttonAnchor.addAreaCheckbox(text, new ButtonAction(this) {
                @Override
                public void execute() {
                    if (profile.getMemberIdPreset() != null) profile.resetMembers();
                    else profile.setMemberList(new ArrayList<>());
                    recreatePanel(callback);
                }
            }, baseColor, bgColour, brightColor, MuseumManageParadeOptionProvider.BUTTON_80, MuseumManageParadeOptionProvider.BUTTON_30, 0);

            entryPanel.addUIElement(buttonAnchor).rightOfMid(lastUsedAnchor, opad);
            lastUsedAnchor = buttonAnchor;

            if (profile.getMemberIdPreset() != null) {
                buttonAnchor = entryPanel.createUIElement(MuseumManageParadeOptionProvider.BUTTON_80, MuseumManageParadeOptionProvider.BUTTON_30, false);
                buttonAnchor.addButton("Edit", new ButtonAction(this) {
                    @Override
                    public void execute() {
                        dismissOnNextCancel = false;
                        callback.dismissCustomDialog(1);

                        ((ManageParadeDialoguePlugin) dialog).showShipPicker(profile);

                    }
                }, baseColor, bgColour, Alignment.MID, CutStyle.ALL, MuseumManageParadeOptionProvider.BUTTON_80, MuseumManageParadeOptionProvider.BUTTON_30, 0);

                entryPanel.addUIElement(buttonAnchor).rightOfBottom(lastUsedAnchor, opad);
                lastUsedAnchor = buttonAnchor;

                float width = MuseumManageParadeOptionProvider.BUTTON_150 * 4;
                TooltipMakerAPI fleetMemberDisplayElement = entryPanel.createUIElement(width, MuseumManageParadeOptionProvider.BUTTON_30, false);

                SubmarketAPI sub = museum.getSubmarket();
                if (sub == null) {
                    museum.advance(0);
                    sub = museum.getSubmarket();
                }

                CargoAPI cargo = sub.getCargo();
                cargo.initMothballedShips(Factions.PLAYER);
                List<FleetMemberAPI> paradeMembers = new ArrayList<>();
                for (FleetMemberAPI m : cargo.getMothballedShips().getMembersListCopy())
                    if (profile.getMemberIdPreset().contains(m.getId())) paradeMembers.add(m);

                fleetMemberDisplayElement.addShipList(10, 1, MuseumManageParadeOptionProvider.BUTTON_30, baseColor, paradeMembers, 0f);

                entryPanel.addUIElement(fleetMemberDisplayElement).rightOfBottom(lastUsedAnchor, opad);
                lastUsedAnchor = fleetMemberDisplayElement;

            }

            //duration

            textAnchor = entryPanel.createUIElement(MuseumManageParadeOptionProvider.BUTTON_100, MuseumManageParadeOptionProvider.BUTTON_30, false);
            textAnchor.addPara("Duration: ", spad);//.getPosition().inLMid(opad);

            entryPanel.addUIElement(textAnchor).belowLeft(lastTextAnchor, spad);
            lastTextAnchor = textAnchor;
            lastUsedAnchor = textAnchor;

            int current = profile.getDurationPreset();
            float width = MuseumManageParadeOptionProvider.BUTTON_80;

            final int buttonDays1 = MuseumConstants.PARADE_DAY_OPTIONS[0];
            buttonAnchor = entryPanel.createUIElement(width, MuseumManageParadeOptionProvider.BUTTON_30, false);
            int months = Math.round((float) buttonDays1 / 31);
            String monthOrMonths = months > 1 ? " months" : " month";
            ButtonAPI button = buttonAnchor.addAreaCheckbox(months + monthOrMonths, new ButtonAction(this) {
                @Override
                public void execute() {
                    profile.setDuration(buttonDays1);
                    recreatePanel(callback);
                }
            }, baseColor, bgColour, brightColor, width, MuseumManageParadeOptionProvider.BUTTON_30, 0);
            button.setChecked(current == buttonDays1);

            entryPanel.addUIElement(buttonAnchor).rightOfBottom(lastUsedAnchor, opad);
            lastUsedAnchor = buttonAnchor;

            final int buttonDays2 = MuseumConstants.PARADE_DAY_OPTIONS[1];
            buttonAnchor = entryPanel.createUIElement(width, MuseumManageParadeOptionProvider.BUTTON_30, false);
            months = Math.round((float) buttonDays2 / 31);
            monthOrMonths = months > 1 ? " months" : " month";
            button = buttonAnchor.addAreaCheckbox(months + monthOrMonths, new ButtonAction(this) {
                @Override
                public void execute() {
                    profile.setDuration(buttonDays2);
                    recreatePanel(callback);
                }
            }, baseColor, bgColour, brightColor, width, MuseumManageParadeOptionProvider.BUTTON_30, 0);
            button.setChecked(current == buttonDays2);

            entryPanel.addUIElement(buttonAnchor).rightOfMid(lastUsedAnchor, opad);
            lastUsedAnchor = buttonAnchor;

            final int buttonDays3 = MuseumConstants.PARADE_DAY_OPTIONS[2];
            buttonAnchor = entryPanel.createUIElement(width, MuseumManageParadeOptionProvider.BUTTON_30, false);
            months = Math.round((float) buttonDays3 / 31);
            monthOrMonths = months > 1 ? " months" : " month";
            button = buttonAnchor.addAreaCheckbox(months + monthOrMonths, new ButtonAction(this) {
                @Override
                public void execute() {
                    profile.setDuration(buttonDays3);
                    recreatePanel(callback);
                }
            }, baseColor, bgColour, brightColor, width, MuseumManageParadeOptionProvider.BUTTON_30, 0);
            button.setChecked(current == buttonDays3);

            entryPanel.addUIElement(buttonAnchor).rightOfMid(lastUsedAnchor, opad);
            lastUsedAnchor = buttonAnchor;

            final int buttonDays4 = MuseumConstants.PARADE_DAY_OPTIONS[3];
            buttonAnchor = entryPanel.createUIElement(width, MuseumManageParadeOptionProvider.BUTTON_30, false);
            months = Math.round((float) buttonDays4 / 31);
            monthOrMonths = months > 1 ? " months" : " month";
            button = buttonAnchor.addAreaCheckbox(months + monthOrMonths, new ButtonAction(this) {
                @Override
                public void execute() {
                    profile.setDuration(buttonDays4);
                    recreatePanel(callback);
                }
            }, baseColor, bgColour, brightColor, width, MuseumManageParadeOptionProvider.BUTTON_30, 0);
            button.setChecked(current == buttonDays4);

            entryPanel.addUIElement(buttonAnchor).rightOfMid(lastUsedAnchor, opad);
            lastUsedAnchor = buttonAnchor;

            //options: Recall - disable - delete

            textAnchor = entryPanel.createUIElement(MuseumManageParadeOptionProvider.BUTTON_100, MuseumManageParadeOptionProvider.BUTTON_30, false);
            textAnchor.addPara("Options: ", spad);//.getPosition().inLMid(opad);

            entryPanel.addUIElement(textAnchor).belowLeft(lastTextAnchor, spad);
            lastTextAnchor = textAnchor;
            lastUsedAnchor = textAnchor;

            enabled = profile.getCurrentFleet() != null && !profile.getCurrentFleet().hasTag(FLEET_RETURNING_TAG);
            buttonBgColour = enabled ? new Color(140, 70, 20, 255) : grayColour;
            buttonTextColour = Color.WHITE;

            buttonAnchor = entryPanel.createUIElement(MuseumManageParadeOptionProvider.BUTTON_120, MuseumManageParadeOptionProvider.BUTTON_30, false);
            button = buttonAnchor.addButton("Recall Parade", new ButtonAction(this) {
                @Override
                public void execute() {
                    ParadeFleetAssignmentAI.get(profile.getCurrentFleet()).forceReturnToSource();
                    recreatePanel(callback);
                }
            }, buttonTextColour, buttonBgColour, Alignment.MID, CutStyle.ALL, MuseumManageParadeOptionProvider.BUTTON_120, MuseumManageParadeOptionProvider.BUTTON_30, 0);

            button.setEnabled(enabled);

            entryPanel.addUIElement(buttonAnchor).rightOfBottom(lastUsedAnchor, opad);
            lastUsedAnchor = buttonAnchor;

            buttonAnchor = entryPanel.createUIElement(MuseumManageParadeOptionProvider.BUTTON_120, MuseumManageParadeOptionProvider.BUTTON_30, false);
            button = buttonAnchor.addAreaCheckbox((profile.isEnabled() ? "Disable" : "Enable") + " profile", new ButtonAction(this) {
                @Override
                public void execute() {
                    profile.setEnabled(!profile.isEnabled());
                    recreatePanel(callback);
                }
            }, baseColor, bgColour, brightColor, MuseumManageParadeOptionProvider.BUTTON_120, MuseumManageParadeOptionProvider.BUTTON_30, 0);

            button.setChecked(profile.isEnabled());

            entryPanel.addUIElement(buttonAnchor).rightOfMid(lastUsedAnchor, opad);
            lastUsedAnchor = buttonAnchor;

            enabled = museum.getParadeFleetProfiles().size() > museum.getMaxParades();
            buttonBgColour = enabled ? new Color(80, 20, 10, 255) : grayColour;
            buttonTextColour = Color.WHITE;

            buttonAnchor = entryPanel.createUIElement(MuseumManageParadeOptionProvider.BUTTON_120, MuseumManageParadeOptionProvider.BUTTON_30, false);
            button = buttonAnchor.addButton("Delete profile", new ButtonAction(this) {
                @Override
                public void execute() {
                    museum.getParadeFleetProfiles().remove(profile);
                    recreatePanel(callback);
                }
            }, buttonTextColour, buttonBgColour, Alignment.MID, CutStyle.ALL, MuseumManageParadeOptionProvider.BUTTON_120, MuseumManageParadeOptionProvider.BUTTON_30, 0);

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
