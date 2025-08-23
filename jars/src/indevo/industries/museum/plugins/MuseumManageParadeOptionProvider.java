package indevo.industries.museum.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomDialogDelegate;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import indevo.dialogue.sidepanel.ButtonAction;
import indevo.dialogue.sidepanel.ButtonReportingCustomPanel;
import indevo.dialogue.sidepanel.ButtonReportingDialogueDelegate;
import indevo.ids.Ids;
import indevo.industries.museum.data.MuseumConstants;
import indevo.industries.museum.data.ParadeFleetProfile;
import indevo.industries.museum.industry.Museum;
import indevo.utils.plugins.SingleIndustrySimpifiedOptionProvider;

import java.awt.*;
import java.util.List;

public class MuseumManageParadeOptionProvider extends SingleIndustrySimpifiedOptionProvider {

    //recall - set parade loc outside viewport (angle to old loc) and set return assignment (forceReturnToSource)
    //create - set up new parade, data: Name, Target planet, Ships, duration (add button: repeat)
    //stop parades & resume random parades

    //make sure to have contingency for multiple parades

    public static final float WIDTH = 600f;
    public static final float HEIGHT = Global.getSettings().getScreenHeight() - 300f;
    protected static final float BUTTON_20 = 20, BUTTON_30 = 30, BUTTON_150 = 150f, BUTTON_100 = 100f;

    public static final float ENTRY_HEIGHT = 100; //MUST be even
    public static final float ENTRY_WIDTH = WIDTH - 10f; //MUST be even
    public static final float CONTENT_HEIGHT = 96;

    public static void register(){
        Global.getSector().getListenerManager().addListener(new MuseumManageParadeOptionProvider(), true);
    }

    @Override
    public String getTargetIndustryId() {
        return Ids.MUSEUM;
    }

    @Override
    public boolean optionEnabled(IndustryOptionData opt) {
        return ((Museum) opt.ind).getArchiveSubMarkets().size() < 5;
    }

    @Override
    public void onClick(IndustryOptionData opt, DialogCreatorUI ui) {
        Museum museum = ((Museum) opt.ind);

        //update so list isn't empty if opened the first time
        List<ParadeFleetProfile> profiles = museum.getParadeFleetProfiles();
        int maxParades = museum.getMaxParades();
        if (profiles.size() < maxParades) while (profiles.size() < maxParades) profiles.add(new ParadeFleetProfile(museum));

        CustomDialogDelegate delegate = new ButtonReportingDialogueDelegate() {

            CustomPanelAPI basePanel;
            CustomPanelAPI panel;

            float listScrollY = 0f;
            ScrollPanelAPI externalScroller;

            public void createCustomDialog(CustomPanelAPI panel, final CustomDialogCallback callback) {
                this.basePanel = panel;
                recreatePanel(callback);
            }

            public void recreatePanel(final CustomDialogCallback callback) {
                // capture current scroll before tearing down
                if (externalScroller != null) listScrollY = externalScroller.getYOffset();

                if (panel != null) basePanel.removeComponent(panel);

                panel = Global.getSettings().createCustom(basePanel.getPosition().getWidth(), basePanel.getPosition().getHeight(), null);

                float opad = 10f;
                float spad = 5f;

                Color baseColor = Misc.getButtonTextColor();
                Color bgColour = Misc.getDarkPlayerColor();
                Color brightColor = Misc.getBrightPlayerColor();

                //--------- Add basic on/off option ---------

                //heading
                TooltipMakerAPI bottomPanel = panel.createUIElement(WIDTH, HEIGHT, false);
                bottomPanel.addSectionHeading("Parade configuration", Alignment.MID, opad);

                //button panel
                CustomPanelAPI selectorPanel = panel.createCustomPanel(WIDTH, BUTTON_30 + opad, new ButtonReportingCustomPanel(this));
                TooltipMakerAPI selectorAnchor = selectorPanel.createUIElement(BUTTON_150, BUTTON_30, false);

                ButtonAPI paradeOnButton = selectorAnchor.addAreaCheckbox("Enable Parades", new ButtonAction(this) {
                    @Override
                    public void execute() {
                        museum.setFlyParades(true);
                        recreatePanel(callback);
                    }
                }, baseColor, bgColour, brightColor, BUTTON_150, BUTTON_30, 0);

                paradeOnButton.setChecked(museum.flyParades());

                selectorPanel.addUIElement(selectorAnchor).inLMid(-opad); //if we don't -opad it kinda does it by its own, no clue why
                TooltipMakerAPI lastUsedAnchor = selectorAnchor;

                selectorAnchor = selectorPanel.createUIElement(BUTTON_150, BUTTON_30, false);

                ButtonAPI showInCargoButton = selectorAnchor.addAreaCheckbox("Disable Parades", new ButtonAction(this) {
                    @Override
                    public void execute() {
                        museum.setFlyParades(false);
                        recreatePanel(callback);
                    }
                }, baseColor, bgColour, brightColor, BUTTON_150, BUTTON_30, 0);

                showInCargoButton.setChecked(!museum.flyParades());

                selectorPanel.addUIElement(selectorAnchor).rightOfMid(lastUsedAnchor, opad);
                lastUsedAnchor = selectorAnchor;

                selectorAnchor = selectorPanel.createUIElement(BUTTON_150, BUTTON_30, false);

                ButtonAPI addProfileButton = selectorAnchor.addButton("+ add new profile", new ButtonAction(this) {
                    @Override
                    public void execute() {
                        museum.getParadeFleetProfiles().add(new ParadeFleetProfile(museum));
                        recreatePanel(callback);
                    }
                }, baseColor, bgColour, Alignment.MID, CutStyle.ALL, BUTTON_150, BUTTON_30, 0);

                addProfileButton.setEnabled(museum.getParadeFleetProfiles().size() < (MuseumConstants.DEFAULT_MAX_PARADES + MuseumConstants.ALPHA_CORE_EXTRA_PARADES) * 3f);

                selectorPanel.addUIElement(selectorAnchor).rightOfMid(lastUsedAnchor, WIDTH - BUTTON_150 * 3 - opad);

                bottomPanel.addCustom(selectorPanel, opad);

                //next line, new panel
                selectorPanel = panel.createCustomPanel(WIDTH, BUTTON_30 * 2, new ButtonReportingCustomPanel(this));
                TooltipMakerAPI textPanel = selectorPanel.createUIElement(WIDTH, BUTTON_30, false);

                textPanel.addSectionHeading("Parade profiles", Alignment.MID, opad);
                textPanel.addPara("Parade profiles are used to configure your parades. A random one will be picked for a parade if there are more profiles than fleet slots.", opad);
                selectorPanel.addUIElement(textPanel).inTL(-5f, 0f);
                lastUsedAnchor = selectorAnchor;

                bottomPanel.addCustom(selectorPanel, opad);

                //parade fleet profile list
                float height = HEIGHT - 160f;

                selectorPanel = panel.createCustomPanel(WIDTH, height, new ButtonReportingCustomPanel(this));
                selectorAnchor = selectorPanel.createUIElement(WIDTH, height, true);

                if (externalScroller != null) {
                    selectorAnchor.setExternalScroller(externalScroller);
                    externalScroller.setYOffset(listScrollY);
                }

                //add the profile list
                boolean first = true;
                for (ParadeFleetProfile profile : museum.getParadeFleetProfiles()){
                    CustomPanelAPI entryPanel = panel.createCustomPanel(ENTRY_WIDTH, ENTRY_HEIGHT, new ButtonReportingCustomPanel(this, baseColor, -1));

                    //header
                    String profileName = profile.getNamePreset();
                    Color headerColour = Misc.getGrayColor();
                    if (profile.isEnabled()) headerColour =  bgColour;
                    if (profile.hasActiveFleet()) headerColour = MuseumConstants.SUBMARKET_COLOUR;

                    Color textColour = Color.BLACK;
                    if (profile.isEnabled()) textColour = baseColor;
                    if (profile.hasActiveFleet()) textColour = Color.WHITE;

                    TooltipMakerAPI entryAnchor = entryPanel.createUIElement(ENTRY_WIDTH, BUTTON_20, false);
                    entryAnchor.addSectionHeading("Profile - " + (profileName == null ? " Random" : profileName), textColour, headerColour, Alignment.MID, opad);

                    entryPanel.addUIElement(entryAnchor);
                    lastUsedAnchor = entryAnchor;

                    //name
                    TooltipMakerAPI textAnchor = entryPanel.createUIElement(BUTTON_100, BUTTON_30, false);
                    textAnchor.addPara("Fleet Name: ", 0f);
                    entryPanel.addUIElement(textAnchor).belowLeft(lastUsedAnchor, 0f);

                    lastUsedAnchor = textAnchor;

                    String text = profileName == null ? "Random" : "Custom:";

                    TooltipMakerAPI buttonAnchor = entryPanel.createUIElement(BUTTON_100, BUTTON_30, false);
                    ButtonAPI nameModeBox = buttonAnchor.addAreaCheckbox(text, new ButtonAction(this) {
                        @Override
                        public void execute() {
                            if (profile.getNamePreset() != null) profile.resetNamePreset();
                            else profile.setNamePreset("Custom Name");
                            recreatePanel(callback);
                        }
                    }, baseColor, bgColour, brightColor, BUTTON_150, BUTTON_30, 0);

                    entryPanel.addUIElement(buttonAnchor).rightOfMid(lastUsedAnchor, opad);
                    lastUsedAnchor = buttonAnchor;

                    if (profileName != null){
                        float width = BUTTON_150 * 2;
                        TooltipMakerAPI nameFieldAnchor = entryPanel.createUIElement(width, BUTTON_30, false);
                        TextFieldAPI nameField = nameFieldAnchor.addTextField(width, opad);
                        nameField.setText(profileName);

                        entryPanel.addUIElement(nameFieldAnchor).rightOfMid(lastUsedAnchor, opad);
                        lastUsedAnchor = nameFieldAnchor;

                        buttonAnchor = entryPanel.createUIElement(60f, BUTTON_30, false);
                        ButtonAPI saveButton = buttonAnchor.addButton("Save", new ButtonAction(this) {
                            @Override
                            public void execute() {
                                profile.setNamePreset(nameField.getText());
                                Global.getSoundPlayer().playUISound("ui_rep_raise", 0.8f, 0.5f);
                            }
                        }, baseColor, bgColour, Alignment.MID, CutStyle.ALL, 60f, BUTTON_30, 0);

                        selectorPanel.addUIElement(selectorAnchor).rightOfMid(lastUsedAnchor, WIDTH - BUTTON_150 * 3 - opad);
                    }

                    //loadout


                    //duration


                    //options: Recall - disable - delete

                    if (first) selectorAnchor.addCustom(entryPanel, spad); //why?? WHY?
                    else selectorAnchor.addCustom(entryPanel, spad);
                    first = false;
                }

                selectorPanel.addUIElement(selectorAnchor).inTL(-opad, 0f); //if we don't -opad it kinda does it by its own, no clue why
                bottomPanel.addCustom(selectorPanel, opad);

                panel.addUIElement(bottomPanel).inTL(0f, 0.0F);
                basePanel.addComponent(panel);

                if (externalScroller == null) externalScroller = selectorAnchor.getExternalScroller();
            }

            @Override
            public String getConfirmText() {
                return "Return";
            }
        };

        ui.showDialog(WIDTH, HEIGHT, delegate);
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, IndustryOptionData opt) {
        Color hl = Misc.getHighlightColor();
        float opad = 10f;

        tooltip.addPara("Manage the parade fleets your museum dispatches. Stop, resume them, or set up your own configurations.", 0f);
    }

    @Override
    public String getOptionLabel(Industry ind) {
        return "Manage Parades...";
    }
}