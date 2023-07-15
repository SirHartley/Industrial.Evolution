package indevo.industries.petshop.dialogue;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import indevo.ids.ItemIds;
import indevo.industries.petshop.listener.EscapeInterceptListener;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import indevo.industries.petshop.memory.PetData;
import indevo.industries.petshop.script.EscBlockerMemoryIterator;
import indevo.utils.ModPlugin;
import indevo.utils.helper.Settings;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static indevo.industries.petshop.listener.EscapeInterceptListener.BLOCK_ESC;

public class PetShopDialogPlugin  implements InteractionDialogPlugin {

    public InteractionDialogAPI dialog;

    private String name = null;
    private Industry industry;
    private PetManagerDialogueDelegate.PetLocationFilter lastFilter = PetManagerDialogueDelegate.PetLocationFilter.ALL;

    static enum OptionID {
        INIT,
    }

    public PetShopDialogPlugin(Industry industry) {
        this.industry = industry;
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        if (!Global.getSector().getListenerManager().hasListenerOfClass(EscapeInterceptListener.class)) Global.getSector().getListenerManager().addListener(new EscapeInterceptListener(), true);

        boolean hasScript = false;
        for (EveryFrameScript s : Global.getSector().getTransientScripts()){
            if (s instanceof EscBlockerMemoryIterator) {
                hasScript = true;
                break;
            }
        }

        if (!hasScript) Global.getSector().addTransientScript(new EscBlockerMemoryIterator());

        dialog.setPromptText("");
        dialog.hideVisualPanel();
        dialog.hideTextPanel();
        optionSelected(null, OptionID.INIT);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (optionData == OptionID.INIT) displaySelectionDelegate();
    }

    public void displayPetRenamePanel(Pet pet){
        dialog.showCustomDialog(PetRenameDialogueDelegate.WIDTH, PetRenameDialogueDelegate.HEIGHT_200, new PetRenameDialogueDelegate(pet, this));
    }

    public void displaySelectionDelegate(){
        dialog.showCustomDialog(PetManagerDialogueDelegate.WIDTH, PetManagerDialogueDelegate.HEIGHT,
                new PetManagerDialogueDelegate(this, industry));
    }

    public void showShipPicker(final Pet pet) {
        PetStatusManager manager = PetStatusManager.getInstance();

        List<FleetMemberAPI> membersWithoutPets = new LinkedList<>();
        for (FleetMemberAPI m : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
            Pet p = manager.getPet(m.getVariant());
            if (p == null) membersWithoutPets.add(m);
        }

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

                            pet.removeFromStorage();
                            pet.unassign();
                            pet.assign(newHome);

                            Global.getSector().getCampaignUI().getMessageDisplay().addMessage("Assigned " + pet.name + " to " + newHome.getShipName());
                            showDelegate = true;

                        } else {
                            showShipPicker(pet);
                        }
                    }

                    @Override
                    public void cancelledFleetMemberPicking() {
                        showDelegate = true;
                    }
                });

    }

    boolean showDelegate = false;

    public void close() {
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
        if (Global.getSector().getCampaignUI().isShowingDialog()) Global.getSector().getMemoryWithoutUpdate().set(BLOCK_ESC, this, 1f);

        if (showDelegate) {
            displaySelectionDelegate();
            showDelegate = false;
        }
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
