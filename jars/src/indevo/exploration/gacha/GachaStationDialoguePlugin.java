package indevo.exploration.gacha;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.MusicPlayerPluginImpl;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.HullMods;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.ValueDisplayMode;
import com.fs.starfarer.api.util.ListMap;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import com.fs.starfarer.campaign.rules.Memory;
import indevo.ids.ItemIds;
import indevo.utils.helper.MiscIE;
import indevo.utils.helper.Settings;
import org.lwjgl.input.Keyboard;

import java.util.*;

import static indevo.utils.helper.MiscIE.getCurrentInteractionTargetMarket;
import static indevo.utils.helper.MiscIE.stripShipToCargoAndReturnVariant;

public class GachaStationDialoguePlugin implements InteractionDialogPlugin {

    public static final String HAS_RESTORED_STATION = "$IndEvo_stationRepaired";
    public static final int RARE_PART_COST_AMT = 100;

    public static final int METALS_REPAIR_COST = 10000;
    public static final int MACHINERY_REPAIR_COST = 1000;
    public static final int PARTS_REPAIR_COST = 4000;

    public static final int PARTS_PER_DP = 100;

    public static final float AUTOMATED_CHANCE = 0.1f;

    public static final String RANDOM = "$IndEvo_randomSeed";

    private enum Option {
        MAIN,
        INITIAL_PAY_TO_RESTORE,
        PARTS_SELECTOR,
        SELECT_SHIPS,
        CONFIRM,
        LEAVE,
        CARGO
    }

    protected InteractionDialogAPI dialog;
    protected TextPanelAPI text;
    protected OptionPanelAPI options;

    private List<FleetMemberAPI> selectedShips = new ArrayList<>();
    private float partsToSacrifice = 0f;

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        this.options = dialog.getOptionPanel();
        this.text = dialog.getTextPanel();

        displayDefaultOptions(true);
    }

    public boolean isRepaired() {
        return dialog.getInteractionTarget().getMemoryWithoutUpdate().getBoolean(HAS_RESTORED_STATION);
    }

    public void setRepaired() {
        dialog.getInteractionTarget().getMemoryWithoutUpdate().set(HAS_RESTORED_STATION, true);
    }

    public void displayDefaultOptions(boolean clearText) {
        if (clearText) text.clear();
        addDefaultTooltip();
        if (clearText)
            dialog.getVisualPanel().showImageVisual(dialog.getInteractionTarget().getCustomInteractionDialogImageVisual());

        OptionPanelAPI opts = options;
        opts.clearOptions();

        boolean isEnabled;
        Option option;

        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        CargoAPI cargo = playerFleet.getCargo();

        SubmarketPlugin storageSubmarket = Misc.getStorage(dialog.getInteractionTarget().getMarket());
        if (storageSubmarket == null){
            Misc.setAbandonedStationMarket(dialog.getInteractionTarget().getId(), dialog.getInteractionTarget());
            storageSubmarket = Misc.getStorage(dialog.getInteractionTarget().getMarket());
        }

        CargoAPI storage = storageSubmarket.getCargo();

        if (isRepaired()) {
            addPostRestoreTooltip();

            option = Option.CARGO;
            opts.addOption("Visit the cargo areas", option);

            option = Option.SELECT_SHIPS;
            storage.initMothballedShips(Factions.PLAYER);

            isEnabled = playerFleet.getFleetData().getNumMembers() + storage.getMothballedShips().getNumMembers() > 1;

            opts.addOption("Select ships to sacrifice", option);
            opts.setEnabled(option, isEnabled);
            if (isEnabled)
                opts.setTooltip(option, "Select ships to offer to the machine, for it may bestow a blessing of silicone and steel made anew");
            else opts.setTooltip(option, "You can not sacrifice the only ship in your fleet, tempting as it may be");

            float partsAvailable = cargo.getCommodityQuantity(ItemIds.PARTS) + storage.getCommodityQuantity(ItemIds.PARTS);
            opts.addSelector("Sacrifice Starship Components", Option.PARTS_SELECTOR, Misc.getHighlightColor(),
                    300f,
                    50f,
                    0f,
                    partsAvailable,
                    ValueDisplayMode.VALUE,
                    partsAvailable >= 1f ?
                            "Offer ship components - know that the residing mind might consider them inferior to the sanctified metal of a true ship."
                            : "You do not have any ship components to sacrifice");

            opts.setSelectorValue(Option.PARTS_SELECTOR, partsToSacrifice);

            option = Option.CONFIRM;
            float rarePartsAvailable = cargo.getCommodityQuantity(ItemIds.RARE_PARTS) + storage.getCommodityQuantity(ItemIds.RARE_PARTS);
            isEnabled = (!selectedShips.isEmpty() || partsToSacrifice > 0) && rarePartsAvailable >= RARE_PART_COST_AMT;

            opts.addOption("Start the machine", option);
            opts.setEnabled(option, isEnabled);
            if (isEnabled)
                opts.setTooltip(option, "Pray to the mechanic deity to grant upon you a rare print from the deepest parts of the ancient mnemonic relays");
            else {
                opts.setTooltip(option, "You do not have a sacrifice selected or do not have sufficient relic components to offer upon the altar. (Cost: " + RARE_PART_COST_AMT + " Relic Components)");
                opts.setTooltipHighlightColors(option, Misc.getHighlightColor());
                opts.setTooltipHighlights(option, "(Cost: " + RARE_PART_COST_AMT + " Relic Components)");
            }
        } else {
            addPreRestoreTooltip();

            option = Option.INITIAL_PAY_TO_RESTORE;

            isEnabled = cargo.getCommodityQuantity(Commodities.METALS) >= METALS_REPAIR_COST
                    && cargo.getCommodityQuantity(Commodities.HEAVY_MACHINERY) >= MACHINERY_REPAIR_COST
                    && cargo.getCommodityQuantity(ItemIds.PARTS) >= PARTS_REPAIR_COST;

            if (Global.getSettings().isDevMode()) isEnabled = true;

            opts.addOption("Restore the machine to function", option);
            opts.setEnabled(option, isEnabled);
            if (isEnabled)
                opts.setTooltip(option, "Deliver the offerings to the monolithic drop-off zones and perform the initialization rites");
            else
                opts.setTooltip(option, "Your cargo contains insufficient offerings to restore this temple to its former glory");
        }

        option = Option.LEAVE;
        opts.addOption("Leave", option);
        opts.setShortcut(option, Keyboard.KEY_ESCAPE, false, false, false, true);
    }


    @Override
    public void optionSelected(String optionText, Object optionData) {
        options.clearOptions();
        Option opt = (Option) optionData;

        switch (opt) {
            case MAIN:
                displayDefaultOptions(true);
                break;
            case INITIAL_PAY_TO_RESTORE:
                text.addPara("As your crew chants the last verses of the inscribed prayers, massive, multi-segmented arms unfold from the altar-like walls and slowly begin transferring the offerings into the multitude of openings formed by their emergence.");
                text.addPara("Billowing red flames erupt from long dead braziers. A cacophony of clicking and grinding accompanies them, echoing through the cathedral-like space in a grotesque mockery of a choir.");
                text.addPara("Your crew watches on as the long dead machine revives itself, heaving in effort as it shakes off the weight of centuries with rasped breath.");

                CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
                if (!Global.getSettings().isDevMode()){
                    cargo.removeCommodity(Commodities.METALS, METALS_REPAIR_COST);
                    cargo.removeCommodity(Commodities.HEAVY_MACHINERY, MACHINERY_REPAIR_COST);
                    cargo.removeCommodity(ItemIds.PARTS, PARTS_REPAIR_COST);
                }

                AddRemoveCommodity.addCommodityLossText(Commodities.METALS, METALS_REPAIR_COST, text);
                AddRemoveCommodity.addCommodityLossText(Commodities.HEAVY_MACHINERY, MACHINERY_REPAIR_COST, text);
                AddRemoveCommodity.addCommodityLossText(ItemIds.PARTS, PARTS_REPAIR_COST, text);

                setRepaired();

                Misc.setAbandonedStationMarket(dialog.getInteractionTarget().getId(), dialog.getInteractionTarget());
                dialog.getInteractionTarget().getMarket().getMemoryWithoutUpdate().set(MusicPlayerPluginImpl.MUSIC_SET_MEM_KEY, "IndEvo_Haplogynae_derelict_theme");
                options.addOption("Continue", Option.MAIN);
                break;
            case SELECT_SHIPS:
                initFleetMemberPicker();
                break;
            case CONFIRM:
                cycleMember();
                break;
            case LEAVE:
                dialog.dismiss();
                break;
            case CARGO:
                HashMap<String, MemoryAPI> map = new HashMap<>();
                map.put(MemKeys.LOCAL, new Memory());
                FireBest.fire(null, dialog,map, "IndEvo_GachaStationCargoOpen");
                break;
        }
    }

    @Override
    public void optionMousedOver(String optionText, Object optionData) {

    }

    @Override
    public void advance(float amount) {
        OptionPanelAPI panel = dialog.getOptionPanel();

        if (!panel.hasOption(Option.CONFIRM)) return;

        partsToSacrifice = panel.getSelectorValue(Option.PARTS_SELECTOR);
        boolean isEnabled = (!selectedShips.isEmpty() || partsToSacrifice > 0) && Global.getSector().getPlayerFleet().getCargo().getCommodityQuantity(ItemIds.RARE_PARTS) >= RARE_PART_COST_AMT;
        panel.setEnabled(Option.CONFIRM, isEnabled);
    }

    @Override
    public void backFromEngagement(EngagementResultAPI battleResult) {

    }

    @Override
    public Object getContext() {
        return null;
    }

    @Override
    public Map<String, MemoryAPI> getMemoryMap() {
        return null;
    }

    public void addDefaultTooltip() {
        text.addPara("\"In its eternal glory, it shall reward sacrifice with blessings.\"\n");

        text.addPara("Long banners of decaying synth-weave illuminated by chandeliers and wall sconces line the massive landing bays, tattered, yet inspiring awe nonetheless.");
        text.addPara("Through the omnipresent religious iconography, it becomes clear that the soul contained in the reliquary core is very old - and very not human.");
    }

    public void addPreRestoreTooltip() {
        text.addPara("The central chamber is lined by engraved golden walls detailing the journey of an ancient being, " +
                "constructing a shell befitting its grand image as it extinguishes star after star to fuel itself, " +
                "forever running from a searching golden eye.");

        text.addPara("Your shipboard historian points out some smaller icons depicting robed humans presenting it with strangely specific offerings.");

        Misc.showCost(text, null, null,
                new String[]{Commodities.METALS, Commodities.HEAVY_MACHINERY, ItemIds.PARTS},
                new int[]{METALS_REPAIR_COST, MACHINERY_REPAIR_COST, PARTS_REPAIR_COST});
    }

    public void addPostRestoreTooltip() {
        text.addPara("An archaic computer interface appears on a central dias as you approach, flickering in a sooty crimson blaze.");
        text.addPara("Thousands of mechanical faces, revealed by the light, watch from above as you are once again presented with the primary tenet of the immortal engine.");
        text.addPara("\"Give, and thou shalt be given unto.\"\n");

        Misc.showCost(text, null, null,
                new String[]{ItemIds.RARE_PARTS},
                new int[]{RARE_PART_COST_AMT});

        if (selectedShips.isEmpty()) return;
        text.addPara("Ships selected for sacrifice:");
        TooltipMakerAPI tt = text.beginTooltip();
        tt.addShipList(selectedShips.size(), 1, Math.min((int) Math.ceil((dialog.getTextWidth() * 0.8f) / selectedShips.size()), 40f), Misc.getBasePlayerColor(), selectedShips, 10f);
        text.addTooltip();
    }

    public void cycleMember() {
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        CargoAPI cargo = playerFleet.getCargo();
        CargoAPI storage = Misc.getStorage(dialog.getInteractionTarget().getMarket()).getCargo();

        float totalDP = 0;
        for (FleetMemberAPI member : selectedShips) {
            totalDP += member.getDeploymentPointsCost();
            stripShipToCargoAndReturnVariant(member, cargo);
            member.updateStats();
            if (playerFleet.getFleetData().getMembersListCopy().contains(member)) playerFleet.getFleetData().removeFleetMember(member);
            else {
                storage.initMothballedShips(Factions.PLAYER);
                storage.getMothballedShips().removeFleetMember(member);
            }
        }

        totalDP += partsToSacrifice > 0 ? Math.round(partsToSacrifice / PARTS_PER_DP) : 0;

        Global.getLogger(GachaStationDialoguePlugin.class).info("Combined input DP " + totalDP);

        String bestID = null;
        float currentHighest = 0;

        for (Map.Entry<String, Float> e : createHullIdSelection().entrySet()) {
            if (totalDP > e.getValue() && e.getValue() > currentHighest) {
                bestID = e.getKey();
                currentHighest = e.getValue();
            }
        }

        String itemID = ItemIds.PARTS;
        float partsInStorage = storage.getCommodityQuantity(itemID);
        float partsToRemove = partsToSacrifice;

        if (partsInStorage > partsToRemove) storage.removeCommodity(itemID, partsToRemove);
        else {
            storage.removeCommodity(itemID, partsInStorage);
            cargo.removeCommodity(itemID, partsToRemove - partsInStorage);
        }

        itemID = ItemIds.RARE_PARTS;
        partsInStorage = storage.getCommodityQuantity(itemID);
        partsToRemove = RARE_PART_COST_AMT;

        if (partsInStorage > partsToRemove) storage.removeCommodity(itemID, partsToRemove);
        else {
            storage.removeCommodity(itemID, partsInStorage);
            cargo.removeCommodity(itemID, partsToRemove - partsInStorage);
        }

        partsToSacrifice = 0;
        selectedShips.clear();

        Global.getLogger(GachaStationDialoguePlugin.class).info("Player receives: " + bestID);

        if (bestID == null || bestID.isEmpty()) {
            text.addPara("With the last hull placed in the sacrificial coves and the last offerings prepared on the altars, your crew prepares to perform the ancient rites of activation. " +
                    "As they chant, the waking faces join in with screeching voice, rhythmic thumping swelling up from the heart of the holy assembly, increasing in volume and intensity as the prayer carries through the empty halls.");
            text.addPara("Hangar after hangar abruptly closes, anything inside swallowed into the depths, to be reformed through the power of a star and feeding whatever resides within this place.");
            text.addPara("And as the cacophony reaches its apex, the screeching stops, the faces slacken - and nothing remains.");
            text.addPara("\n\nYour offerings have been deemed insignificant.");
            text.setHighlightColorsInLastPara(Misc.getNegativeHighlightColor());
            text.highlightInLastPara("Your offerings have been deemed insufficient.");

            if (partsToSacrifice > 0)
                AddRemoveCommodity.addCommodityLossText(ItemIds.PARTS, Math.round(partsToSacrifice), text);
            AddRemoveCommodity.addCommodityLossText(ItemIds.RARE_PARTS, RARE_PART_COST_AMT, text);
        }

        if (bestID != null) {
            text.addPara("With the last hull placed in the sacrificial coves and the last offerings prepared on the altars, your crew prepares to perform the ancient rites of activation. " +
                    "As they chant, the waking faces join in with screeching voice, rhythmic thumping swelling up from the heart of the holy assembly, increasing in volume and intensity as the prayer carries through the empty halls.");
            text.addPara("Hangar after hangar abruptly closes, anything inside swallowed into the depths, to be reformed through the power of a star and feeding whatever resides deep inside this place.");
            text.addPara("And as the cacophony reaches its apex, the screeching stops, the faces slacken - the interface flickers green, and a hangar number is displayed in large, gothic letters.");
            text.addPara("The machine mind has deemed your offerings sufficient.");

            FleetMemberAPI ship = createAndPrepareMember(bestID, 3);

            dialog.getVisualPanel().showFleetMemberInfo(ship, true);
            playerFleet.getFleetData().addFleetMember(ship);
            if (partsToSacrifice > 0)
                AddRemoveCommodity.addCommodityLossText(ItemIds.PARTS, Math.round(partsToSacrifice), text);
            AddRemoveCommodity.addCommodityLossText(ItemIds.RARE_PARTS, RARE_PART_COST_AMT, text);
            AddRemoveCommodity.addFleetMemberGainText(ship, text);
        }

        options.addOption("Continue", Option.MAIN);
    }

    public void initFleetMemberPicker() {
        List<FleetMemberAPI> fleetMemberListAll = Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy();
        List<FleetMemberAPI> fleetMemberList = new ArrayList<>();

        //remove quest relevant ships from selectables
        for (FleetMemberAPI m : fleetMemberListAll) {
            if (m.getHullSpec().getTags().contains(Tags.SHIP_CAN_NOT_SCUTTLE) || m.getVariant().getTags().contains(Tags.SHIP_CAN_NOT_SCUTTLE))
                continue;
            fleetMemberList.add(m);
        }

        CargoAPI storage = Misc.getStorage(dialog.getInteractionTarget().getMarket()).getCargo();
        storage.initMothballedShips(Factions.PLAYER);

        for (FleetMemberAPI m : storage.getMothballedShips().getMembersListCopy()) {
            if (m.getHullSpec().getTags().contains(Tags.SHIP_CAN_NOT_SCUTTLE) || m.getVariant().getTags().contains(Tags.SHIP_CAN_NOT_SCUTTLE))
                continue;
            fleetMemberList.add(m);
        }

        int shipsPerRow = Settings.getInt(Settings.SHIP_PICKER_ROW_COUNT);
        int rows = fleetMemberList.size() > shipsPerRow ? (int) Math.ceil(fleetMemberList.size() / (float) shipsPerRow) : 1;
        int cols = Math.min(fleetMemberList.size(), shipsPerRow);

        dialog.showFleetMemberPickerDialog("Select ships to sacrifice", "Confirm", "Cancel", rows,
                cols, 80f, true, true, fleetMemberList, new FleetMemberPickerListener() {

                    @Override
                    public void pickedFleetMembers(List<FleetMemberAPI> members) {
                        if (members != null && !members.isEmpty()) {
                            selectedShips.clear();
                            selectedShips.addAll(members);
                        }

                        displayDefaultOptions(true);
                    }

                    @Override
                    public void cancelledFleetMemberPicking() {
                        displayDefaultOptions(true);
                    }
                });
    }

    public FleetMemberAPI createAndPrepareMember(String hullID, int maxDmodAmt) {
        List<String> l = Global.getSettings().getHullIdToVariantListMap().get(hullID);
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
        picker.addAll(l);

        ShipVariantAPI variant = Global.getSettings().getVariant(picker.pick());
        if (variant == null)
            variant = Global.getSettings().createEmptyVariant(Misc.genUID(), Global.getSettings().getHullSpec(hullID));

        FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant);

        variant = variant.clone();
        variant.setOriginalVariant(null);

        Random random = (Random) dialog.getInteractionTarget().getMemoryWithoutUpdate().get(RANDOM);

        boolean isAutomated = random.nextFloat() >= 1f - AUTOMATED_CHANCE;
        if (Global.getSettings().isDevMode()) isAutomated = true;

        int dModsAlready = DModManager.getNumDMods(variant);
        int dmods = maxDmodAmt > 0 ? Math.max(0, random.nextInt(maxDmodAmt) - dModsAlready) : 0;

        if (dmods > 0) {
            DModManager.setDHull(variant);
        }
        member.setVariant(variant, false, true);

        if (dmods > 0) {
            DModManager.addDMods(member, false, dmods, random);
        }

        if (isAutomated) {
            // TODO: 21/05/2023 automation, check if this needs an officer set!
            variant.addTag(Tags.TAG_AUTOMATED_NO_PENALTY);
            variant.addPermaMod(HullMods.AUTOMATED);
            variant.setVariantDisplayName("Automated");
        }

        member.setVariant(variant, true, true);
        member.updateStats();

        float retain = 1f / maxDmodAmt;
        FleetEncounterContext.prepareShipForRecovery(member, true, true, false, retain, retain, random);
        member.getVariant().autoGenerateWeaponGroups();

        member.updateStats();
        return member;
    }

    private Map<String, Float> createHullIdSelection() {
        Map<String, Float> hullMap = new HashMap<>();
        WeightedRandomPicker<ShipHullSpecAPI> picker = new WeightedRandomPicker<>((Random) dialog.getInteractionTarget().getMemoryWithoutUpdate().get(RANDOM));
        ListMap<ShipHullSpecAPI> specAPIListMap = getHullSizeHullSpecListMap();

        for (ShipAPI.HullSize size : new ShipAPI.HullSize[]{
                ShipAPI.HullSize.FRIGATE,
                ShipAPI.HullSize.DESTROYER,
                ShipAPI.HullSize.CRUISER,
                ShipAPI.HullSize.CAPITAL_SHIP}) {

            picker.addAll(specAPIListMap.get(size.toString()));

            if (picker.isEmpty()) continue;

            ShipHullSpecAPI spec = picker.pick();
            ShipVariantAPI var = Global.getSettings().createEmptyVariant(Misc.genUID(), spec);
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

            for (ShipHullSpecAPI spec :  Global.getSettings().getAllShipHullSpecs()) {
                if (Collections.disjoint(spec.getHints(), Arrays.asList("HIDE_IN_CODEX", "STATION"))
                        && Collections.disjoint(spec.getTags(), Arrays.asList("restricted", "no_sell", "no_dealer", "dweller", "threat"))) if (spec.getHullSize().equals(size)) lm.getList(size.toString()).add(spec);
            }

            for (ShipHullSpecAPI spec : MiscIE.getAllLearnableShipHulls()) {
                if (spec.getHullSize().equals(size)) lm.getList(size.toString()).add(spec);
            }
        }

        return lm;
    }
}
