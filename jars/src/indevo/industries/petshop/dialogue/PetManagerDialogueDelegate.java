package indevo.industries.petshop.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.petshop.industry.PetShop;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PetManagerDialogueDelegate implements CustomDialogDelegate {
    //Select a Pet (provide filter buttons, Fleet - Storage - Cargo)
    //Move - Store - Rename - Euthanize
    //if move: fleet picker with valid ships in storage and fleet

    //list of pets,
    // config options on the pet button
    // call another delegate for everything else, who cares
    // on the left, config options on the right

    public static final float WIDTH = 600f;
    public static final float HEIGHT = Global.getSettings().getScreenHeight() - 300f;
    protected static final float BUTTON_HEIGHT = 30, SELECT_BUTTON_WIDTH = 95f;

    public static final float ENTRY_HEIGHT = 100; //MUST be even
    public static final float ENTRY_WIDTH = WIDTH - 5f; //MUST be even
    public static final float CONTENT_HEIGHT = 96;

    public Industry industry;
    public PetLocationFilter currentFilter;
    public PetShopDialogPlugin dialog;

    public boolean dismissOnNextCancel = false;

    public enum PetLocationFilter{
        FLEET,
        STORAGE,
        CARGO,
        ALL
    }

    public PetManagerDialogueDelegate(PetShopDialogPlugin dialog, Industry industry, PetLocationFilter filter) {
        this.industry = industry;
        this.currentFilter = filter;
        this.dialog = dialog;
    }

    public void createCustomDialog(CustomPanelAPI panel, final CustomDialogCallback callback) {

        float opad = 10f;
        float spad = 5f;

        Color baseColor = Misc.getButtonTextColor();
        Color bgColour = Misc.getDarkPlayerColor();
        Color brightColor = Misc.getBrightPlayerColor();

        //--------- Add storage selection ---------

        TooltipMakerAPI selectorTooltip = panel.createUIElement(WIDTH, BUTTON_HEIGHT + 10f + opad, false);
        selectorTooltip.addSectionHeading("Select storage location", Alignment.MID, 0f);

        CustomPanelAPI selectorPanel = panel.createCustomPanel(WIDTH, BUTTON_HEIGHT + opad, new ButtonReportingCustomPanel(this));
        TooltipMakerAPI selectorAnchor = selectorPanel.createUIElement(SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, false);

        //fist selector button
        ButtonAPI allFilterButton = selectorAnchor.addAreaCheckbox(Misc.ucFirst(PetLocationFilter.ALL.name().toLowerCase()), new ButtonAction(this) {
            @Override
            public void execute() {
                dismissOnNextCancel = false;
                callback.dismissCustomDialog(1);
                dialog.displaySelectionDelegate(PetLocationFilter.ALL);
            }
        }, baseColor, bgColour, brightColor,  SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, 0);

        allFilterButton.setChecked(currentFilter == PetLocationFilter.ALL);

        selectorPanel.addUIElement(selectorAnchor).inLMid(-opad); //if we don't -opad it kinda does it by its own, no clue why
        TooltipMakerAPI lastUsedAnchor = selectorAnchor;

        for (final PetLocationFilter filter : Arrays.asList(PetLocationFilter.FLEET,PetLocationFilter.CARGO,PetLocationFilter.STORAGE)){
            selectorAnchor = selectorPanel.createUIElement(SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, false);

            //fist selector button
            ButtonAPI filterButton = selectorAnchor.addAreaCheckbox(Misc.ucFirst(filter.name().toLowerCase()), new ButtonAction(this) {
                @Override
                public void execute() {
                    dismissOnNextCancel = false;
                    callback.dismissCustomDialog(1);
                    dialog.displaySelectionDelegate(filter);
                }
            }, baseColor, bgColour, brightColor,  SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, 0);

            filterButton.setChecked(currentFilter == filter);

            selectorPanel.addUIElement(selectorAnchor).rightOfMid(lastUsedAnchor, opad);
            lastUsedAnchor = selectorAnchor;
        }

        //create tooltip from panel 1 P1 (TT1)
        //create new panel from P1 (P2)
        //create UI Elements from P2 and add them to P2
        //add P2 to TT1 via addCustom
        //add TT1 to P1 via addUIElement

        selectorTooltip.addCustom(selectorPanel, 0f);
        panel.addUIElement(selectorTooltip).inTL(0.0F, 0.0F);

        //--------- Add pet list ---------

        TooltipMakerAPI petListTooltip = panel.createUIElement(WIDTH + spad, HEIGHT, true);
        petListTooltip.addSectionHeading("Select a Pet to manage", Alignment.MID, 0f);

        boolean first = true;
        for (final Pet pet : getPets(currentFilter)) {
            CustomPanelAPI petEntryPanel = panel.createCustomPanel(ENTRY_WIDTH + spad, ENTRY_HEIGHT + 2f, new ButtonReportingCustomPanel(this, baseColor, -1));

            //left: Assigned Fleetmember
            //right: title with name
            //Species - Age
            //Move - Store - Rename - Euthanize

            //fleetmember display if needed

            float defaultPadding = (ENTRY_HEIGHT - CONTENT_HEIGHT) / 2;

            TooltipMakerAPI anchor = petEntryPanel.createUIElement(CONTENT_HEIGHT, CONTENT_HEIGHT - BUTTON_HEIGHT - opad, false);
            if (pet.isAssigned()) anchor.addShipList(1,1, CONTENT_HEIGHT, baseColor, Collections.singletonList(pet.assignedFleetMember), 0f);
            else anchor.addImage(Global.getSettings().getSpriteName("IndEvo", "pets_large"), CONTENT_HEIGHT, CONTENT_HEIGHT, 0f);
            petEntryPanel.addUIElement(anchor).inTL(defaultPadding, defaultPadding);

            TooltipMakerAPI lastUsed = anchor;

            if (pet.isAssigned()){
                anchor = petEntryPanel.createUIElement(BUTTON_HEIGHT+2, BUTTON_HEIGHT+2, false);
                ButtonAPI border = anchor.addAreaCheckbox("", null, baseColor, baseColor, baseColor,  BUTTON_HEIGHT+2, BUTTON_HEIGHT+2, 0);
                border.setEnabled(false);
                petEntryPanel.addUIElement(anchor).rightOfBottom(lastUsed, -BUTTON_HEIGHT);

                anchor = petEntryPanel.createUIElement(BUTTON_HEIGHT, BUTTON_HEIGHT, false);
                anchor.addImage(pet.getData().icon, BUTTON_HEIGHT, BUTTON_HEIGHT, 0f);
                petEntryPanel.addUIElement(anchor).rightOfBottom(lastUsed, -BUTTON_HEIGHT+1).setYAlignOffset(1f);
            }

            anchor = petEntryPanel.createUIElement(ENTRY_WIDTH - CONTENT_HEIGHT - spad - defaultPadding, CONTENT_HEIGHT - BUTTON_HEIGHT - opad, false);
            anchor.addSectionHeading(pet.name, Alignment.MID, 0f);
            anchor.addPara("Species: %s, Age: %s", opad, Misc.getHighlightColor(), Misc.ucFirst(pet.getData().species), pet.getAgeString());
            petEntryPanel.addUIElement(anchor).rightOfTop(lastUsed, opad);
            lastUsed = anchor;

            //buttons
            anchor = petEntryPanel.createUIElement(SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, false);
            String name = pet.isAssigned() ? "Re-Assign" : "Assign";
            ButtonAPI moveButton = anchor.addButton(name, new ButtonAction(this) {
                @Override
                public void execute() {
                    dismissOnNextCancel = false;
                    callback.dismissCustomDialog(1);
                    dialog.showShipPicker(pet);
                }
            }, baseColor, bgColour, Alignment.MID, CutStyle.C2_MENU, SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, 0);
            moveButton.setEnabled(true);
            petEntryPanel.addUIElement(anchor).belowLeft(lastUsed, 0f);
            lastUsed = anchor;

            anchor = petEntryPanel.createUIElement(SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, false);
            ButtonAPI storeButton = anchor.addButton("Store", new ButtonAction(this) {
                @Override
                public void execute() {
                    pet.store(industry);
                    Global.getSector().getCampaignUI().getMessageDisplay().addMessage(pet.name + " stored at " + industry.getMarket().getName());
                    dismissOnNextCancel = false;
                    callback.dismissCustomDialog(1);
                    dialog.displaySelectionDelegate();
                }
            }, baseColor, bgColour, Alignment.MID, CutStyle.C2_MENU, SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, 0);
            storeButton.setEnabled(pet.isAssigned());
            petEntryPanel.addUIElement(anchor).rightOfMid(lastUsed, 10f);
            lastUsed = anchor;

            anchor = petEntryPanel.createUIElement(SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, false);
            ButtonAPI renameButton = anchor.addButton("Rename", new ButtonAction(this) {
                @Override
                public void execute() {
                    dismissOnNextCancel = false;
                    callback.dismissCustomDialog(1);
                    dialog.dialog.showCustomDialog(PetRenameDialogueDelegate.WIDTH, PetRenameDialogueDelegate.HEIGHT_200, new PetRenameDialogueDelegate(pet, dialog));
                }
            }, baseColor, bgColour, Alignment.MID, CutStyle.C2_MENU, SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, 0);
            renameButton.setEnabled(true);
            petEntryPanel.addUIElement(anchor).rightOfMid(lastUsed, 10f);
            lastUsed = anchor;

            anchor = petEntryPanel.createUIElement(SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, false);
            ButtonAPI killButton = anchor.addButton("Euthanize", new ButtonAction(this) {
                @Override
                public void execute() {
                    dismissOnNextCancel = false;
                    callback.dismissCustomDialog(1);
                    CustomDialogDelegate delegate = new BaseCustomDialogDelegate() {
                        @Override
                        public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
                            TooltipMakerAPI info = panel.createUIElement(400f, 100f, false);
                            info.addSectionHeading("Confirm your choice", Alignment.MID, 0f);
                            info.addPara("Are you sure? This action can not be undone.", 10f).setAlignment(Alignment.MID);
                            panel.addUIElement(info).inTL(0, 0);
                        }

                        @Override
                        public boolean hasCancelButton() {
                            return true;
                        }

                        @Override
                        public void customDialogConfirm() {
                            PetStatusManager.getInstance().reportPetDied(pet, PetStatusManager.PetDeathCause.UNKNOWN);
                            Global.getSector().getCampaignUI().getMessageDisplay().addMessage(pet.name + " has been euthanized.");
                            dialog.showDelegate = true;
                        }

                        @Override
                        public void customDialogCancel() {
                            dialog.showDelegate = true;
                        }

                        @Override
                        public String getConfirmText() {
                            return "Euthanize";
                        }

                        @Override
                        public String getCancelText() {
                            return "Return";
                        }
                    };

                    dialog.dialog.showCustomDialog(400f, 100f, delegate);
                }
            }, Color.white, new Color(160, 30, 20, 255), Alignment.MID, CutStyle.C2_MENU, SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, 0);
            killButton.setEnabled(true);
            petEntryPanel.addUIElement(anchor).rightOfMid(lastUsed, 10f);
            lastUsed = anchor;

            if (first) petListTooltip.addCustom(petEntryPanel, spad).getPosition().setXAlignOffset(-spad); //why?? WHY?
            else petListTooltip.addCustom(petEntryPanel, spad);
            first = false;
        }

        panel.addUIElement(petListTooltip).belowLeft(selectorTooltip, 0);
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
            default:
                for (FleetMemberAPI m : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()){
                    Pet pet = manager.getPet(m.getVariant());
                    if (pet != null) petList.add(pet);
                }

                petList.addAll(((PetShop) industry).getStoredPetsPetsCopy());

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
        dialog.close();
    }

    public void reportButtonPressed(Object id) {
        if (id instanceof ButtonAction) ((ButtonAction) id).execute();
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
        if (dismissOnNextCancel) dialog.close();
        else dismissOnNextCancel = true;
    }

    public CustomUIPanelPlugin getCustomPanelPlugin() {
        return null;
    }

    public static class ButtonReportingCustomPanel extends BaseCustomUIPanelPlugin {
        public PetManagerDialogueDelegate delegate;
        protected PositionAPI pos;
        public float sideRatio = 0.5f;
        public Color color;
        public float heightOverride = 0f;

        public ButtonReportingCustomPanel(PetManagerDialogueDelegate delegate) {
            this.delegate = delegate;
        }

        public ButtonReportingCustomPanel(PetManagerDialogueDelegate delegate, Color edgeColour, float heightOverride) {
            this.delegate = delegate;
            this.color = edgeColour;
            this.heightOverride = heightOverride;
        }

        @Override
        public void buttonPressed(Object buttonId) {
            super.buttonPressed(buttonId);
            delegate.reportButtonPressed(buttonId);
        }

        @Override
        public void render(float alphaMult) {
            if (color == null) return;

            float x = pos.getX();
            float y = pos.getY();
            float w = pos.getWidth();
            float h = heightOverride > 0f ? heightOverride : pos.getHeight();

            renderBox(x, y, w, h, alphaMult);
        }

        @Override
        public void positionChanged(PositionAPI pos) {
            this.pos = pos;
        }

        public void renderBox(float x, float y, float w, float h, float alphaMult) {
            float lh = h * sideRatio;
            float lw = w * sideRatio;

            float[] points = new float[]{
                    // upper left
                    0, h - lh,
                    0, h,
                    0 + lw, h,

                    // upper right
                    w - lw, h,
                    w, h,
                    w, h - lh,

                    // lower right
                    w, lh,
                    w, 0,
                    w - lw, 0,

                    // lower left
                    lw, 0,
                    0, 0,
                    0, lh
            };

            GL11.glPushMatrix();
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, 0.3f * alphaMult);

            for (int i = 0; i < 4; i++) {
                GL11.glBegin(GL11.GL_LINES);
                {
                    int index = i * 6;

                    GL11.glVertex2f(points[index] + x, points[index + 1] + y);
                    GL11.glVertex2f(points[index + 2] + x, points[index + 3] + y);
                    GL11.glVertex2f(points[index + 2] + x, points[index + 3] + y);
                    GL11.glVertex2f(points[index + 4] + x, points[index + 5] + y);
                }
                GL11.glEnd();
            }

            GL11.glPopMatrix();
        }

        @Override
        public void renderBelow(float alphaMult) {
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
