package indevo.industries.artillery.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.impl.campaign.PlanetInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Sounds;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.SetStoryOption;
import indevo.ids.Ids;

import javax.swing.text.html.Option;

/**
 * watchtowers count as campaign objectives if counted via the misc. method so we have to replace the interaction plugin to make it not do that
 *
 * THIS CODE IS BUG-PRONE BECAUSE IT USES STRINGS TO SUBSTITUTE FOR THE PRIVATE ENUMS, WHICH WILL FAIL == CHECKS
 */

public class SunInteractionDialogPluginImpl extends PlanetInteractionDialogPluginImpl {

    private InteractionDialogAPI dialog;
    private TextPanelAPI textPanel;
    private OptionPanelAPI options;

    public enum Options {
        ADD_STABLE_CONFIRM,
        ADD_STABLE_NEVER_MIND
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        this.textPanel = dialog.getTextPanel();
        this.options = dialog.getOptionPanel();

        super.init(dialog);
    }

    @Override
    public void optionSelected(String text, Object optionData) {
        if (optionData == null) return;

        PlanetAPI planet = ((PlanetAPI) dialog.getInteractionTarget());
        String corona2 = planet.getSpec().isBlackHole() ? "near the event horizon" : "in the star's corona";

        switch (optionData.toString()){
            case "ADD_STABLE_CONFIRM":
                StarSystemAPI system = planet.getStarSystem();
                if (system != null) {

                    CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
                    cargo.removeFuel(STABLE_FUEL_REQ);
                    AddRemoveCommodity.addCommodityLossText(Commodities.FUEL, STABLE_FUEL_REQ, dialog.getTextPanel());
                    StarSystemGenerator.addStableLocations(system, 1);
                    planet.getMemoryWithoutUpdate().set(ADDED_KEY, true);
                    textPanel.addParagraph("Preparations are made, and you give the go-ahead. " +
                            "A few tense minutes later, the chief engineer reports success. " +
                            "The resulting stable location won't last for millennia, like " +
                            "naturally-occurring ones - but it'll do for your purposes.");
                }
                createInitialOptions();
                break;
            case "ADD_STABLE_DESCRIBE":

                textPanel.addParagraph("The procedure requires spreading prodigious amounts of antimatter " + corona2 + ", " +
                        "according to calculations far beyond the ability of anything on the right side of the " +
                        "treaty that ended the Second AI War.");
                boolean canAfford = textPanel.addCostPanel("Resources required (available)",
                        Commodities.ALPHA_CORE, 1, false,
                        Commodities.HEAVY_MACHINERY, STABLE_MACHINERY_REQ, false,
                        Commodities.FUEL, STABLE_FUEL_REQ, true
                );

                options.clearOptions();

                int num = getNumStableLocations(planet.getStarSystem());
                boolean alreadyCant = false;
                if (num <= 0) {
                    options.addOption("Proceed with the operation", Options.ADD_STABLE_CONFIRM, null);
                } else if (num < 2) {
                    textPanel.addParagraph("Normally, this procedure can only be performed in a star system without any " +
                            "stable locations. However, your chief engineer suggests an unorthodox workaround.");
                    options.addOption("Proceed with the operation", Options.ADD_STABLE_CONFIRM, null);
                    SetStoryOption.set(dialog, Global.getSettings().getInt("createStableLocation"),
                            Options.ADD_STABLE_CONFIRM, "createStableLocation", Sounds.STORY_POINT_SPEND_TECHNOLOGY,
                            "Created additional stable location in " + planet.getStarSystem().getNameWithLowercaseType() + "");
                } else {
                    alreadyCant = true;

                    String reason = "This procedure can not performed in a star system that already has " +
                            "numerous stable locations.";
                    options.addOption("Proceed with the operation", Options.ADD_STABLE_CONFIRM, null);
                    options.setEnabled(Options.ADD_STABLE_CONFIRM, false);
                    textPanel.addParagraph(reason);
                    options.setTooltip(Options.ADD_STABLE_CONFIRM, reason);
                }

                if (!canAfford && !alreadyCant) {
                    String reason = "You do not have the necessary resources to carry out this procedure.";
                    options.setEnabled(Options.ADD_STABLE_CONFIRM, false);
                    textPanel.addParagraph(reason);
                    options.setTooltip(Options.ADD_STABLE_CONFIRM, reason);
                }

                options.addOption("Never mind", Options.ADD_STABLE_NEVER_MIND, null);
                break;
            case "ADD_STABLE_NEVER_MIND":
                createInitialOptions();
                break;
            default: super.optionSelected(text, optionData);
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
}
