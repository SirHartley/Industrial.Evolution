package indevo.exploration.salvage.specials;

//combine X ships into a new one
//pre-seed the options, calculate hull size according to combined DP (if it exceeds the DP of the next highest ship, you get it)

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageSpecialInteraction;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.BaseSalvageSpecial;
import com.fs.starfarer.api.util.ListMap;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.utils.helper.Misc;
import indevo.utils.helper.Settings;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static indevo.utils.helper.Misc.stripShipToCargoAndReturnVariant;

public class ShipRouletteSpecial extends BaseSalvageSpecial {

    public static final String SELECT = "select";
    public static final String NOT_NOW = "not_now";

    public static class ShipRouletteSpecialData implements SalvageSpecialInteraction.SalvageSpecialData {
        public final int qualityLevel;
        public final Map<String, Float> hullIdDpMap;

        public ShipRouletteSpecialData(int qualityLevel) {
            this.qualityLevel = qualityLevel;
            this.hullIdDpMap = createHullIdSelection();
        }

        private Map<String, Float> createHullIdSelection() {
            Map<String, Float> hullMap = new HashMap<>();
            WeightedRandomPicker<ShipHullSpecAPI> picker = new WeightedRandomPicker<>();
            ListMap<ShipHullSpecAPI> specAPIListMap = getHullSizeHullSpecListMap();

            for (ShipAPI.HullSize size : new ShipAPI.HullSize[]{
                    ShipAPI.HullSize.FRIGATE,
                    ShipAPI.HullSize.DESTROYER,
                    ShipAPI.HullSize.CRUISER,
                    ShipAPI.HullSize.CAPITAL_SHIP}) {

                picker.addAll(specAPIListMap.get(size.toString()));

                if (picker.isEmpty()) continue;

                ShipHullSpecAPI spec = picker.pick();
                ShipVariantAPI var = Global.getSettings().createEmptyVariant(com.fs.starfarer.api.util.Misc.genUID(), spec);
                hullMap.put(spec.getHullId(), Global.getFactory().createFleetMember(FleetMemberType.SHIP, var).getDeploymentPointsCost());
                picker.clear();
            }

            return hullMap;
        }

        private ListMap<ShipHullSpecAPI> getHullSizeHullSpecListMap() {
            ListMap<ShipHullSpecAPI> lm = new ListMap<>();
            for (ShipAPI.HullSize size : new ShipAPI.HullSize[]{
                    ShipAPI.HullSize.FRIGATE,
                    ShipAPI.HullSize.DESTROYER,
                    ShipAPI.HullSize.CRUISER,
                    ShipAPI.HullSize.CAPITAL_SHIP}) {
                for (ShipHullSpecAPI spec : Misc.getAllLearnableShipHulls()) {
                    if (spec.getHullSize().equals(size)) lm.getList(size.toString()).add(spec);
                }
            }

            return lm;
        }

        public SalvageSpecialInteraction.SalvageSpecialPlugin createSpecialPlugin() {
            return new ShipRouletteSpecial();
        }
    }

    private ShipRouletteSpecial.ShipRouletteSpecialData data;

    public ShipRouletteSpecial() {
    }

    @Override
    public void init(InteractionDialogAPI dialog, Object specialData) {
        super.init(dialog, specialData);
        data = (ShipRouletteSpecial.ShipRouletteSpecialData) specialData;
        if (data.hullIdDpMap.isEmpty()) initNothing();

        /*
        Global.getLogger(ShipRouletteSpecial.class).info("List RouletteSpecialShips:");
        for (Map.Entry<String, Float> e : data.hullIdDpMap.entrySet()){
            Global.getLogger(ShipRouletteSpecial.class).info(e.getKey() + " at " + e.getValue());
        }
*/

        String shape = "surprisingly good";
        if (data.qualityLevel > 1) shape = "passable";
        if (data.qualityLevel > 2) shape = "rather bad";

        addText("While making a preliminary assessment, your salvage crews " +
                "find a strange, seemingly automated starship factory lying cold and empty. It appears to be in " + shape + " shape, and is completely out of usable material.\n\n" +
                "There is no indication as to what it might be able to build with enough hull parts.");

        displayBaseOptions();
    }

    public void displayBaseOptions() {
        options.clearOptions();
        options.addOption("Sacrifice ships to the factory", SELECT);
        options.addOption("Not now", NOT_NOW);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (SELECT.equals(optionData)) initHullPicker();
        else if (NOT_NOW.equals(optionData)) {
            addText("You leave. " +
                    "The silent machinery remains behind, to be used or disassembled for parts.");

            setEndWithContinue(false);
            setShowAgain(true);
            setDone(true);
        }
    }

    private void initHullPicker() {
        List<FleetMemberAPI> fleetMemberList = Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy();

        int shipsPerRow = Settings.getInt(Settings.SHIP_PICKER_ROW_COUNT);
        int rows = fleetMemberList.size() > shipsPerRow ? (int) Math.ceil(fleetMemberList.size() / (float) shipsPerRow) : 1;
        int cols = Math.min(fleetMemberList.size(), shipsPerRow);

        cols = Math.max(cols, 4);

        dialog.showFleetMemberPickerDialog("Select Ships", "Confirm", "Cancel", rows,
                cols, 88f, true, true, fleetMemberList, new FleetMemberPickerListener() {

                    @Override
                    public void pickedFleetMembers(List<FleetMemberAPI> members) {
                        if (members != null && !members.isEmpty()) {
                            CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
                            CargoAPI cargo = playerFleet.getCargo();

                            float totalDP = 0;
                            for (FleetMemberAPI member : members) {
                                totalDP += member.getDeploymentPointsCost();
                                stripShipToCargoAndReturnVariant(member, cargo);
                                member.updateStats();
                                playerFleet.getFleetData().removeFleetMember(member);
                            }

                            Global.getLogger(ShipRouletteSpecial.class).info("Combined input DP " + totalDP);

                            String bestID = null;
                            float currentHighest = 0;

                            for (Map.Entry<String, Float> e : getData().hullIdDpMap.entrySet()) {
                                if (totalDP > e.getValue() && e.getValue() > currentHighest) {
                                    bestID = e.getKey();
                                    currentHighest = e.getValue();
                                }
                            }

                            Global.getLogger(ShipRouletteSpecial.class).info("Player receives: " + bestID);

                            if (bestID == null || bestID.isEmpty()) {
                                addText("And with a horrifying sound, the factory comes to life - as if it had only ever waited to be fed. Mechanical arms rip " +
                                        "your old ships apart, stripping them and extracting whatever the machine mind is looking for, then discarding the remains into space." +
                                        "\nA new ship is slowly being assembled out of the salvaged parts in a central hangar." +
                                        "\n\nBut there is not enough material to finish even the smallest hull on the roster. The assembly grinds to a halt, " +
                                        "lying dead once again. Further attempts to reactivate it all fail.\n\n");

                                finishAndDisplayCont();
                            }

                            if (bestID != null) {
                                addText("And with a horrifying sound, the factory comes to life - as if it had only ever waited to be fed. Mechanical arms rip " +
                                        "your old ships apart, stripping them and extracting whatever the machine mind is looking for, then discarding the remains into space." +
                                        "\nSlowly, a new ship is assembled out of the salvaged parts in a central hangar." +
                                        "\n\nThen, just as the final armor is slotted, and the last coat of paint applied, the assembly grinds to a halt, lying" +
                                        " dead once again. Further attempts to reactivate it all fail.\n\n");

                                FleetMemberAPI ship = createAndPrepareMember(bestID);
                                getVisual().showFleetMemberInfo(ship, true);
                                playerFleet.getFleetData().addFleetMember(ship);
                                AddRemoveCommodity.addFleetMemberGainText(ship, getText());

                                finishAndDisplayCont();
                            }

                        } else {
                            cancelledFleetMemberPicking();
                        }
                    }

                    @Override
                    public void cancelledFleetMemberPicking() {
                        addText("You decide to let the old machinery rest in peace - it may be for the best.");
                        displayBaseOptions();
                    }
                });
    }

    public void finishAndDisplayCont() {
        setDone(true);
        setShowAgain(false);
        setEndWithContinue(false);

        getOptions().clearOptions();
        getOptions().addOption("Continue", null);
    }

    public OptionPanelAPI getOptions() {
        return options;
    }

    public FleetMemberAPI createAndPrepareMember(String hullID) {
        List<String> l = Global.getSettings().getHullIdToVariantListMap().get(hullID);
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
        picker.addAll(l);

        ShipVariantAPI variant = Global.getSettings().getVariant(picker.pick());
        if (variant == null)
            variant = Global.getSettings().createEmptyVariant(com.fs.starfarer.api.util.Misc.genUID(), Global.getSettings().getHullSpec(hullID));

        FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant);

        variant = variant.clone();
        variant.setOriginalVariant(null);

        int dModsAlready = DModManager.getNumDMods(variant);
        int dmods = data.qualityLevel > 0 ? Math.max(0, random.nextInt(data.qualityLevel) - dModsAlready) : 0;

        if (dmods > 0) {
            DModManager.setDHull(variant);
        }
        member.setVariant(variant, false, true);

        if (dmods > 0) {
            DModManager.addDMods(member, false, dmods, random);
        }

        member.setVariant(variant, true, true);
        member.updateStats();

        float retain = 1f / data.qualityLevel;
        FleetEncounterContext.prepareShipForRecovery(member, true, true, false, retain, retain, random);
        member.getVariant().autoGenerateWeaponGroups();

        member.updateStats();
        return member;
    }

    public VisualPanelAPI getVisual() {
        return visual;
    }

    public ShipRouletteSpecialData getData() {
        return data;
    }

    public TextPanelAPI getText() {
        return text;
    }

    private MemoryAPI getMemory() {
        return BaseCommandPlugin.getEntityMemory(dialog.getPlugin().getMemoryMap());
    }
}
