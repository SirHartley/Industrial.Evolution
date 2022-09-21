package com.fs.starfarer.api.campaign.impl.items.consumables.singleUseItemPlugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.CargoTransferHandlerAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.LinkedList;
import java.util.List;

public class IndEvo_SpooferConsumableItemPlugin extends IndEvo_BaseConsumableItemPlugin {

    public static final String MEMKEY_CURRENT_FACTION = "$IndEvo_consumable_currentFaction";
    public static final String MEMKEY_CURRENT_FACTION_LIST = "$IndEvo_consumable_currentFactionList";

    @Override
    public void init(CargoStackAPI stack) {
        super.init(stack);
        if(!Global.getSector().getMemoryWithoutUpdate().contains(MEMKEY_CURRENT_FACTION)) setCurrentFaction(0);
    }

    public static void setCurrentFaction(int i){
        Global.getSector().getMemoryWithoutUpdate().set(MEMKEY_CURRENT_FACTION, i);
    }

    public static int getCurrentFactionIndex(){
        return Global.getSector().getMemoryWithoutUpdate().getInt(MEMKEY_CURRENT_FACTION);
    }

    public static String getCurrentFaction(){
        return getFactionList().get(getCurrentFactionIndex());
    }

    public static List<String> getFactionList(){
        List<String> factionList;
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();

        if (mem.contains(MEMKEY_CURRENT_FACTION_LIST)) factionList = (List<String>) mem.get(MEMKEY_CURRENT_FACTION_LIST);
        else {
            factionList = new LinkedList<>();
            updateValidFactions();
            mem.set(MEMKEY_CURRENT_FACTION_LIST, factionList);
        }

        return factionList;
    }

    public static void nextFaction(){
        int max = getFactionList().size() - 1;
        int next = getCurrentFactionIndex() + 1;

        if (next > max) setCurrentFaction(0);
        else setCurrentFaction(next);
    }

    public static void prevFaction(){
        int max = getFactionList().size() - 1;
        int next = getCurrentFactionIndex() - 1;

        if (next < 0) setCurrentFaction(max);
        else setCurrentFaction(next);
    }

    public static void updateValidFactions(){
        List<String> factionList = getFactionList();

        for (FactionAPI f : Global.getSector().getAllFactions()){
            String id = f.getId();
            boolean isValidFaction = f.isShowInIntelTab()
                    && !f.isPlayerFaction()
                    && !Misc.getFactionMarkets(id).isEmpty();

            if (isValidFaction && !factionList.contains(id)) factionList.add(id);
        }

        Global.getSector().getMemoryWithoutUpdate().set(MEMKEY_CURRENT_FACTION_LIST, factionList);
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, CargoTransferHandlerAPI transferHandler, Object stackSource) {
        super.createTooltip(tooltip, expanded, transferHandler, stackSource);
        float opad = 10f;
        float spad = 3f;

        FactionAPI faction = Global.getSector().getFaction(getCurrentFaction());
        tooltip.addPara("Spoofing " + faction.getPersonNamePrefixAOrAn() + " %s transponder.", opad, faction.getColor(), faction.getDisplayName());
        tooltip.addPara("[Use arrow keys to change the faction]", spad, Misc.getGrayColor());
    }
}
