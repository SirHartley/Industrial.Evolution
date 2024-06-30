package indevo.industries.artillery.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.impl.campaign.DevMenuOptions;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Sounds;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.DumpMemory;
import com.fs.starfarer.api.impl.campaign.rulecmd.SetStoryOption;
import com.fs.starfarer.api.loading.Description;
import indevo.ids.Ids;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * watchtowers count as campaign objectives if counted via the misc. method so we have to replace the interaction plugin to make it not do that
 */

public class SunInteractionDialogPluginImpl implements InteractionDialogPlugin {
    public static int STABLE_FUEL_REQ = 500;
    public static int STABLE_MACHINERY_REQ = 200;

    private static enum OptionId {
        INIT,
        ADD_STABLE_CONFIRM,
        ADD_STABLE_DESCRIBE,
        ADD_STABLE_NEVER_MIND,
        LEAVE,
    }

    private InteractionDialogAPI dialog;
    private TextPanelAPI textPanel;
    private OptionPanelAPI options;
    private VisualPanelAPI visual;

    private CampaignFleetAPI playerFleet;
    private PlanetAPI planet;

    private static final Color HIGHLIGHT_COLOR = Global.getSettings().getColor("buttonShortcut");

    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;

//		dialog.hideVisualPanel();
//		dialog.setTextWidth(700);

        textPanel = dialog.getTextPanel();
        options = dialog.getOptionPanel();
        visual = dialog.getVisualPanel();

        playerFleet = Global.getSector().getPlayerFleet();
        planet = (PlanetAPI) dialog.getInteractionTarget();

        visual.setVisualFade(0.25f, 0.25f);

        if (planet.getCustomInteractionDialogImageVisual() != null) {
            visual.showImageVisual(planet.getCustomInteractionDialogImageVisual());
        } else {
            if (!Global.getSettings().getBoolean("3dPlanetBGInInteractionDialog")) {
                visual.showPlanetInfo(planet);
            }
        }

        dialog.setOptionOnEscape("Leave", OptionId.LEAVE);

        optionSelected(null, OptionId.INIT);
    }

    public Map<String, MemoryAPI> getMemoryMap() {
        return null;
    }

    public void backFromEngagement(EngagementResultAPI result) {
        // no combat here, so this won't get called
    }

    public void optionSelected(String text, Object optionData) {
        if (optionData == null) return;

        if (optionData == DumpMemory.OPTION_ID) {
            Map<String, MemoryAPI> memoryMap = new HashMap<String, MemoryAPI>();
            MemoryAPI memory = dialog.getInteractionTarget().getMemory();

            memoryMap.put(MemKeys.LOCAL, memory);
            if (dialog.getInteractionTarget().getFaction() != null) {
                memoryMap.put(MemKeys.FACTION, dialog.getInteractionTarget().getFaction().getMemory());
            } else {
                memoryMap.put(MemKeys.FACTION, Global.getFactory().createMemory());
            }
            memoryMap.put(MemKeys.GLOBAL, Global.getSector().getMemory());
            memoryMap.put(MemKeys.PLAYER, Global.getSector().getCharacterData().getMemory());

            if (dialog.getInteractionTarget().getMarket() != null) {
                memoryMap.put(MemKeys.MARKET, dialog.getInteractionTarget().getMarket().getMemory());
            }

            new DumpMemory().execute(null, dialog, null, memoryMap);

            return;
        } else if (DevMenuOptions.isDevOption(optionData)) {
            DevMenuOptions.execute(dialog, (String) optionData);
            return;
        }

        OptionId option = (OptionId) optionData;

        if (text != null) {
            //textPanel.addParagraph(text, Global.getSettings().getColor("buttonText"));
            dialog.addOptionSelectedText(option);
        }

        switch (option) {
            case INIT:
                boolean didAlready = planet.getMemoryWithoutUpdate().getBoolean(ADDED_KEY);
                addText(getString("approach"));
                if (didAlready) {
                    addText("The star's corona exhibits fluctuations indicative of recent antimatter application.");
                }

                Description desc = Global.getSettings().getDescription(planet.getCustomDescriptionId(), Description.Type.CUSTOM);
                if (desc != null && desc.hasText3()) {
                    addText(desc.getText3());
                }
                createInitialOptions();
                break;
            case ADD_STABLE_CONFIRM:
                StarSystemAPI system = planet.getStarSystem();
                if (system != null) {

                    CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
                    cargo.removeFuel(STABLE_FUEL_REQ);
                    AddRemoveCommodity.addCommodityLossText(Commodities.FUEL, STABLE_FUEL_REQ, dialog.getTextPanel());
                    StarSystemGenerator.addStableLocations(system, 1);
                    planet.getMemoryWithoutUpdate().set(ADDED_KEY, true);
                    addText("Preparations are made, and you give the go-ahead. " +
                            "A few tense minutes later, the chief engineer reports success. " +
                            "The resulting stable location won't last for millennia, like " +
                            "naturally-occurring ones - but it'll do for your purposes.");
                }
                createInitialOptions();
                break;
            case ADD_STABLE_DESCRIBE:
                addText("The procedure requires spreading prodigious amounts of antimatter in the star's corona, " +
                        "according to calculations far beyond the ability of anything on the right side of the " +
                        "treaty that ended the Second AI War.");
                boolean canAfford = dialog.getTextPanel().addCostPanel("Resources required (available)",
                        Commodities.ALPHA_CORE, 1, false,
                        Commodities.HEAVY_MACHINERY, STABLE_MACHINERY_REQ, false,
                        Commodities.FUEL, STABLE_FUEL_REQ, true
                );

                options.clearOptions();

                int num = getNumStableLocations(planet.getStarSystem());

                boolean alreadyCant = false;
                if (num <= 0) {
                    options.addOption("Proceed with the operation", OptionId.ADD_STABLE_CONFIRM, null);
                } else if (num < 2) {
                    addText("Normally, this procedure can only be performed in a star system without any " +
                            "stable locations. However, your chief engineer suggests an unorthodox workaround.");
                    options.addOption("Proceed with the operation", OptionId.ADD_STABLE_CONFIRM, null);
                    SetStoryOption.set(dialog, Global.getSettings().getInt("createStableLocation"),
                            OptionId.ADD_STABLE_CONFIRM, "createStableLocation", Sounds.STORY_POINT_SPEND_TECHNOLOGY,
                            "Created additional stable location in " + planet.getStarSystem().getNameWithLowercaseType() + "");
                } else {
                    alreadyCant = true;

                    String reason = "This procedure can not performed in a star system that already has " +
                            "numerous stable locations.";
                    options.addOption("Proceed with the operation", OptionId.ADD_STABLE_CONFIRM, null);
                    options.setEnabled(OptionId.ADD_STABLE_CONFIRM, false);
                    addText(reason);
                    options.setTooltip(OptionId.ADD_STABLE_CONFIRM, reason);
                }

                if (!canAfford && !alreadyCant) {
                    String reason = "You do not have the necessary resources to carry out this procedure.";
                    options.setEnabled(OptionId.ADD_STABLE_CONFIRM, false);
                    addText(reason);
                    options.setTooltip(OptionId.ADD_STABLE_CONFIRM, reason);
                }


                options.addOption("Never mind", OptionId.ADD_STABLE_NEVER_MIND, null);
                //createInitialOptions();
                break;
            case ADD_STABLE_NEVER_MIND:
                createInitialOptions();
                break;
            case LEAVE:
                Global.getSector().setPaused(false);
                dialog.dismiss();
                break;
        }
    }

    private int getNumStableLocations(StarSystemAPI system) {
        int count = system.getEntitiesWithTag(Tags.STABLE_LOCATION).size();

        for (SectorEntityToken t : system.getEntitiesWithTag(Tags.OBJECTIVE)) {
            if (t.hasTag(Ids.TAG_WATCHTOWER)) continue;

            count++;
        }

        return count;
    }

    public static String ADDED_KEY = "$core_starAddedStable";

    protected void createInitialOptions() {
        options.clearOptions();

        StarSystemAPI system = planet.getStarSystem();
        boolean didAlready = planet.getMemoryWithoutUpdate().getBoolean(ADDED_KEY);
        if (system != null && planet == system.getStar() && !didAlready) {
//			int num = MiscIE.getNumStableLocations(planet.getStarSystem());
            //options.addOption("Induce a resonance cascade in the star's hyperfield, creating a stable location", OptionId.ADD_STABLE_DESCRIBE, null);
            options.addOption("Consider inducing a resonance cascade in the star's hyperfield, creating a stable location", OptionId.ADD_STABLE_DESCRIBE, null);
//			SetStoryOption.set(dialog, Global.getSettings().getInt("createStableLocation"),
//					OptionId.ADD_STABLE, "createStableLocation", Sounds.STORY_POINT_SPEND_TECHNOLOGY);
//			if (num >= 3) {
//				options.setEnabled(OptionId.ADD_STABLE, false);
//				options.setTooltip(OptionId.ADD_STABLE, "This star system can't have any more stable locations.");
//			}
//			if (num >= 0) {
//				options.setEnabled(OptionId.ADD_STABLE, false);
//				options.setTooltip(OptionId.ADD_STABLE, "This procedure can only be performed in star systems " +
//														"without any stable locations.");
//			}
        }


        options.addOption("Leave", OptionId.LEAVE, null);
        options.setShortcut(OptionId.LEAVE, Keyboard.KEY_ESCAPE, false, false, false, true);

        if (Global.getSettings().isDevMode()) {
            DevMenuOptions.addOptions(dialog);
        }
    }


    private OptionId lastOptionMousedOver = null;

    public void optionMousedOver(String optionText, Object optionData) {

    }

    public void advance(float amount) {

    }

    private void addText(String text) {
        textPanel.addParagraph(text);
    }

    private void appendText(String text) {
        textPanel.appendToLastParagraph(" " + text);
    }

    private String getString(String id) {
        String str = Global.getSettings().getString("planetInteractionDialog", id);

        String fleetOrShip = "fleet";
        if (playerFleet.getFleetData().getMembersListCopy().size() == 1) {
            fleetOrShip = "ship";
            if (playerFleet.getFleetData().getMembersListCopy().get(0).isFighterWing()) {
                fleetOrShip = "fighter wing";
            }
        }
        str = str.replaceAll("\\$fleetOrShip", fleetOrShip);
        str = str.replaceAll("\\$planetName", planet.getName());

        return str;
    }


    public Object getContext() {
        return null;
    }
}
