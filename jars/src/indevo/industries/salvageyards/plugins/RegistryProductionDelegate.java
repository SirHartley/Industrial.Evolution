package indevo.industries.salvageyards.plugins;

import com.fs.starfarer.api.campaign.CustomProductionPickerDelegate;
import com.fs.starfarer.api.campaign.FactionProductionAPI;

import java.util.Set;

// picker delegate of a yards with the Salvage Drone Replicator, read by the salvageyards patches
public interface RegistryProductionDelegate extends CustomProductionPickerDelegate {

    String TAB_LABEL = "Registry hulls";

    Set<String> getRegistryHulls();

    // Integer.MAX_VALUE when the item has no limit
    int getMaxQuantity(FactionProductionAPI production, FactionProductionAPI.ProductionItemType type, String id);

    String getLimitMessage(FactionProductionAPI production, FactionProductionAPI.ProductionItemType type, String id);
}
