package indevo.items.consumables.dialogue;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.petshop.dialogue.PetPickerInteractionDialoguePlugin;
import indevo.industries.petshop.memory.PetData;
import indevo.items.consumables.itemAbilities.BeaconAbilityPlugin;

import java.util.ArrayList;
import java.util.List;

public class BeaconMessageSetterDialogueDelegate implements CustomDialogDelegate {
    public static final float WIDTH = 600f;
    public static final float HEIGHT_200 = 200f;

    private InteractionDialogAPI dialogue;
    private BeaconAbilityPlugin plugin;
    public TextFieldAPI nameField = null;

    public BeaconMessageSetterDialogueDelegate(InteractionDialogAPI dialogue, BeaconAbilityPlugin plugin) {
        this.dialogue = dialogue;
        this.plugin = plugin;
    }

    public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
        float opad = 10f;
        float spad = 2f;

        TooltipMakerAPI heading = panel.createUIElement(WIDTH, HEIGHT_200, false);
        heading.addSectionHeading("Specify your broadcast message", Alignment.MID, opad);

        CustomPanelAPI subPanel = panel.createCustomPanel(WIDTH, HEIGHT_200 + 2f, new BaseCustomUIPanelPlugin());
        TooltipMakerAPI anchor = subPanel.createUIElement(WIDTH, HEIGHT_200, false);

        TextFieldAPI nameField = anchor.addTextField(WIDTH, opad);
        if (this.nameField != null) nameField.setText(this.nameField.getText());
        this.nameField = nameField;

        anchor.addPara("Specify the message you would like the beacon to broadcast. It will show up on the beacon and the intel entry.", Misc.getGrayColor(), opad);

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
        plugin.spawnBeacon(nameField.getText());
        dialogue.dismiss();
    }

    public void customDialogCancel() {
        plugin.spawnBeacon("");
        dialogue.dismiss();
    }

    public CustomUIPanelPlugin getCustomPanelPlugin() {
        return null;
    }
}
