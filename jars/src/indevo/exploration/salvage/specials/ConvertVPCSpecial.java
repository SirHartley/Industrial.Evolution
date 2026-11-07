package indevo.exploration.salvage.specials;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageSpecialInteraction;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.BaseSalvageSpecial;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.industries.assembler.industry.CommodityForge;
import indevo.industries.assembler.industry.VariableAssembler;
import indevo.items.VPCItemPlugin;
import indevo.items.memory.VPCCommodityRepo.Commodity;
import indevo.utils.helper.StringHelper;
import org.apache.log4j.Logger;

import java.util.*;

public class ConvertVPCSpecial extends BaseSalvageSpecial {
    //convert a VPC into ressources

    public static final Logger log = Global.getLogger(ConvertVPCSpecial.class);

    public static final String SELECT = "select";
    public static final String NOT_NOW = "not_now";
    public static final String VPC_IDENT = "option_item_";

    public static class ConvertVPCSpecialData implements SalvageSpecialInteraction.SalvageSpecialData {

        public final float qualityModifier;

        public ConvertVPCSpecialData(float efficiencyModifier) {
            this.qualityModifier = efficiencyModifier;
        }

        public SalvageSpecialInteraction.SalvageSpecialPlugin createSpecialPlugin() {
            return new ConvertVPCSpecial();
        }
    }

    private ConvertVPCSpecial.ConvertVPCSpecialData data;
    private final List<SpecialItemData> choices = new ArrayList<>();

    public ConvertVPCSpecial() {
    }

    @Override
    public void init(InteractionDialogAPI dialog, Object specialData) {
        super.init(dialog, specialData);
        data = (ConvertVPCSpecial.ConvertVPCSpecialData) specialData;

        options.clearOptions();

        String shape = "surprisingly good";
        if (data.qualityModifier < 0.75f) shape = "passable";
        if (data.qualityModifier < 0.4f) shape = "rather bad";

        String s = entity instanceof PlanetAPI ? "facility" : "$shortName";

        addText("Deep within the bowels of the " + s + " your crew finds an experimental Variable Commodity Forge.\n\n" +
                "It seems to be in " + shape + " shape, and is missing a VPC.");

        if (getVPCsInPlayerCargo().isEmpty()) {
            addText("You do not have a VPC to slot into the Forge.\nConsider returning when you have found one.");
            text.highlightLastInLastPara("You do not have a VPC to slot into the Forge.", Misc.getNegativeHighlightColor());

            setDone(true);
            setEndWithContinue(true);
            setShowAgain(true);
        } else {
            options.addOption("Slot in a VPC", SELECT);
            options.addOption("Not now", NOT_NOW);
        }
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (SELECT.equals(optionData)) {
            options.clearOptions();
            choices.clear();
            choices.addAll(getVPCsInPlayerCargo());

            for (int i = 0; i < choices.size(); i++) {
                SpecialItemData vpc = choices.get(i);
                String ident = VPC_IDENT + i;
                options.addOption(VPCItemPlugin.getDisplayName(vpc), ident);
                options.addOptionConfirmation(ident,
                        "Are you sure? This will convert the VPC into " + VPCItemPlugin.getOutputString(vpc) + ".",
                        "Confirm",
                        "Return");
            }

            options.addOption("Not now", NOT_NOW);

        } else if (optionData.toString().startsWith(VPC_IDENT)) {
            String prct = StringHelper.getAbsPercentString(data.qualityModifier, false);
            SpecialItemData vpc = choices.get(Integer.parseInt(optionData.toString().substring(VPC_IDENT.length())));

            addText("The Forge accepts the chip without incident, and starts churning out " + VPCItemPlugin.getOutputString(vpc) + ".\n" +
                    "After producing " + prct + " of the predicted amount, the entire assembly grinds to a sudden halt.\n\n" +
                    "Judging by the ridiculous amounts of smoke and the concerning noises, it is probably done for good.");

            text.highlightLastInLastPara(prct, Misc.getHighlightColor());

            CargoAPI cargo = playerFleet.getCargo();
            cargo.removeItems(CargoAPI.CargoItemType.SPECIAL, vpc, 1);

            for (Map.Entry<String, Integer> e : getDepositList(vpc).entrySet()) {
                cargo.addCommodity(e.getKey(), e.getValue());
                AddRemoveCommodity.addCommodityGainText(e.getKey(), e.getValue(), text);
            }

            setShowAgain(false);
            setDone(true);

        } else if (NOT_NOW.equals(optionData)) {
            addText("You leave. " +
                    "The Commodity Forge remains behind, to be used or disassembled for parts.");

            setShowAgain(true);
            setDone(true);
        }
    }

    public Map<String, Integer> getDepositList(SpecialItemData vpc) {
        Map<String, Integer> map = new LinkedHashMap<>();
        float mult = VPCItemPlugin.getDepositMult(vpc) * data.qualityModifier;

        for (Commodity c : VPCItemPlugin.getOutputs(vpc)) {
            map.put(c.id(), VariableAssembler.getDepositAmount(c.getDeposit(Ids.COMFORGE), CommodityForge.LOG_INCREASE, 7, mult));
        }

        return map;
    }

    private List<SpecialItemData> getVPCsInPlayerCargo() {
        List<SpecialItemData> list = new ArrayList<>();
        for (CargoStackAPI stack : playerFleet.getCargo().getStacksCopy()) {
            if (VPCItemPlugin.isValid(stack.getSpecialDataIfSpecial())) list.add(stack.getSpecialDataIfSpecial());
        }

        return list;
    }
}
