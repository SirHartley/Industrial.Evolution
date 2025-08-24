package indevo.industries.museum.plugins;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FleetMemberPickerListener;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import indevo.dialogue.sidepanel.EscapeBlockingDialoguePluginAPI;
import indevo.industries.museum.data.ParadeFleetProfile;
import indevo.industries.museum.industry.Museum;
import indevo.industries.petshop.listener.EscapeInterceptListener;
import indevo.industries.petshop.script.EscBlockerMemoryIterator;
import indevo.utils.helper.Settings;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static indevo.industries.petshop.listener.EscapeInterceptListener.BLOCK_ESC;

public class ManageParadeDialoguePlugin implements InteractionDialogPlugin, EscapeBlockingDialoguePluginAPI {

    public InteractionDialogAPI dialog;
    private Museum museum;

    public boolean showDelegate = false;
    public ParadeManagementDialogueDelegate delegate;

    public ManageParadeDialoguePlugin(Industry museum) {
        this.museum = (Museum) museum;
        this.delegate = new ParadeManagementDialogueDelegate((Museum) museum, this);
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        if (!Global.getSector().getListenerManager().hasListenerOfClass(EscapeInterceptListener.class))
            Global.getSector().getListenerManager().addListener(new EscapeInterceptListener(), true);

        boolean hasScript = false;
        for (EveryFrameScript s : Global.getSector().getTransientScripts()) {
            if (s instanceof EscBlockerMemoryIterator) {
                hasScript = true;
                break;
            }
        }

        if (!hasScript) Global.getSector().addTransientScript(new EscBlockerMemoryIterator());

        dialog.setPromptText("");
        dialog.hideVisualPanel();
        dialog.hideTextPanel();
        displayMainPanel();
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
    }

    public void displayMainPanel() {
        dialog.showCustomDialog(MuseumManageParadeOptionProvider.WIDTH, MuseumManageParadeOptionProvider.HEIGHT,
                delegate);
    }

    public void showShipPicker(final ParadeFleetProfile profile) {
        SubmarketAPI sub = museum.getSubmarket();
        if (sub == null) {
            museum.advance(0);
            sub = museum.getSubmarket();
        }

        CargoAPI cargo = sub.getCargo();
        cargo.initMothballedShips(Factions.PLAYER);
        List<FleetMemberAPI> paradeMembers = new LinkedList<>(cargo.getMothballedShips().getMembersListCopy());

        int shipsPerRow = Settings.getInt(Settings.SHIP_PICKER_ROW_COUNT);
        int rows = paradeMembers.size() > shipsPerRow ? (int) Math.ceil(paradeMembers.size() / (float) shipsPerRow) : 1;
        int cols = Math.min(paradeMembers.size(), shipsPerRow);

        cols = Math.max(cols, 4);

        dialog.showFleetMemberPickerDialog("Select ships for your parade", "Confirm", "Cancel", rows,
                cols, 88f, true, true, paradeMembers, new FleetMemberPickerListener() {

                    @Override
                    public void pickedFleetMembers(List<FleetMemberAPI> members) {
                        if (members != null && !members.isEmpty()) {

                            List<String> memberIdList = new ArrayList<>();
                            for (FleetMemberAPI m : members) memberIdList.add(m.getId());

                            profile.setMemberList(memberIdList);

                            Global.getSector().getCampaignUI().getMessageDisplay().addMessage("Assigned " + memberIdList.size() + " ships to the parade.");
                        }

                        showDelegate = true;
                    }

                    @Override
                    public void cancelledFleetMemberPicking() {
                        showDelegate = true;
                    }
                });

    }

    public void close() {
        dialog.dismiss();
    }

    @Override
    public void optionMousedOver(String optionText, Object optionData) {

    }

    @Override
    public void advance(float amount) {
        if (Global.getSector().getCampaignUI().isShowingDialog())
            Global.getSector().getMemoryWithoutUpdate().set(BLOCK_ESC, this, 1f);

        if (showDelegate) {
            displayMainPanel();
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