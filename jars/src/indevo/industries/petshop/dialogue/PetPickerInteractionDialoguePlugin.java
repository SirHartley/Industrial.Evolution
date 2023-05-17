package indevo.industries.petshop.dialogue;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.ids.ItemIds;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import indevo.industries.petshop.memory.PetData;
import indevo.utils.ModPlugin;
import indevo.utils.helper.Settings;

import java.util.*;

public class PetPickerInteractionDialoguePlugin implements InteractionDialogPlugin {

    public InteractionDialogAPI dialog;
    public PetData data;

    private String name = null;
    private boolean hasShownPicker = false;
    private List<FleetMemberAPI> membersWithoutPets;

    static enum OptionID{
        INIT,
    }

    public PetPickerInteractionDialoguePlugin(PetData petData){
        this.data = petData;

        PetStatusManager manager = PetStatusManager.getInstance();

        membersWithoutPets = new LinkedList<>();
        for (FleetMemberAPI m : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()){
            Pet pet = manager.getPet(m.getVariant());
            if (pet == null) membersWithoutPets.add(m);
        }
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;

        dialog.setPromptText("");
        dialog.hideVisualPanel();
        dialog.hideTextPanel();

        optionSelected(null, OptionID.INIT);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (optionData == OptionID.INIT) {

            dialog.showCustomDialog(PetNameAndAssignDialogueDelegate.WIDTH,
                    PetNameAndAssignDialogueDelegate.HEIGHT_200,
                    new PetNameAndAssignDialogueDelegate(data, membersWithoutPets,dialog));
        }
    }

    public void showShipPicker(){

        int shipsPerRow = Settings.SHIP_PICKER_ROW_COUNT;
        int rows = membersWithoutPets.size() > shipsPerRow ? (int) Math.ceil(membersWithoutPets.size() / (float) shipsPerRow) : 1;
        int cols = Math.min(membersWithoutPets.size(), shipsPerRow);

        cols = Math.max(cols, 4);

        dialog.showFleetMemberPickerDialog("Select a new home", "Confirm", "Cancel", rows,
                cols, 88f, true, false, membersWithoutPets, new FleetMemberPickerListener() {

                    @Override
                    public void pickedFleetMembers(List<FleetMemberAPI> members) {
                        if (members != null && !members.isEmpty()) {
                            FleetMemberAPI newHome = members.get(0);

                            Pet pet = new Pet(data.id, getName());
                            PetStatusManager.getInstance().register(pet);
                            pet.assign(newHome);

                            Global.getSector().getCampaignUI().getMessageDisplay().addMessage("Assigned " + getName() + " to " + newHome.getShipName());//,
                            Global.getSector().getPlayerFleet().getCargo().removeItems(CargoAPI.CargoItemType.SPECIAL, new SpecialItemData(ItemIds.PET_CHAMBER, data.id), 1);

                            close();

                        } else {
                            cancelledFleetMemberPicking();
                        }
                    }

                    @Override
                    public void cancelledFleetMemberPicking() {
                        close();
                    }
                });

    }

    public void close(){
        dialog.dismiss();
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public void optionMousedOver(String optionText, Object optionData) {

    }

    @Override
    public void advance(float amount) {
        if (!hasShownPicker && name != null) showShipPicker();
    }

    @Override
    public void backFromEngagement(EngagementResultAPI battleResult) {

    }

    @Override
    public Object getContext() {
        return null;
    }

    @Override
    public Map<String, MemoryAPI> getMemoryMap() {
        return null;
    }
}
