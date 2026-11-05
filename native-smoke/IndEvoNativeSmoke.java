import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.econ.impl.ItemEffectsRepo;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import indevo.ids.ItemIds;
import indevo.items.installable.SpecialItemEffectsRepo;

public final class IndEvoNativeSmoke extends BaseModPlugin {
    @Override
    public void onApplicationLoad() {
        NativeSmokeSupport.run(IndEvoNativeSmoke::probe);
    }

    private static void probe() throws Exception {
        NativeSmokeSupport.check(Global.getSettings().getModManager().isModEnabled("IndEvo"), "IndEvo is enabled");
        Object effect = ItemEffectsRepo.ITEM_EFFECTS.get(ItemIds.SIMULATOR);
        NativeSmokeSupport.expect(effect != null && effect == SpecialItemEffectsRepo.ITEM_EFFECTS.get(ItemIds.SIMULATOR),
                "IndEvo ModPlugin.onApplicationLoad ran before the probe");

        String icon = Global.getSettings().getSpecialItemSpec(ItemIds.SIMULATOR).getIconName();
        Global.getSettings().loadTexture(icon);
        CustomPanelAPI root = Global.getSettings().createCustom(NativeSmokeSupport.width(), NativeSmokeSupport.height(), null);
        TooltipMakerAPI panel = root.createUIElement(400f, 200f, false);
        panel.addTitle("Industrial.Evolution native smoke");
        panel.addPara("Simulation Engine icon: " + icon, 10f);
        panel.addImage(icon, 64f, 10f);
        root.addUIElement(panel).inTL(40f, 40f);
        NativeSmokeSupport.advance(root, 30);
        NativeSmokeSupport.render(root);
        NativeSmokeSupport.expect(NativeSmokeSupport.litPixels(NativeSmokeSupport.region(panel)) > 200,
                "IndEvo tooltip text and icon render");
        NativeSmokeSupport.screenshot("boot.png");
    }
}
