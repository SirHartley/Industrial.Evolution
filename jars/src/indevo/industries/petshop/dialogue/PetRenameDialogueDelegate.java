package indevo.industries.petshop.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomDialogDelegate;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.petshop.memory.Pet;

public class PetRenameDialogueDelegate implements CustomDialogDelegate {

    public static final float WIDTH = 600f;
    public static final float HEIGHT_200 = 200f;

    private Pet pet;
    private PetShopDialogPlugin dialogue;
    public TextFieldAPI nameField = null;

    public PetRenameDialogueDelegate(Pet pet, PetShopDialogPlugin dialogue) {
        this.pet = pet;
        this.dialogue = dialogue;
    }

    public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
        float opad = 10f;
        float spad = 2f;

        TooltipMakerAPI heading = panel.createUIElement(WIDTH, HEIGHT_200, false);
        heading.addSectionHeading("Rename your " + pet.getData().species, Alignment.MID, opad);

        CustomPanelAPI subPanel = panel.createCustomPanel(WIDTH, HEIGHT_200 + 2f, new BaseCustomUIPanelPlugin());
        TooltipMakerAPI anchor = subPanel.createUIElement(WIDTH, HEIGHT_200, false);

        TextFieldAPI nameField = anchor.addTextField(WIDTH, opad);
        if (this.nameField != null) nameField.setText(this.nameField.getText());
        this.nameField = nameField;

        anchor.addPara("Current name: %s", opad, Misc.getHighlightColor(), pet.name);
        anchor.addPara(pet.getData().desc, Misc.getGrayColor(), opad);

        subPanel.addUIElement(anchor).inTL(-opad, 0f); //if we don't -opad it kinda does it by its own, no clue why
        heading.addCustom(subPanel, 0f);

        panel.addUIElement(heading).inTL(0f, 0.0F);
    }

    public boolean hasCancelButton() {
        return true;
    }

    public String getConfirmText() {
        return "Confirm";
    }

    public String getCancelText() {
        return "Cancel";
    }

    public void customDialogConfirm() {
        Global.getSector().getCampaignUI().getMessageDisplay().addMessage(pet.name + " renamed to " + nameField.getText());
        pet.name = nameField.getText();
        dialogue.showDelegate = true;
    }

    public void customDialogCancel() {
        dialogue.showDelegate = true;
    }

    public CustomUIPanelPlugin getCustomPanelPlugin() {
        return null;
    }
}
