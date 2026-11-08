package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import indevo.ids.ItemIds;
import indevo.items.VPCItemPlugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class VpcChecks {
    private static final String ASSEMBLER = "IndEvo_AdAssem";
    private static final String MANUFACTORY = "IndEvo_AdManuf";

    private VpcChecks() {
    }

    private static String resolve(String itemId, String params) throws Exception {
        SpecialItemData seed = VPCItemPlugin.roll(VPCItemPlugin.Tier.getLowest(itemId), new Random(1));
        CargoAPI cargo = Global.getFactory().createCargo(true);
        cargo.addSpecial(seed, 1f);
        CargoStackAPI stack = cargo.getStacksCopy().get(0);
        return ((VPCItemPlugin) stack.getPlugin()).resolveDropParamsToSpecificItemData(params, new Random(2));
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(c -> {
            Random random = new Random(7);
            for (VPCItemPlugin.Tier tier : VPCItemPlugin.Tier.values()) {
                boolean ok = true;
                for (int i = 0; i < 20; i++) {
                    SpecialItemData data = VPCItemPlugin.roll(tier, random);
                    List<String> ids = VPCItemPlugin.getCommodityIds(data);
                    ok &= data.getId().equals(tier.itemId) && ids.size() == tier.outputs
                            && new HashSet<>(ids).size() == ids.size() && VPCItemPlugin.isValid(data);
                }
                c.expect(ok, "V1 " + tier + " rolls " + tier.itemId + " with " + tier.outputs + " distinct outputs");
            }
            boolean npc = true;
            for (int i = 0; i < 100; i++) npc &= VPCItemPlugin.isValid(VPCItemPlugin.rollForNPC(random));
            c.expect(npc, "V1 npc rolls are valid");

            c.expect(VPCItemPlugin.getTier(new SpecialItemData(ItemIds.VPC_RARE, resolve(ItemIds.VPC_RARE, "{tier:rare}"))) == VPCItemPlugin.Tier.RARE, "V1 rare drop params");
            c.expect(VPCItemPlugin.getTier(new SpecialItemData(ItemIds.VPC_ULTRA, resolve(ItemIds.VPC_ULTRA, "{tier:ultra}"))) == VPCItemPlugin.Tier.ULTRA, "V1 ultra drop params");
            c.expect(VPCItemPlugin.getTier(new SpecialItemData(ItemIds.VPC, resolve(ItemIds.VPC, ""))) == VPCItemPlugin.Tier.COMMON, "V1 empty params give the lowest tier");
            c.expect(VPCItemPlugin.getTier(new SpecialItemData(ItemIds.VPC, resolve(ItemIds.VPC, "{tier:ultra}"))) == VPCItemPlugin.Tier.COMMON, "V1 mismatched tier falls back to the lowest tier");

            SpecialItemData ultra = VPCItemPlugin.roll(VPCItemPlugin.Tier.ULTRA, random);
            c.expect(!VPCItemPlugin.isValid(new SpecialItemData(ItemIds.VPC, ultra.getData())), "V1 common id with ultra data is not valid");

            Colony.pair(c, 6);
            MarketAPI a = c.market("a");
            a.addIndustry(ASSEMBLER);
            Industry assembler = a.getIndustry(ASSEMBLER);
            assembler.setSpecialItem(ultra);
            a.reapplyIndustries();
            boolean supplied = true;
            for (Map.Entry<String, Integer> e : VPCItemPlugin.getSupply(ultra).entrySet()) {
                supplied &= assembler.getSupply(e.getKey()).getQuantity().getModifiedValue() > 0;
            }
            c.expect(supplied, "V2 assembler supplies all three ultra outputs");
            boolean demanded = true;
            for (String id : VPCItemPlugin.getDemand(ultra).keySet()) {
                demanded &= assembler.getDemand(id).getQuantity().getModifiedValue() > 0;
            }
            c.expect(demanded, "V2 assembler demands every ultra input");

            a.removeIndustry(ASSEMBLER, null, false);
            a.addIndustry(MANUFACTORY);
            Industry manufactory = a.getIndustry(MANUFACTORY);
            manufactory.setSpecialItem(ultra);
            a.reapplyIndustries();
            boolean manuSupplied = true;
            for (String id : VPCItemPlugin.getSupply(ultra).keySet()) {
                manuSupplied &= manufactory.getSupply(id).getQuantity().getModifiedValue() > 0;
            }
            c.expect(manuSupplied, "V2 manufactory supplies all three ultra outputs");
            c.reload();
        });
        steps.add(c -> {
            Industry manufactory = c.market("a").getIndustry(MANUFACTORY);
            c.expect(manufactory != null && VPCItemPlugin.getTier(manufactory.getSpecialItem()) == VPCItemPlugin.Tier.ULTRA, "N2 ultra vpc survives reload");
        });
        return steps;
    }
}
