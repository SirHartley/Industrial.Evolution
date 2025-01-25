package indevo.other;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.campaign.listeners.ShowLootListener;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Items;

public class RemnantStationLootAdder implements ShowLootListener {

    public static void register(){
        ListenerManagerAPI manager = Global.getSector().getListenerManager();
        if (manager.hasListenerOfClass(RemnantStationLootAdder.class)) return;

        manager.addListener(new RemnantStationLootAdder(), true);
    }

    @Override
    public void reportAboutToShowLootToPlayer(CargoAPI loot, InteractionDialogAPI dialog) {
        if (dialog.getInteractionTarget() instanceof CampaignFleetAPI) {
            CampaignFleetAPI fleet = (CampaignFleetAPI) dialog.getInteractionTarget();

            if (!Factions.REMNANTS.equals(fleet.getFaction().getId())) return; //no parts if pirates are involved

            boolean hasOrHadStation = false;

            for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()){
                if (member.isStation()) {
                    hasOrHadStation = true;
                    break;
                }
            }

            if (!hasOrHadStation) for (FleetMemberAPI member : fleet.getFleetData().getSnapshot()) {
                if (member.isStation()) {
                    hasOrHadStation = true;
                    break;
                }
            }

            if (hasOrHadStation){
                loot.addSpecial(new SpecialItemData(Items.SHIP_BP, "YOUR SHIP ID"), 1);
            }
        }
    }
}
