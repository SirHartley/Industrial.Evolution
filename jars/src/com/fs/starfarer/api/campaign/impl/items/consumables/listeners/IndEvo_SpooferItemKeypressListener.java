package com.fs.starfarer.api.campaign.impl.items.consumables.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.impl.items.consumables.singleUseItemPlugins.IndEvo_SpooferConsumableItemPlugin;
import com.fs.starfarer.api.campaign.listeners.CampaignInputListener;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.input.InputEventType;
import org.lwjgl.input.Keyboard;

import java.util.List;

public class IndEvo_SpooferItemKeypressListener implements CampaignInputListener {

    public static void register(){
        Global.getSector().getListenerManager().addListener(new IndEvo_SpooferItemKeypressListener(), true);
    }

    @Override
    public int getListenerInputPriority() {
        return 0;
    }

    @Override
    public void processCampaignInputPreCore(List<InputEventAPI> events) {
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        if (ui.getCurrentCoreTab() != CoreUITabId.CARGO || ui.getCurrentInteractionDialog() != null) return;

        for (InputEventAPI input : events) {
            if (input.isConsumed()) continue;

            if (input.getEventType().equals(InputEventType.KEY_DOWN)) {
                if (input.getEventValue() == Keyboard.KEY_LEFT) IndEvo_SpooferConsumableItemPlugin.nextFaction();
                if (input.getEventValue() == Keyboard.KEY_RIGHT) IndEvo_SpooferConsumableItemPlugin.prevFaction();
            }
        }
    }

    @Override
    public void processCampaignInputPreFleetControl(List<InputEventAPI> events) {
    }

    @Override
    public void processCampaignInputPostCore(List<InputEventAPI> events) {

    }
}
