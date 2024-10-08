package indevo.industries.ruinfra.conditions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.econ.BaseHazardCondition;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.ids.Ids;
import indevo.utils.helper.MiscIE;
import org.apache.log4j.Logger;

import java.util.Random;

public class DerelictInfrastructureCondition extends BaseHazardCondition {
    public static final Logger log = Global.getLogger(DerelictInfrastructureCondition.class);
    public static final String RUINED_INFRA_UPGRADE_ID_KEY = "$IndEvo_RuinedInfraIndId";

    public void apply(String id) {
        super.apply(id);

        if (!market.getMemoryWithoutUpdate().getBoolean("$isPlanetConditionMarketOnly")
                && !isRuinfraConditionSet()
                && market.getFaction() != null
                && !market.getFactionId().equals("neutral")
                && !market.isPlanetConditionMarketOnly()) {

            setUpgradeSpec(market);
            addRuinfraIfNeeded();
        }
    }

    private static boolean isAvailableToBuild(String id, MarketAPI market) {
        return Global.getSettings().getIndustrySpec(id) != null && Global.getSettings().getIndustrySpec(id).getNewPluginInstance(market).isAvailableToBuild();
    }

    public void unapply(String id) {
        super.unapply(id);
    }

    @Override
    public void advance(float amount) {
        removeConditionIfRuinsNotPresent();
    }

    public static void setUpgradeSpec(MarketAPI market) {
        WeightedRandomPicker<String> industryIdPicker = new WeightedRandomPicker<>();
        industryIdPicker.addAll(MiscIE.getCSVSetFromMemory(Ids.RUIND_LIST));

        Random random = new Random(Misc.getSalvageSeed(market.getPlanetEntity()));
        String pickedId = industryIdPicker.pick(random);
        log.info("picked " + pickedId);

        while (pickedId != null && !isAvailableToBuild(pickedId, market)) {
            pickedId = industryIdPicker.pickAndRemove();

            log.info("repicked " + pickedId);
        }

        if (pickedId == null) pickedId = Ids.ADASSEM;

        setUpgradeSpec(market, pickedId);
    }

    /**
     * reset via resetRuinfraState() before setting manually
     */
    public static void setUpgradeSpec(MarketAPI market, String id){
        MemoryAPI memory = market.getMemoryWithoutUpdate();

        //if this ind has not set its target already
        if (memory.contains(RUINED_INFRA_UPGRADE_ID_KEY)) return;

        market.getMemoryWithoutUpdate().set(RUINED_INFRA_UPGRADE_ID_KEY, id);
    }

    public static void resetRuinfraState(MarketAPI market){
        MemoryAPI memory = market.getMemoryWithoutUpdate();
        memory.unset(RUINED_INFRA_UPGRADE_ID_KEY);
        setRuinfraPlacedMemoryKey(market, false);
    }

    public static boolean marketPrimedForCondition(MarketAPI market){
        MemoryAPI memory = market.getMemoryWithoutUpdate();
        String ruinsConditionSet = "$IndEvo_infraPlaced_" + market.getId();
        return memory.contains(RUINED_INFRA_UPGRADE_ID_KEY) && !memory.getBoolean(ruinsConditionSet);
    }

    private void addRuinfraIfNeeded() {
        if (market == null) return;

        log.info("Adding ruinfra to " + market.getName());
        market.addIndustry(Ids.RUINFRA);
        setRuinfraPlacedMemoryKey(market, true);
        Global.getSector().getEconomy().tripleStep();
    }

    private boolean isRuinfraConditionSet() {
        MemoryAPI memory = market.getMemoryWithoutUpdate();
        String ruinsConditionSet = "$IndEvo_infraPlaced_" + market.getId();

        return memory.getBoolean(ruinsConditionSet);
    }

    private static void setRuinfraPlacedMemoryKey(MarketAPI market, boolean set) {
        MemoryAPI memory = market.getMemoryWithoutUpdate();
        String ruinsConditionSet = "$IndEvo_infraPlaced_" + market.getId();
        memory.set(ruinsConditionSet, set);
    }

    private void removeConditionIfRuinsNotPresent() {
        if (!market.getMemoryWithoutUpdate().getBoolean("$isPlanetConditionMarketOnly")
                && isRuinfraConditionSet()
                && !market.hasIndustry(Ids.RUINFRA)
                && !market.getFactionId().equals("neutral")
                && !market.isPlanetConditionMarketOnly()) {

            log.info("Removing InfraCondition on " + market.getName());
            market.removeSpecificCondition(condition.getIdForPluginModifications());
        }
    }

    @Override
    public boolean showIcon() {
        return false;
    }
}