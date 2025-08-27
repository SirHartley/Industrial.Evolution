package indevo.industries.engineeringhub.listener;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.listeners.CoreUITabListener;
import indevo.ids.ItemIds;

public class EngineeringHubRelicItemAdder implements CoreUITabListener {

    public static void register(){
        Global.getSector().getListenerManager().addListener(new EngineeringHubRelicItemAdder(), true);
    }

    @Override
    public void reportAboutToOpenCoreTab(CoreUITabId tab, Object param) {
        if (tab == CoreUITabId.OUTPOSTS){
            Global.getSector().getPlayerFleet().getCargo().addSpecial(new SpecialItemData(ItemIds.RELIC_SPECIAL_ITEM, null), 1);
        } else {
            CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
            float quantity = cargo.getQuantity(CargoAPI.CargoItemType.SPECIAL, new SpecialItemData(ItemIds.RELIC_SPECIAL_ITEM, null));
            Global.getSector().getPlayerFleet().getCargo().removeItems(CargoAPI.CargoItemType.SPECIAL, new SpecialItemData(ItemIds.RELIC_SPECIAL_ITEM, null), quantity);
        }
    }
}
