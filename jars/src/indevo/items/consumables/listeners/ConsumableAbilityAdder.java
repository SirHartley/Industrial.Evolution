package indevo.items.consumables.listeners;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.util.IntervalUtil;
import indevo.items.consumables.ItemAbilityHelper;

import java.util.Map;

// polls because loot, markets, rules and console all add cargo with no shared callback
public class ConsumableAbilityAdder implements EveryFrameScript {

    private final IntervalUtil interval = new IntervalUtil(0.5f, 0.5f);

    public static void register() {
        if (!Global.getSector().hasTransientScript(ConsumableAbilityAdder.class))
            Global.getSector().addTransientScript(new ConsumableAbilityAdder());
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return true;
    }

    @Override
    public void advance(float amount) {
        interval.advance(amount);
        if (!interval.intervalElapsed()) return;

        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null) return;

        CargoAPI cargo = fleet.getCargo();
        for (Map.Entry<String, String> e : ItemAbilityHelper.ID_MAP.entrySet()) {
            if (fleet.hasAbility(e.getValue())) continue;
            if (cargo.getQuantity(CargoAPI.CargoItemType.SPECIAL, new SpecialItemData(e.getKey(), null)) > 0)
                ItemAbilityHelper.addAbility(fleet, e.getValue());
        }
    }
}
