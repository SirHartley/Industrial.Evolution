package indevo.items.consumables.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.ids.ItemIds;
import indevo.utils.ModPlugin;

public class ConsumableItemMarketAdder extends BaseCampaignEventListener {

    public ConsumableItemMarketAdder(boolean permaRegister) {
        super(permaRegister);
    }

    public static void register() {
        Global.getSector().addTransientListener(new ConsumableItemMarketAdder(false));
    }

    @Override
    public void reportPlayerOpenedMarketAndCargoUpdated(MarketAPI market) {
        super.reportPlayerOpenedMarketAndCargoUpdated(market);

        for (SubmarketAPI sub : market.getSubmarketsCopy()) {
            if (sub.getPlugin() instanceof BaseSubmarketPlugin && !Submarkets.SUBMARKET_STORAGE.equals(sub.getSpecId()) && ((BaseSubmarketPlugin) sub.getPlugin()).getSinceSWUpdate() == 0) {
                ModPlugin.log("updating " + sub.getNameOneLine() + " on " + sub.getMarket().getName());
                updateSubmarketCargo(sub);
            }
        }
    }

    public void updateSubmarketCargo(SubmarketAPI submarket) {
        MarketAPI market = submarket.getMarket();
        if (market == null) return;

        CargoAPI cargo = submarket.getCargo();
        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            if (stack.isSpecialStack() && stack.getSpecialDataIfSpecial().getId().contains("IndEvo_consumable_"))
                cargo.removeStack(stack);
        }

        String id = submarket.getSpecId();
        boolean isMil = id.equals(Submarkets.GENERIC_MILITARY);
        String faction = market.getFactionId();
        boolean isBoringSubmarket = submarket.isIllegalOnSubmarket(Global.getFactory().createCargoStack(CargoAPI.CargoItemType.RESOURCES, Commodities.LUXURY_GOODS, null), SubmarketPlugin.TransferAction.PLAYER_SELL);
        boolean isOpenMilOrBlack = id.equals(Submarkets.SUBMARKET_OPEN) || id.equals(Submarkets.SUBMARKET_BLACK) || id.equals(Submarkets.GENERIC_MILITARY);

        if (isOpenMilOrBlack && !isBoringSubmarket) {
            WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
            picker.add(ItemIds.CONSUMABLE_NANITES, 10);
            if (faction.equals(Factions.INDEPENDENT)) picker.add(ItemIds.CONSUMABLE_LOCATOR, 5);
            if (isMil) picker.add(ItemIds.CONSUMABLE_SPIKE, 20);
            picker.add(ItemIds.CONSUMABLE_SCOOP, 10);
            if (faction.equals(Factions.PIRATES)) {
                picker.add(ItemIds.CONSUMABLE_DECOY, 5);
                picker.add(ItemIds.CONSUMABLE_SPOOFER, 5);
            }
            picker.add("nothing", 300);

            for (int i = 0; i < 6; i++) {
                String itemId = picker.pick();
                if (!itemId.equals("nothing")) cargo.addSpecial(new SpecialItemData(itemId, null), 1f);
            }

            ((BaseSubmarketPlugin) submarket.getPlugin()).setSinceSWUpdate(0.001f);
        }
    }
}
