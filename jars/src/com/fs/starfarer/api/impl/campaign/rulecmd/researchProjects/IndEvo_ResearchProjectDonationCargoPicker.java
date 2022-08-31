package com.fs.starfarer.api.impl.campaign.rulecmd.researchProjects;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.IndEvo_StringHelper;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoPickerListener;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;

public class IndEvo_ResearchProjectDonationCargoPicker {

    public static final Logger log = Global.getLogger(IndEvo_ResearchProjectDonationCargoPicker.class);

    public static String getStackId(CargoStackAPI stack) {
        switch (stack.getType()) {
            case RESOURCES:
                return stack.getCommodityId();
            case WEAPONS:
                return stack.getWeaponSpecIfWeapon().getWeaponId();
            case FIGHTER_CHIP:
                return stack.getFighterWingSpecIfWing().getId();
            case SPECIAL:
                return stack.getSpecialDataIfSpecial().getId();
            case NULL:
                return null;
        }

        return null;
    }

    public static void init(IndEvo_ResearchProjectDialoguePlugin plugin, String id) {
        final float width = 310;
        InteractionDialogAPI dialog = plugin.dialog;
        final IndEvo_ResearchProject project = IndEvo_ResearchProjectTemplateRepo.RESEARCH_PROJECTS.get(id);

        final CargoAPI allowedItemsInPlayerCargo = Global.getFactory().createCargo(false);

        for (CargoStackAPI stack : Global.getSector().getPlayerFleet().getCargo().createCopy().getStacksCopy()) {
            String stackId = getStackId(stack);

            for (IndEvo_RequiredItem item : project.getRequiredItems()) {
                if (item.id.equals(stackId)) allowedItemsInPlayerCargo.addFromStack(stack);
            }
        }

        dialog.showCargoPickerDialog("Select items to hand in", "Confirm", "Cancel", false, width, allowedItemsInPlayerCargo, new CargoPickerListener() {
            public void pickedCargo(CargoAPI cargo) {

                if (!cargo.isEmpty()) {
                    // TODO: 11.03.2022 make sure to remove all excess items, using only the ones first picked until point count is filled
                    IndEvo_ResearchProject.Progress progress = project.getProgress();

                    for (CargoStackAPI stack : cargo.getStacksCopy()) {
                        String stackId = getStackId(stack);
                        float num = stack.getSize();
                        float pointsPerItem = 0f;

                        for (IndEvo_RequiredItem item : project.getRequiredItems()) {
                            if (item.id.equals(stackId)) {
                                pointsPerItem = item.points;
                                break;
                            }
                            ;
                        }

                        float totalPointsFromStack = num * pointsPerItem;
                        float requiredPointsToFinish = project.getRequiredPoints() - progress.points;
                        //2/4 = 0.75 round up = 1
                        float neededWeaponsToFinish = (float) Math.ceil(requiredPointsToFinish / pointsPerItem);

                        //lower or equal
                        if (requiredPointsToFinish >= totalPointsFromStack) {
                            Global.getSector().getPlayerFleet().getCargo().removeItems(stack.getType(), stack.getData(), stack.getSize());
                            progress.points += totalPointsFromStack;

                            //higher, remove only what is needed
                        } else {
                            Global.getSector().getPlayerFleet().getCargo().removeItems(stack.getType(), stack.getData(), neededWeaponsToFinish);
                            progress.points += neededWeaponsToFinish * pointsPerItem;
                        }

                        if (progress.points >= project.getRequiredPoints()) break;
                    }

                    IndEvo_ResearchProjectDialoguePlugin.getCurrentDialoguePlugin().refreshCustomPanel();
                }
            }

            public void cancelledCargoSelection() {
            }

            public void recreateTextPanel(TooltipMakerAPI panel, CargoAPI cargo, CargoStackAPI pickedUp, boolean pickedUpFromSource, CargoAPI combined) {
                float opad = 10f;
                //panel.addImage("cargo_loading", width * 1f, 3f);
                panel.addSectionHeading("Select the items you would like to donate.", Alignment.MID, opad);
                panel.addPara("If more items than required are handed in, the excess will remain with you.", opad, Misc.getGrayColor());
                panel.addPara("", opad);

                panel.setGridFontDefault();
                panel.beginGridFlipped(300, 1, 100f, 10f);

                int i = 0;
                float total = 0f;

                for (CargoStackAPI stack : cargo.getStacksCopy()) {
                    if (stack.getSize() < 1) continue;

                    String stackId = getStackId(stack);
                    float points = 0f;

                    for (IndEvo_RequiredItem item : project.getRequiredItems()) {
                        if (item.id.equals(stackId)) {
                            points = item.points;
                            break;
                        }
                    }
                    float progress = (points / project.getRequiredPoints()) * stack.getSize();
                    total += progress;

                    String percentPerItem = IndEvo_StringHelper.getAbsPercentString(progress, false);

                    if (i < 10) {
                        panel.addToGrid(0, i, (int) stack.getSize() + "x " + stack.getDisplayName(), percentPerItem);
                        i++;
                    }
                }

                panel.addGrid(opad);
                if (i > 9) {
                    panel.addPara("... and " + (cargo.getStacksCopy().size() - 10) + " more.", 3f);
                }
                panel.setParaFontDefault();

                panel.addPara("Total progress: %s", opad, Misc.getPositiveHighlightColor(), IndEvo_StringHelper.getAbsPercentString(total, false));
            }
        });
    }

}
