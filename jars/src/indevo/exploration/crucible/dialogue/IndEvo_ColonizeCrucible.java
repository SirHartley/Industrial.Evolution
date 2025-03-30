package indevo.exploration.crucible.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.MusicPlayerPluginImpl;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.impl.campaign.submarkets.StoragePlugin;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

public class IndEvo_ColonizeCrucible extends BaseCommandPlugin {
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        SectorEntityToken crucible = dialog.getInteractionTarget();

        String name = "The Crucible";
        MarketAPI market = Global.getFactory().createMarket(Misc.genUID(), name, 3);
        market.setName(name);
        market.setHidden(false);
        market.setFactionId(Factions.PLAYER);
        market.setPlayerOwned(true);
        market.setSurveyLevel(MarketAPI.SurveyLevel.FULL);

        market.addCondition(Conditions.POPULATION_3);
        market.addCondition(Conditions.RUINS_VAST);
        market.addIndustry(Industries.POPULATION);

        market.setDaysInExistence(0);
        market.setPlanetConditionMarketOnly(false);

        market.addSubmarket(Submarkets.SUBMARKET_STORAGE);
        ((StoragePlugin)market.getSubmarket(Submarkets.SUBMARKET_STORAGE).getPlugin()).setPlayerPaidToUnlock(true);

        market.addSubmarket(Submarkets.LOCAL_RESOURCES);
        market.getTariff().modifyFlat("default_tariff", market.getFaction().getTariffFraction());

        market.setPrimaryEntity(crucible);
        crucible.setMarket(market);
        crucible.setFaction(Factions.PLAYER);



        market.getMemoryWithoutUpdate().set(MusicPlayerPluginImpl.MUSIC_SET_MEM_KEY, "IndEvo_Haplogynae_derelict_theme");
        crucible.getMemoryWithoutUpdate().set(MusicPlayerPluginImpl.MUSIC_SET_MEM_KEY, "IndEvo_Haplogynae_derelict_theme");

        Global.getSector().getEconomy().addMarket(market, true);
        Global.getSector().getEconomy().tripleStep();
        market.advance(0f);

        market.getConstructionQueue().addToEnd(Industries.SPACEPORT, 0);

        return true;
    }
}
