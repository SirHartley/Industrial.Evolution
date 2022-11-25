package industrial_evolution.splinterFleet.salvageSpecials;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.FleetMemberPickerListener;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerUtil;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;
import com.fs.starfarer.api.impl.campaign.rulecmd.ShowDefaultVisual;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageSpecialInteraction;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.BaseSalvageSpecial;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OfficerAndShipRecoverySpecial extends BaseSalvageSpecial {

    public static final String OPEN = "open";
    public static final String CONTINUE = "continue";
    public static final String RECOVER = "recover";
    public static final String RECOVERY_FINISHED = "finished";

    public static final String NOT_NOW_OFFICER = "not_now_officer";
    public static final String NOT_NOW_SHIP = "not_now_ship";
    public static final String ABORT_CONTINUE = "leave";

    private FleetMemberAPI member = null;

    public static enum ShipCondition {
        PRISTINE,
        GOOD,
        AVERAGE,
        BATTERED,
        WRECKED,
    }

    public static class OfficerAndShipRecoverySpecialData implements SalvageSpecialInteraction.SalvageSpecialData {
        public PersonAPI officer = null;
        public ShipCondition condition = ShipCondition.AVERAGE;
        public ShipVariantAPI variant = null;
        public String shipName = null;
        public boolean officerRecovered = false;

        public OfficerAndShipRecoverySpecialData(PersonAPI officer, FleetMemberAPI member, ShipCondition condition) {
            this.condition = condition;
            this.variant = member.getVariant();
            this.shipName = member.getShipName();
            this.officer = officer;
        }

        public SalvageSpecialInteraction.SalvageSpecialPlugin createSpecialPlugin() {
            return new OfficerAndShipRecoverySpecial();
        }
    }

    private OfficerAndShipRecoverySpecial.OfficerAndShipRecoverySpecialData data;

    public OfficerAndShipRecoverySpecial() {
    }

    @Override
    public void init(InteractionDialogAPI dialog, Object specialData) {
        super.init(dialog, specialData);

        data = (OfficerAndShipRecoverySpecial.OfficerAndShipRecoverySpecialData) specialData;
        if (data.officer != null && !data.officerRecovered && !data.officer.isAICore()) initOfficer();
        else initShipRecovery();
    }

    protected void initOfficer() {
        addText("While making a preliminary assessment, your salvage crews " +
                "find the command cradle running in emergency cryo-mode.");

        options.clearOptions();
        options.addOption("Attempt to open the cradle", OPEN);
        options.addOption("Not now", NOT_NOW_OFFICER);
    }

    protected void initShipRecovery() {
        visual.showFleetMemberInfo(getMember(), true);

        addInitialText();

        options.clearOptions();
        options.addOption("Consider ship recovery", RECOVER);
        options.addOption("Not now", NOT_NOW_SHIP);
        options.setShortcut(NOT_NOW_SHIP, Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (OPEN.equals(optionData)) {
            addText("The thawing process completes, and the cradle opens. " +
                    "Your slightly groggy officer, " + data.officer.getNameString() + ", greets you and reports for duty - obviously intent on forgetting what led to this situation in the first place.");

            playerFleet.getFleetData().addOfficer(data.officer);
            AddRemoveCommodity.addOfficerGainText(data.officer, text);
            data.officerRecovered = true;
            options.clearOptions();
            options.addOption("Continue", CONTINUE);
        } else if (NOT_NOW_OFFICER.equals(optionData) || CONTINUE.equals(optionData)) {
            initShipRecovery();
        } else if (NOT_NOW_SHIP.equals(optionData)) {
            addExtraSalvageFromUnrecoveredShips();
            setEndWithContinue(false);
            setDone(true);
            setShowAgain(true);
        } else if (RECOVER.equals(optionData)) {
            options.clearOptions();
            options.addOption("Consider ship recovery", RECOVER);

            options.addOption("Not now", NOT_NOW_SHIP);
            options.setShortcut(NOT_NOW_SHIP, Keyboard.KEY_ESCAPE, false, false, false, true);

            List<FleetMemberAPI> pool = Collections.singletonList(member);
            List<FleetMemberAPI> storyPool = new ArrayList<FleetMemberAPI>();

            dialog.showFleetMemberRecoveryDialog("Select ships to recover", pool, storyPool,
                    new FleetMemberPickerListener() {

                        public void pickedFleetMembers(List<FleetMemberAPI> selected) {
                            if (selected.isEmpty()) return;

                            new ShowDefaultVisual().execute(null, dialog, Misc.tokenize(""), memoryMap);

                            float minHull = playerFleet.getStats().getDynamic().getValue(Stats.RECOVERED_HULL_MIN, 0f);
                            float maxHull = playerFleet.getStats().getDynamic().getValue(Stats.RECOVERED_HULL_MAX, 0f);
                            float minCR = playerFleet.getStats().getDynamic().getValue(Stats.RECOVERED_CR_MIN, 0f);
                            float maxCR = playerFleet.getStats().getDynamic().getValue(Stats.RECOVERED_CR_MAX, 0f);

                            float hull = (float) Math.random() * (maxHull - minHull) + minHull;
                            hull = Math.max(hull, member.getStatus().getHullFraction());
                            member.getStatus().setHullFraction(hull);

                            float cr = (float) Math.random() * (maxCR - minCR) + minCR;
                            member.getRepairTracker().setCR(cr);

                            playerFleet.getFleetData().addFleetMember(member);

                            if (dialog.getPlugin() instanceof SalvageSpecialInteraction.SalvageSpecialDialogPlugin) {
                                SalvageSpecialInteraction.SalvageSpecialDialogPlugin plugin = (SalvageSpecialInteraction.SalvageSpecialDialogPlugin) dialog.getPlugin();
                                plugin.optionSelected(null, RECOVERY_FINISHED);

                            } else {
                                // bad state, exit dialog
                                // apparently possible? maybe mods involved
                                // http://fractalsoftworks.com/forum/index.php?topic=12492.0
                                dialog.dismiss();
                            }
                        }

                        public void cancelledFleetMemberPicking() {
                        }
                    });
        } else if (RECOVERY_FINISHED.equals(optionData)) {
            new ShowDefaultVisual().execute(null, dialog, Misc.tokenize(""), memoryMap);

            addExtraSalvageTextAndStuff();

            addText("The " + member.getShipName() + " is now part of your fleet.");

            setShouldAbortSalvageAndRemoveEntity(true);
            options.clearOptions();
            options.addOption("Leave", ABORT_CONTINUE);
            options.setShortcut(ABORT_CONTINUE, Keyboard.KEY_ESCAPE, false, false, false, true);

            List<FleetMemberAPI> recovered = Collections.singletonList(member);
            ListenerUtil.reportShipsRecovered(recovered, dialog);

            for (FleetMemberAPI member : recovered) {
                dialog.getInteractionTarget().getMemoryWithoutUpdate().set("$srs_memberId", member.getId(), 0);
                dialog.getInteractionTarget().getMemoryWithoutUpdate().set("$srs_hullId", member.getHullId(), 0);
                dialog.getInteractionTarget().getMemoryWithoutUpdate().set("$srs_baseHullId", member.getHullSpec().getBaseHullId(), 0);
            }
        } else if (ABORT_CONTINUE.equals(optionData)) {
            setDone(true);
            setShowAgain(false);
            setEndWithContinue(false);
        }
    }

    public void addExtraSalvageTextAndStuff() {
        ExtraSalvage es = BaseSalvageSpecial.getExtraSalvage(entity);
        if (es != null && !es.cargo.isEmpty()) {
            addText("Your crews find some securely stowed cargo during the recovery operation.");

            es.cargo.sort();
            playerFleet.getCargo().addAll(es.cargo);
            for (CargoStackAPI stack : es.cargo.getStacksCopy()) {
                AddRemoveCommodity.addStackGainText(stack, text);
            }
            //addText("The recovery operation is finished without any further surprises.");
        }
    }

    protected void addInitialText() {
        if (!FireBest.fire(null, dialog, memoryMap, "tempShipRecoveryCustomText")) {
            addText("Salvage crews boarding the wreck discover that many essential systems " +
                    "are undamaged and the ship could be restored to basic functionality.");

            ExtraSalvage es = BaseSalvageSpecial.getExtraSalvage(entity);

            if (es != null && !es.cargo.isEmpty()) {
                addText("There are also indications that it has some sort of cargo on board.");
            }
        }

        addText("If not recovered, the ship will be scuttled, " +
                "and any fitted weapons and fighter LPCs will be retrieved.");
    }

    protected FleetMemberAPI getMember() {
        if (member != null) return member;
        if (data.variant == null) return null;

        ShipVariantAPI variant = data.variant;
        variant = variant.clone();
        variant.setOriginalVariant((String) null);
        variant.setHullVariantId(Misc.genUID());

        FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant);
        prepareMember(member, data.condition);

        this.member = member;
        return member;
    }

    public void prepareMember(FleetMemberAPI member, ShipCondition condition) {
        int hits = getHitsForCondition(member, condition);
        int dmods = getDmodsForCondition(condition);

        int reduction = (int) playerFleet.getStats().getDynamic().getValue(Stats.SHIP_DMOD_REDUCTION, 0);
        reduction = random.nextInt(reduction + 1);
        dmods -= reduction;


        member.getStatus().setRandom(random);

        for (int i = 0; i < hits; i++) {
            member.getStatus().applyDamage(1000000f);
        }

        member.getStatus().setHullFraction(getHullForCondition(condition));
        member.getRepairTracker().setCR(0f);


        ShipVariantAPI variant = member.getVariant();
        variant = variant.clone();
        variant.setOriginalVariant(null);

        int dModsAlready = DModManager.getNumDMods(variant);
        dmods = Math.max(0, dmods - dModsAlready);

        if (dmods > 0) {
            DModManager.setDHull(variant);
        }
        member.setVariant(variant, false, true);

        if (dmods > 0) {
            DModManager.addDMods(member, true, dmods, random);
        }

       /* if (shipData.pruneWeapons) {
            float retain = getFighterWeaponRetainProb(shipData.condition);
            FleetEncounterContext.prepareShipForRecovery(member, false, false, false, retain, retain, random);
            member.getVariant().autoGenerateWeaponGroups();
        }*/
    }


    protected float getHullForCondition(ShipCondition condition) {
        switch (condition) {
            case PRISTINE:
                return 1f;
            case GOOD:
                return 0.6f + random.nextFloat() * 0.2f;
            case AVERAGE:
                return 0.4f + random.nextFloat() * 0.2f;
            case BATTERED:
                return 0.2f + random.nextFloat() * 0.2f;
            case WRECKED:
                return random.nextFloat() * 0.1f;
        }
        return 1;
    }


    protected int getDmodsForCondition(ShipCondition condition) {
        if (condition == ShipCondition.PRISTINE) return 0;

        switch (condition) {
            case GOOD:
                return 1;
            case AVERAGE:
                return 1 + random.nextInt(2);
            case BATTERED:
                return 2 + random.nextInt(2);
            case WRECKED:
                return 3 + random.nextInt(2);
        }
        return 1;
    }

    protected int getHitsForCondition(FleetMemberAPI member, ShipCondition condition) {
        if (condition == ShipCondition.PRISTINE) return 0;
        if (condition == ShipCondition.WRECKED) return 20;

        switch (member.getHullSpec().getHullSize()) {
            case CAPITAL_SHIP:
                switch (condition) {
                    case GOOD:
                        return 2 + random.nextInt(2);
                    case AVERAGE:
                        return 4 + random.nextInt(3);
                    case BATTERED:
                        return 7 + random.nextInt(6);
                }
                break;
            case CRUISER:
                switch (condition) {
                    case GOOD:
                        return 1 + random.nextInt(2);
                    case AVERAGE:
                        return 2 + random.nextInt(3);
                    case BATTERED:
                        return 4 + random.nextInt(4);
                }
                break;
            case DESTROYER:
                switch (condition) {
                    case GOOD:
                        return 1 + random.nextInt(2);
                    case AVERAGE:
                        return 2 + random.nextInt(2);
                    case BATTERED:
                        return 3 + random.nextInt(3);
                }
                break;
            case FRIGATE:
                switch (condition) {
                    case GOOD:
                        return 1;
                    case AVERAGE:
                        return 2;
                    case BATTERED:
                        return 3;
                }
                break;
        }
        return 1;
    }

    protected void addExtraSalvageFromUnrecoveredShips() {
        CargoAPI extra = Global.getFactory().createCargo(true);
        addStuffFromMember(extra, member);
        addTempExtraSalvage(extra);
    }

    protected void addStuffFromMember(CargoAPI cargo, FleetMemberAPI member) {
        cargo.addCommodity(Commodities.SUPPLIES, member.getRepairTracker().getSuppliesFromScuttling());
        cargo.addCommodity(Commodities.FUEL, member.getRepairTracker().getFuelFromScuttling());
        cargo.addCommodity(Commodities.HEAVY_MACHINERY, member.getRepairTracker().getHeavyMachineryFromScuttling());

        ShipVariantAPI variant = member.getVariant();
        for (String slotId : variant.getNonBuiltInWeaponSlots()) {
            cargo.addWeapons(variant.getWeaponId(slotId), 1);
        }

        int index = 0;
        for (String wingId : variant.getWings()) {
            if (wingId != null && !wingId.isEmpty() && !variant.getHullSpec().isBuiltInWing(index)) {
                cargo.addFighters(wingId, 1);
            }
            index++;
        }
    }
}
