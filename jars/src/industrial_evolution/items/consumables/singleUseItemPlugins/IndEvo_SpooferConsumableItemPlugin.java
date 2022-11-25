package industrial_evolution.items.consumables.singleUseItemPlugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;
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

        if (mem.contains(MEMKEY_CURRENT_FACTION_LIST)) {
            factionList = (List<String>) mem.get(MEMKEY_CURRENT_FACTION_LIST);
            updateValidFactions(factionList);
        }
        else {
            factionList = new LinkedList<>();
            updateValidFactions(factionList);
        }

        return factionList;
    }

    @Override
    public void render(float x, float y, float w, float h, float alphaMult, float glowMult, SpecialItemRendererAPI renderer) {
        super.render(x, y, w, h, alphaMult, glowMult, renderer);

        SpriteAPI glow = (Global.getSettings().getSprite("fx", "IndEvo_transpoofer_glow"));
        Color glowColor = Global.getSector().getFaction(getCurrentFaction()).getColor();

        glow.setColor(glowColor);
        glow.setAdditiveBlend();
        glow.setAlphaMult(0.5f);
        glow.renderAtCenter(0f, 0f);
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

    public static void updateValidFactions(List<String> factionList){
        for (FactionAPI f : Global.getSector().getAllFactions()){
            String id = f.getId();
            boolean isValidFaction = f.isShowInIntelTab()
                    && !f.isPlayerFaction()
                    && !Misc.getFactionMarkets(id).isEmpty();

            if (isValidFaction && !factionList.contains(id)) factionList.add(id);
        }

        Global.getSector().getMemoryWithoutUpdate().set(MEMKEY_CURRENT_FACTION_LIST, factionList);
    }
}
