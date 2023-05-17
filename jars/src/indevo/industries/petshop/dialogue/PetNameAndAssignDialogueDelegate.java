package indevo.industries.petshop.dialogue;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.ItemIds;
import indevo.industries.changeling.dialogue.ChangelingIndustryDialogueDelegate;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import indevo.industries.petshop.memory.PetData;

import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class PetNameAndAssignDialogueDelegate implements CustomDialogDelegate {

    public static final float WIDTH = 600f;
    public static final float HEIGHT_200 = 200f;

    private PetData data;
    private InteractionDialogAPI dialogue;
    private List<FleetMemberAPI> members;

    private FleetMemberAPI selected;

    public List<ButtonAPI> buttons = new ArrayList<>();
    public TextFieldAPI nameField = null;

    public PetNameAndAssignDialogueDelegate(PetData data, List<FleetMemberAPI> validMembers, InteractionDialogAPI dialogue) {
       this.data = data;
       this.members = validMembers;
       this.dialogue = dialogue;
    }

    public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
        float opad = 10f;
        float spad = 2f;

        TooltipMakerAPI heading = panel.createUIElement(WIDTH, HEIGHT_200, false);
        heading.addSectionHeading("Name your " + data.species, Alignment.MID, opad);

        CustomPanelAPI subPanel = panel.createCustomPanel(WIDTH, HEIGHT_200 + 2f, new BaseCustomUIPanelPlugin());
        TooltipMakerAPI anchor = subPanel.createUIElement(WIDTH, HEIGHT_200, false);

        TextFieldAPI nameField = anchor.addTextField(WIDTH, opad);
        if (this.nameField != null) nameField.setText(this.nameField.getText());
        this.nameField = nameField;

        anchor.addPara(data.desc, Misc.getGrayColor(), opad);

        subPanel.addUIElement(anchor).inTL(-opad, 0f); //if we don't -opad it kinda does it by its own, no clue why
        heading.addCustom(subPanel, 0f);

        panel.addUIElement(heading).inTL(0f, 0.0F);;
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
        ((PetPickerInteractionDialoguePlugin) dialogue.getPlugin()).setName(nameField.getText());
    }

    public void customDialogCancel() {
        dialogue.dismiss();

        Global.getSector().setPaused(false);
        Global.getSector().addScript(new EveryFrameScript() {
            boolean done = false;

            @Override
            public boolean isDone() {
                return done;
            }

            @Override
            public boolean runWhilePaused() {
                return false;
            }

            @Override
            public void advance(float amount) {
                if (!done) Global.getSector().getCampaignUI().showCoreUITab(CoreUITabId.CARGO);
                done = true;
            }
        });
    }

    public CustomUIPanelPlugin getCustomPanelPlugin() {
        return null;
    }
}
