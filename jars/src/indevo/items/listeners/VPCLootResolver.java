package indevo.items.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.listeners.ShowLootListener;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.Misc;
import indevo.items.VPCItemPlugin;
import indevo.items.VPCItemPlugin.Tier;

import java.util.Random;

public class VPCLootResolver implements ShowLootListener {

    public static void register() {
        if (!Global.getSector().getListenerManager().hasListenerOfClass(VPCLootResolver.class))
            Global.getSector().getListenerManager().addListener(new VPCLootResolver(), true);
    }

    @Override
    public void reportAboutToShowLootToPlayer(CargoAPI loot, InteractionDialogAPI dialog) {
        Random random = null;

        for (CargoStackAPI stack : loot.getStacksCopy()) {
            SpecialItemData data = stack.getSpecialDataIfSpecial();
            if (!VPCItemPlugin.isVPC(data) || VPCItemPlugin.isValid(data)) continue;

            Tier tier = VPCItemPlugin.getTier(data);
            if (tier == null || !tier.itemId.equals(data.getId())) tier = Tier.getLowest(data.getId());
            if (random == null) random = getRandom(dialog);

            int count = (int) stack.getSize();
            for (int i = 0; i < count; i++) {
                SpecialItemData item = VPCItemPlugin.roll(tier, random);
                if (item == null) break;

                loot.removeItems(CargoAPI.CargoItemType.SPECIAL, data, 1);
                loot.addSpecial(item, 1);
            }
        }
    }

    // vanilla salvage is seeded, so a reload rolls the same outputs
    private static Random getRandom(InteractionDialogAPI dialog) {
        SectorEntityToken entity = dialog != null ? dialog.getInteractionTarget() : null;
        long seed = entity != null ? entity.getMemoryWithoutUpdate().getLong(MemFlags.SALVAGE_SEED) : 0L;
        return Misc.getRandom(seed, 11);
    }
}
