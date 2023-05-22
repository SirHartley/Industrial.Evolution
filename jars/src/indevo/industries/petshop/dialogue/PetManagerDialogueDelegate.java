package indevo.industries.petshop.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.ItemEffectsRepo;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.changeling.industry.SubIndustryAPI;
import indevo.industries.changeling.industry.SwitchableIndustryAPI;
import indevo.industries.petshop.industry.PetShop;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PetManagerDialogueDelegate implements CustomDialogDelegate {
    //Select a Pet (provide filter buttons, Fleet - Storage - Cargo)
    //Move - Store - Rename - Euthanize
    //if move: fleet picker with valid ships in storage and fleet
    //store is instant
    //rename has confirm button

    //list of pets on the left, config options on the right

    public static final float WIDTH = 1200f;
    public static final float PET_SELECTOR_WIDTH = 300f;
    public static final float HEIGHT = Global.getSettings().getScreenHeight() - 300f;
    protected static final float ARROW_BUTTON_WIDTH = 20, BUTTON_HEIGHT = 30, SELECT_BUTTON_WIDTH = 95f;

    //for pet entries
    public static final float ENTRY_HEIGHT = 84f; //MUST be even
    public static final float ENTRY_WIDTH = WIDTH - 5f; //MUST be even
    public static final float CONTENT_HEIGHT = 80f;

    public Industry industry;

    public Pet selectedPet = null;
    public List<ButtonAPI> petAreaCheckboxes = new ArrayList<>();
    public PetLocationFilter currentFilter = PetLocationFilter.FLEET;

    public enum PetLocationFilter{
        FLEET,
        STORAGE,
        CARGO
    }

    public PetManagerDialogueDelegate(Industry industry) {
        this.industry = industry;
    }

    public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
        petAreaCheckboxes.clear();

        float opad = 10f;
        float spad = 2f;

        Color baseColor = Misc.getButtonTextColor();
        Color bgColour = Misc.getDarkPlayerColor();

        //filter button panel
        CustomPanelAPI filterButtonPanel = panel.createCustomPanel(PET_SELECTOR_WIDTH, BUTTON_HEIGHT + 20f, new ButtonReportingCustomPanel(this));
        TooltipMakerAPI anchor = filterButtonPanel.createUIElement(PET_SELECTOR_WIDTH, 20f, false);
        anchor.addSectionHeading("Select a Pet to manage", Alignment.MID, 0f);
        filterButtonPanel.addUIElement(anchor);

        TooltipMakerAPI lastUsed = anchor;

        //(provide filter buttons, Fleet - Storage - Cargo)
        //when pressed, re-set the selection panel with the updated list
        //by default, set "fleet" to true

        anchor = filterButtonPanel.createUIElement(PET_SELECTOR_WIDTH, BUTTON_HEIGHT, false);

        ButtonAPI fleetFilterButton = anchor.addButton("Fleet", new ButtonAction(this) {
            @Override
            public void execute() {
                PetManagerDialogueDelegate delegate = (PetManagerDialogueDelegate) this.delegate;
                delegate.currentFilter = PetLocationFilter.FLEET;
                delegate.redrawSelectorPanel();
            }
        }, baseColor, bgColour, Alignment.MID, CutStyle.C2_MENU, SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, 0);
        fleetFilterButton.setEnabled(true);

        filterButtonPanel.addUIElement(anchor).belowLeft(lastUsed,10f); // TODO: 22/05/2023 might have to set this to 0 so the spacing fits if too large
        lastUsed = anchor;

        anchor = filterButtonPanel.createUIElement(PET_SELECTOR_WIDTH, BUTTON_HEIGHT, false);

        ButtonAPI storageFilterButton = anchor.addButton("Storage", new ButtonAction(this) {
            @Override
            public void execute() {
                PetManagerDialogueDelegate delegate = (PetManagerDialogueDelegate) this.delegate;
                delegate.currentFilter = PetLocationFilter.STORAGE;
                delegate.redrawSelectorPanel();
            }
        }, baseColor, bgColour, Alignment.MID, CutStyle.C2_MENU, SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, 0);
        fleetFilterButton.setEnabled(true);

        filterButtonPanel.addUIElement(anchor).leftOfMid(lastUsed,10f);
        lastUsed = anchor;

        ButtonAPI cargoFilterButton = anchor.addButton("Cargo", new ButtonAction(this) {
            @Override
            public void execute() {
                PetManagerDialogueDelegate delegate = (PetManagerDialogueDelegate) this.delegate;
                delegate.currentFilter = PetLocationFilter.CARGO;
                delegate.redrawSelectorPanel();
            }
        }, baseColor, bgColour, Alignment.MID, CutStyle.C2_MENU, SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, 0);
        fleetFilterButton.setEnabled(true);

        filterButtonPanel.addUIElement(anchor).leftOfMid(lastUsed,10f);
        lastUsed = anchor;

        panel.addComponent(filterButtonPanel).inTL(0.0F, 0.0F);

        //------- selection panel

        //create a list with buttons listing each pet on the ship
        //get the list depending on the selected filter
        //this panel has to be stored and re-drawn depending on the filter so we gotta store it somewhere (as well as the hook for it)
/*
        for (SubIndustryAPI sub : subIndustries) {
            if (industry instanceof SwitchableIndustryAPI && sub == ((SwitchableIndustryAPI) industry).getCurrent())
                continue;
            if (!(industry instanceof SwitchableIndustryAPI) && sub.isBase()) continue;

            int buildTime = Math.round(sub.getBuildTime());
            float cost = sub.getCost();
            boolean canAfford = Global.getSector().getPlayerFleet().getCargo().getCredits().get() >= cost;

            Color baseColor = Misc.getButtonTextColor();
            Color bgColour = Misc.getDarkPlayerColor();
            Color brightColor = Misc.getBrightPlayerColor();

            if (!canAfford) {
                baseColor = Color.darkGray;
                bgColour = Color.lightGray;
                brightColor = Color.gray;
            }

            CustomPanelAPI subIndustryButtonPanel = panel.createCustomPanel(ENTRY_WIDTH, ENTRY_HEIGHT + 2f, new ButtonReportingCustomPanel(this));
            anchor = subIndustryButtonPanel.createUIElement(ENTRY_WIDTH, ENTRY_HEIGHT, false);

            ButtonAPI areaCheckbox = anchor.addAreaCheckbox("", sub.getId(), baseColor, bgColour, brightColor, //new Color(255,255,255,0)
                    ENTRY_WIDTH,
                    ENTRY_HEIGHT,
                    0f,
                    true);

            areaCheckbox.setChecked(selectedPet == sub);
            areaCheckbox.setEnabled(canAfford);
            subIndustryButtonPanel.addUIElement(anchor).inTL(-opad, 0f); //if we don't -opad it kinda does it by its own, no clue why

            String spriteName = sub.getImageName(industry.getMarket());
            SpriteAPI sprite = Global.getSettings().getSprite(spriteName);
            float aspectRatio = sprite.getWidth() / sprite.getHeight();
            float adjustedWidth = CONTENT_HEIGHT * aspectRatio;
            float defaultPadding = (ENTRY_HEIGHT - CONTENT_HEIGHT) / 2;

            anchor = subIndustryButtonPanel.createUIElement(adjustedWidth, ENTRY_HEIGHT, false);
            anchor.addImage(spriteName, adjustedWidth, CONTENT_HEIGHT, 0f);
            subIndustryButtonPanel.addUIElement(anchor).inTL(defaultPadding - opad, defaultPadding);

            TooltipMakerAPI lastPos = anchor;

            anchor = subIndustryButtonPanel.createUIElement(ENTRY_WIDTH - adjustedWidth - opad - defaultPadding, CONTENT_HEIGHT, false);
            if (canAfford) anchor.addSectionHeading(" " + sub.getName(), Alignment.LMID, 0f);
            else anchor.addSectionHeading(" " + sub.getName(), Color.WHITE, brightColor, Alignment.LMID, 0f);
            anchor.addPara(sub.getDescription().getText2(), opad);
            anchor.addPara("Cost: %s", spad, canAfford ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor(), Misc.getDGSCredits(cost)).setAlignment(Alignment.RMID);
            //anchor.addPara("Build time: %s", spad, Misc.getHighlightColor(), buildTime + " " + StringHelper.getDayOrDays(buildTime));

            subIndustryButtonPanel.addUIElement(anchor).rightOfMid(lastPos, opad);

            filterButtonPanel.addCustom(subIndustryButtonPanel, 0f);
            petAreaCheckboxes.add(areaCheckbox);
        }

        panel.addUIElement(filterButtonPanel).inTL(0.0F, 0.0F);*/

    }

    public void redrawSelectorPanel(){
        selectedPet = null;

    }

    public List<Pet> getPets(PetLocationFilter filter){
        PetStatusManager manager = PetStatusManager.getInstance();
        List<Pet> petList = new ArrayList<>();

        switch (filter) {
            case FLEET:
                for (FleetMemberAPI m : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()){
                    Pet pet = manager.getPet(m.getVariant());
                    if (pet != null) petList.add(pet);
                }
                break;
            case STORAGE:
                petList.addAll(((PetShop) industry).getStoredPetsPetsCopy());
                break;
            case CARGO:
                for (SubmarketAPI sub : industry.getMarket().getSubmarketsCopy()){
                    if (sub.getPlugin().isFreeTransfer()){
                        sub.getCargo().initMothballedShips(Factions.PLAYER);

                        for (FleetMemberAPI m : sub.getCargo().getMothballedShips().getMembersListCopy()){
                            Pet pet = manager.getPet(m.getVariant());
                            if (pet != null) petList.add(pet);
                        }
                    }
                }
                break;
        }

        return petList;
    }

    public void customDialogConfirm() {
        //on leave - do
    }

    public void reportButtonPressed(Object id) {
        if (id instanceof ButtonAction) ((ButtonAction) id).execute();
        else for (ButtonAPI button : petAreaCheckboxes) {
            if (button.isChecked() && button.getCustomData() != id) button.setChecked(false);
        }
    }

    public boolean hasCancelButton() {
        return false;
    }

    public String getConfirmText() {
        return "Return";
    }

    @Override
    public String getCancelText() {
        return null;
    }

    public void customDialogCancel() {
    }

    public CustomUIPanelPlugin getCustomPanelPlugin() {
        return null;
    }


    public static class ButtonReportingCustomPanel extends BaseCustomUIPanelPlugin {
        public PetManagerDialogueDelegate delegate;

        public ButtonReportingCustomPanel(PetManagerDialogueDelegate delegate) {
            this.delegate = delegate;
        }

        @Override
        public void buttonPressed(Object buttonId) {
            super.buttonPressed(buttonId);
            delegate.reportButtonPressed(buttonId);
        }
    }


    public interface ButtonActionInterface {
        void execute();
    }
    public abstract static class ButtonAction implements ButtonActionInterface {
        CustomDialogDelegate delegate;

        public ButtonAction(CustomDialogDelegate delegate){
            this.delegate = delegate;
        }
    }
}
