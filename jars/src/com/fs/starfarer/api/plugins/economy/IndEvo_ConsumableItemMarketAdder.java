package com.fs.starfarer.api.plugins.economy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.SubmarketPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.campaign.listeners.SubmarketUpdateListener;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.IndEvo_Items;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.util.WeightedRandomPicker;

public class IndEvo_ConsumableItemMarketAdder implements SubmarketUpdateListener {

    public static void register() {
        ListenerManagerAPI manager = Global.getSector().getListenerManager();
        if (!manager.hasListenerOfClass(IndEvo_ConsumableItemMarketAdder.class))
            manager.addListener(new IndEvo_ConsumableItemMarketAdder(), true);
    }

    @Override
    public void reportSubmarketCargoAndShipsUpdated(SubmarketAPI submarket) {
        MarketAPI market = submarket.getMarket();
        if (market == null) return;

        CargoAPI cargo = submarket.getCargo();
        for (CargoStackAPI stack : cargo.getStacksCopy()){
            if (stack.isSpecialStack() && stack.getSpecialDataIfSpecial().getId().contains("IndEvo_consumable_")) cargo.removeStack(stack);
        }

        String id = submarket.getSpecId();
        boolean isMil = id.equals(Submarkets.GENERIC_MILITARY);
        String faction = market.getFactionId();
        boolean isBoringSubmarket = submarket.isIllegalOnSubmarket(Global.getFactory().createCargoStack(CargoAPI.CargoItemType.RESOURCES, Commodities.LUXURY_GOODS, null), SubmarketPlugin.TransferAction.PLAYER_SELL);
        boolean isOpenMilOrBlack = id.equals(Submarkets.SUBMARKET_OPEN) || id.equals(Submarkets.SUBMARKET_BLACK) ||id.equals(Submarkets.GENERIC_MILITARY);

        if(isOpenMilOrBlack && !isBoringSubmarket){
            WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
            picker.add(IndEvo_Items.CONSUMABLE_NANITES, 10);
            if (faction.equals(Factions.INDEPENDENT)) picker.add(IndEvo_Items.CONSUMABLE_LOCATOR, 5);
            if (isMil) picker.add(IndEvo_Items.CONSUMABLE_SPIKE, 20);
            picker.add(IndEvo_Items.CONSUMABLE_SCOOP, 10);
            if (faction.equals(Factions.PIRATES)) {
                picker.add(IndEvo_Items.CONSUMABLE_DECOY, 5);
                picker.add(IndEvo_Items.CONSUMABLE_SPOOFER, 5);
            }
            picker.add("nothing", 300);

            for (int i = 0; i < 6; i++){
                String itemId = picker.pick();
                if (!itemId.equals("nothing")) cargo.addSpecial(new SpecialItemData(itemId, null), 1f);
            }
        }
    }
}
