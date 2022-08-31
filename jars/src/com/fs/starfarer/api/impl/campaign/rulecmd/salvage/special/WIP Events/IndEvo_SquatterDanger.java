package com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special;


import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.impl.items.IndEvo_ForgeTemplateItemPlugin;
import com.fs.starfarer.api.impl.campaign.entityplugins.IndEvo_ExplosionScript;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.IndEvo_Items;
import com.fs.starfarer.api.impl.campaign.ids.Strings;
import com.fs.starfarer.api.impl.campaign.procgen.DropGroupRow;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageSpecialInteraction;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Highlights;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

//DropGroupRow.getPicker("tech_high").pick().getSpecialItemSpec().getId();
public class IndEvo_SquatterDanger extends BaseSalvageSpecial {

    //Initial Scans of the $entity show sections signs of habitation and multiple active power sources.
    //Upon breaching, your salvage crews quickly find the inhabitants - remnants of former civilisation occupying part of the $entity, living off the miraculously intact life support systems.
    //While initially peaceful, negotiation break down quickly once your intent to salvage their home becomes apparent.

    //They send the $entity into lockdown and barricade the corridors, but take no hostile action.
    //Video feeds from hijacked cameras show children being led to supposedly safe locations.

    //core the inhabitated parts with a well-placed shot and salvage the remains
    //send in a team of marines to clear the rooms and take any loot they find
    //trade with them (?)
    //Offer them a place on your colonies
    //Leave them in peace


    public static final String ITEM_1 = "1";
    public static final String BOMB_THE_FUCKERS = "bomb_em";
    public static final String RAID_THE_FUCKERS = "raid_em";
    public static final String REQUEST_TRADE = "trade_em";
    public static final String OFFER_TRANSIT = "move_em";
    public static final String NOT_NOW = "not_now";
    public static final String EXIT = "exit";

    public static class SquatterDangerSpecialData implements SalvageSpecialInteraction.SalvageSpecialData {
        public Float partitionDestroyedByBombing;
        public final float explodeChance;

        public SquatterDangerSpecialData(String itemID, float explodeChance) {
            this.itemID = itemID;
            this.explodeChance = explodeChance;
        }

        public SalvageSpecialInteraction.SalvageSpecialPlugin createSpecialPlugin() {
            return new IndEvo_SquatterDanger();
        }
    }

    private IndEvo_SquatterDanger.SquatterDangerSpecialData data;

    public IndEvo_SquatterDanger() {
    }

    private SpecialItemData specialItemData;

    @Override
    public void init(InteractionDialogAPI dialog, Object specialData) {
        super.init(dialog, specialData);
        if (entity instanceof PlanetAPI) initNothing();

        data = (IndEvo_SquatterDanger.SquatterDangerSpecialData) specialData;
        fixSpecialData();

        Global.getLogger(IndEvo_SquatterDanger.class).info("Resolving " + data.itemID + " to " + specialItemData.getData());

        String chance = "notes that it should be rather easy to defuse it.";
        if (data.explodeChance > 0.2) chance = "is relatively confident they should be able to defuse the system.";
        if (data.explodeChance > 0.4) chance = "is not sure if they can properly defuse it.";
        if (data.explodeChance > 0.6) chance = "warns against touching anything - this thing could blow at any minute.";
        if (data.explodeChance > 0.8) chance = "takes one look, turns around and starts running for the shuttles.";

        SpecialItemSpecAPI spec1 = Global.getSettings().getSpecialItemSpec(data.itemID);
        String s = "The entire $shortName is rigged to explode should the room be breached. ";
        text.addPara(getString("Immediately upon breaching the sturdy hangar bays, your salvage crew runs into problems. Functioning auto-turrets, " +
                        "entire bulkheads rigged to collapse, airlocks repurposed as traps and old corpses in the hallways. " +
                        "Fighting through the defences, your crews finally report a heavily secured room containing valuable technology - and a rather concerning issue:\n\n" + s +
                        "Your explosives squad " + chance + "\n\nYou could recover the " + spec1.getName() + ", or just blow the control room to bits - destroying the tech in the process."));

        Highlights h = new Highlights();
        h.setText(s,  spec1.getName());
        h.setColors(Misc.getNegativeHighlightColor(), Misc.getHighlightColor());
        text.setHighlightsInLastPara(h);

        TooltipMakerAPI tooltip = text.beginTooltip();
        float opad = 10f;

        TooltipMakerAPI item1 = tooltip.beginImageWithText(spec1.getIconName(), 48);
        item1.addPara(spec1.getDesc(), opad);
        tooltip.addImageWithText(opad);

        text.addTooltip();

        options.clearOptions();
        options.addOption("Take the " + spec1.getName() + " and risk an explosion", ITEM_1);
        options.addOption("Destroy the control room including the tech", NOT_NOW);
    }

    private void fixSpecialData(){
        if(data.itemID == null || data.itemID.isEmpty() || data.itemID.equals(" ")){
            //for empty items
            WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(random);

            for (DropGroupRow r : DropGroupRow.getPicker("rare_tech").getItems()){
                if(r.isNothing()) continue;
                if(r.isSpecialItem() && r.getSpecialItemId() != null && !r.getSpecialItemId().isEmpty()) picker.add(r.getSpecialItemId());
            }

            //used to crash because the chosen item was "", no idea what happened, but we'll pick as long as we have to god damn it (or 10 times)
            String item = picker.pick();
            int safeguard = 0;

            while (item == null || item.isEmpty() || safeguard > 10){
                item = picker.pick();
                safeguard++;
            }

            data.itemID = item;
        }

        //for forge templates
        if(data.itemID.contains(IndEvo_Items.FORGETEMPLATE)){
            specialItemData = new SpecialItemData(data.itemID, IndEvo_ForgeTemplateItemPlugin.pickShip(null, random));
        } else {
            specialItemData = new SpecialItemData(data.itemID, null);
        }
    }

    private boolean exploded = false;

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (ITEM_1.equals(optionData)) {
            SpecialItemSpecAPI spec1 = Global.getSettings().getSpecialItemSpec(data.itemID);

            if (random.nextFloat() < data.explodeChance) {
                addText("Your crew quickly breaches the secure room and takes the " + spec1.getName() + ".\n" +
                        "Nothing happens. You realize you have been holding your breath, and start exhaling slowly. The crew reports mission complete as the transport leaves the shuttle bay.");

                text.addPara(getString("The transmission gets cut off by the $shortName suddenly exploding."), Misc.getNegativeHighlightColor());

                entity.getContainingLocation().addScript(new IndEvo_ExplosionScript(entity));

                int crewPenalty = Math.min((int) Math.ceil(playerFleet.getCargo().getCrew() * (0.3 * random.nextFloat() * data.explodeChance)), 500);
                playerFleet.getCargo().removeCrew(crewPenalty);
                exploded = true;

                AddRemoveCommodity.addCommodityLossText(Commodities.CREW, crewPenalty, text);
                //add another option to keep player in diag until I can trigger explo
            } else {
                addText("Your crew quickly breaches the secure room and takes the " + spec1.getName() + ".\n" +
                        "Nothing happens. You realize you have been holding your breath, and start exhaling slowly. " +
                        "The crew reports mission complete as the transport leaves the shuttle bay.\n\n" +
                        "Salvage operations can now commence as usual.");
            }

            playerFleet.getCargo().addSpecial(specialItemData, 1);

            text.setFontSmallInsignia();
            String name = spec1.getName();
            text.addParagraph("Gained " + Misc.getWithDGS(1) + Strings.X + " " + name + "", Misc.getPositiveHighlightColor());
            text.highlightInLastPara(Misc.getHighlightColor(), Misc.getWithDGS(1) + Strings.X);
            text.setFontInsignia();

            options.clearOptions();
            options.addOption("Continue", EXIT);

        } else if (NOT_NOW.equals(optionData)) {
            setDone(true);
            setEndWithContinue(false);
            setShowAgain(false);
        } else if (EXIT.equals(optionData)) {
            if(exploded) setShouldAbortSalvageAndRemoveEntity(true);
            setEndWithContinue(false);
            setDone(true);
            setShowAgain(false);
        }
    }
}