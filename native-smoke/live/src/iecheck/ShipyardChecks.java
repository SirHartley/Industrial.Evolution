package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc;
import indevo.exploration.gacha.GachaStationDialoguePlugin;
import indevo.ids.Ids;
import indevo.ids.ItemIds;

import java.util.ArrayList;
import java.util.List;

public final class ShipyardChecks {
    private static GachaStationDialoguePlugin plugin = null;
    private static SectorEntityToken station = null;

    private ShipyardChecks() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object option(String name) throws Exception {
        return Enum.valueOf((Class) Class.forName(GachaStationDialoguePlugin.class.getName() + "$Option", false, GachaStationDialoguePlugin.class.getClassLoader()), name);
    }

    private static InteractionDialogAPI dialog() {
        return Global.getSector().getCampaignUI().getCurrentInteractionDialog();
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            StarSystemAPI system = Colony.planets(1).get(0).getStarSystem();
            station = system.addCustomEntity(null, null, Ids.GACHA_STATION, Factions.NEUTRAL);
            station.setCircularOrbit(system.getStar(), 0f, 4000f, 100f);
            station.getMemoryWithoutUpdate().set(GachaStationDialoguePlugin.HAS_RESTORED_STATION, true);
            Colony.jump(system, 4000f, 0f);
            Misc.setAbandonedStationMarket(station.getId(), station);

            CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
            CargoAPI cargo = fleet.getCargo();
            cargo.removeCommodity(ItemIds.RARE_PARTS, cargo.getCommodityQuantity(ItemIds.RARE_PARTS));
            cargo.addCommodity(ItemIds.PARTS, 40f);
            Misc.getStorageCargo(station.getMarket()).addCommodity(ItemIds.RARE_PARTS, 150f);

            plugin = new GachaStationDialoguePlugin();
            c.keepDialog(true);
            Global.getSector().getCampaignUI().showInteractionDialog(plugin, station);
            c.frames(60);
        });
        steps.add(c -> {
            c.expect(dialog() != null, "automated shipyard dialog open");
            dialog().getOptionPanel().setSelectorValue(option("PARTS_SELECTOR"), 40f);
            c.frames(60);
        });
        steps.add(c -> {
            float parts = dialog().getOptionPanel().getSelectorValue(option("PARTS_SELECTOR"));
            c.expect(parts > 0f, "parts picked in the selector (" + parts + ")");
            plugin.advance(0.1f);
            List<?> saved = dialog().getOptionPanel().getSavedOptionList();
            c.note("options " + saved.size());
            plugin.optionSelected(null, option("CONFIRM"));
            c.frames(60);
        });
        steps.add(c -> {
            CargoAPI storage = Misc.getStorageCargo(station.getMarket());
            c.near(storage.getCommodityQuantity(ItemIds.RARE_PARTS), 50f, 0.1f, "2104 the machine took relic components from station storage");
            String text = Ui.text(dialog().getTextPanel());
            c.note("dialog text length " + text.length());
            c.expect(text.contains("Your offerings have been deemed insignificant."), "2103 failure line shows");
            c.expect(text.toLowerCase().contains("lost") && text.contains("40"), "2102 lost ship components line shows");
            c.shot("shipyard");
        });
        steps.add(c -> {
            c.keepDialog(false);
            dialog().dismiss();
        });
        return steps;
    }
}
