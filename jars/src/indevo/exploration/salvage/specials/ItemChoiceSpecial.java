package indevo.exploration.salvage.specials;


import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Strings;
import com.fs.starfarer.api.impl.campaign.procgen.DropGroupRow;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageSpecialInteraction;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.BaseSalvageSpecial;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Highlights;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.exploration.salvage.scripts.ExplosionScript;
import indevo.ids.ItemIds;
import indevo.items.ForgeTemplateItemPlugin;

//DropGroupRow.getPicker("tech_high").pick().getSpecialItemSpec().getId();
public class ItemChoiceSpecial extends BaseSalvageSpecial {

    public static final String ITEM_1 = "1";
    public static final String NOT_NOW = "not_now";
    public static final String EXIT = "exit";

    public static class ItemChoiceSpecialData implements SalvageSpecialInteraction.SalvageSpecialData {
        public String itemID;
        public final float explodeChance;

        public ItemChoiceSpecialData(String itemID, float explodeChance) {
            this.itemID = itemID;
            this.explodeChance = explodeChance;
        }

        public SalvageSpecialInteraction.SalvageSpecialPlugin createSpecialPlugin() {
            return new ItemChoiceSpecial();
        }
    }

    private ItemChoiceSpecial.ItemChoiceSpecialData data;

    public ItemChoiceSpecial() {
    }

    private SpecialItemData specialItemData;

    @Override
    public void init(InteractionDialogAPI dialog, Object specialData) {
        super.init(dialog, specialData);
        if (entity instanceof PlanetAPI) initNothing();

        data = (ItemChoiceSpecial.ItemChoiceSpecialData) specialData;
        fixSpecialData();

        Global.getLogger(ItemChoiceSpecial.class).info("Resolving " + data.itemID + " to " + specialItemData.getData());

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
        h.setText(s, spec1.getName());
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

    private void fixSpecialData() {
        if (data.itemID == null || data.itemID.isEmpty() || data.itemID.equals(" ")) {
            //for empty items
            WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(random);

            for (DropGroupRow r : DropGroupRow.getPicker("rare_tech").getItems()) {
                if (r.isNothing()) continue;
                if (r.isSpecialItem() && r.getSpecialItemId() != null && !r.getSpecialItemId().isEmpty())
                    picker.add(r.getSpecialItemId());
            }

            for (DropGroupRow r : DropGroupRow.getPicker("indEvo_tech_event").getItems()) {
                if (r.isNothing()) continue;
                if (r.isSpecialItem() && r.getSpecialItemId() != null && !r.getSpecialItemId().isEmpty())
                    picker.add(r.getSpecialItemId());
            }

            //used to crash because the chosen item was "", no idea what happened, but we'll pick as long as we have to god damn it (or 10 times)
            String item = picker.pick();

            for (int i = 0; i < 10; i++){
                if (item != null) break;
                item = picker.pick();
            }

            data.itemID = item;
        }

        //ultimate failsafe, just give the player a fish
        SpecialItemSpecAPI spec = null;
        try {
           spec = Global.getSettings().getSpecialItemSpec(data.itemID);
        } catch (Exception e){
            data.itemID = "IndEvo_debug";
        }

        if (spec == null) data.itemID = "IndEvo_debug";

        //for forge templates
        if (data.itemID.contains(ItemIds.FORGETEMPLATE)) {
            specialItemData = new SpecialItemData(data.itemID, ForgeTemplateItemPlugin.pickShip(null, random));
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

                entity.getContainingLocation().addScript(new ExplosionScript(entity));

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
            if (exploded) setShouldAbortSalvageAndRemoveEntity(true);
            setEndWithContinue(false);
            setDone(true);
            setShowAgain(false);
        }
    }
}